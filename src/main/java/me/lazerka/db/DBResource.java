package me.lazerka.db;

import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.*;
import com.google.common.collect.Lists;
import me.lazerka.db.Row.Value.Type;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;

@Singleton
@Path("/db")
public class DBResource extends HttpServlet {
	private static final Logger logger = LoggerFactory.getLogger(DBResource.class);

	private static final Pattern KEY_ID_PATTERN = Pattern.compile("^(\\w+)\\((\\d+)\\)$");
	private static final Pattern KEY_NAME_PATTERN = Pattern.compile("^(\\w+)\\(\"([^\"]+)\"\\)$");
	private static final int MAX_VALUE_LENGTH = 2048;

	private final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	@GET
	@Path("/entity")
	@Produces("application/json")
	public List<Row> list(
			@QueryParam("kind") @DefaultValue("") String kind,
			@QueryParam("ancestor") @DefaultValue("") String ancestor,
			@QueryParam("filters") List<String> filters,
			@QueryParam("limit") @DefaultValue("100") int limit
	) throws IOException {
		List<Row> result = new ArrayList<>(100);

		Query q = createQuery(kind, ancestor, filters);
		FetchOptions fetchOptions = createFetchOptions(limit);

		logger.info("{} {}", q, fetchOptions);
		PreparedQuery pq = datastore.prepare(q);

		QueryResultIterable<Entity> iterable = pq.asQueryResultIterable(fetchOptions);

		for(Entity entity : iterable) {
			Row row = new Row();
			row.put(Entity.KEY_RESERVED_PROPERTY, toRowValue(entity.getKey()));
			for(String key : entity.getProperties().keySet()) {
				Object value = entity.getProperty(key);
				row.put(key, toRowValue(value));
			}
			result.add(row);
		}

		return result;
	}

	@GET
	@Path("/kind")
	@Produces("application/json")
	public List<String> kind() {
		Query q = new Query(Entities.KIND_METADATA_KIND);
		PreparedQuery preparedQuery = datastore.prepare(q);
		List<String> results = Lists.newArrayList();
		for(Entity entity : preparedQuery.asIterable()) {
			results.add(entity.getKey().getName());
		}
		return results;
	}

	@GET
	@Path("/entity/count")
	@Produces("application/json")
	public int count(
			@QueryParam("kind") String kind,
			@QueryParam("ancestor") @DefaultValue("") String ancestor,
			@QueryParam("filter") List<String> filters,
			@QueryParam("limit") @DefaultValue("10000") int limit
	) throws IOException {
		Query q = createQuery(kind, ancestor, filters);

		FetchOptions fetchOptions = createFetchOptions(limit);

		q.setKeysOnly();
		logger.info("{} {}", q, fetchOptions);
		PreparedQuery pq = datastore.prepare(q);
		QueryResultIterator<Entity> iterator = pq.asQueryResultIterator(fetchOptions);
		int count;
		logger.debug("Starting counting...");
		for(count = 0; iterator.hasNext(); count++) {
			iterator.next();
		}

		logger.debug("Counted: " + count);
		return count;
	}

	@DELETE
	@Path("/entity")
	@Produces("application/json")
	public int delete(
			@QueryParam("kind") @DefaultValue("") String kind,
			@QueryParam("ancestor") @DefaultValue("") String ancestor,
			@QueryParam("filter") List<String> filters,
			@QueryParam("limit") @DefaultValue("10000") int limit
	) throws IOException {
		Query q = createQuery(kind, ancestor, filters);
		q.setKeysOnly();
		FetchOptions fetchOptions = createFetchOptions(limit);

		logger.info("{} {}", q, fetchOptions);
		PreparedQuery pq = datastore.prepare(q);
		QueryResultIterable<Entity> iterable = pq.asQueryResultIterable(fetchOptions);

		int deleted = 0;
		final int bulkSize = 1000;
		ArrayList<Key> keysBulk = new ArrayList<>(bulkSize);
		for(Entity entity : iterable) {
			keysBulk.add(entity.getKey());
			if (keysBulk.size() == bulkSize) {
				logger.info("Deleting bulk of " + keysBulk.size());
				datastore.delete(keysBulk);
				deleted += keysBulk.size();
				keysBulk.clear();
			}
		}
		logger.info("Deleting final bulk of " + keysBulk.size());
		datastore.delete(keysBulk);
		deleted += keysBulk.size();
		logger.info("All completed.");

		return deleted;
	}


	private Query createQuery(String kind, String ancestor, List<String> filters) {
		Query q;
		if (ancestor.isEmpty() && kind.isEmpty()) {
			throw new IllegalArgumentException("No 'kind' or 'ancestor' argument");
		} else if (kind.isEmpty()) {
			Key key = asKey(ancestor);
			q = new Query(key);
		} else if (ancestor.isEmpty()) {
			q = new Query(kind);
		} else {
			Key key = asKey(ancestor);
			q = new Query(kind, key);
		}
		addFilters(q, filters);
		return q;
	}

	private Key asKey(String valueStr) {
		valueStr = valueStr.trim();
		Matcher matcher = KEY_ID_PATTERN.matcher(valueStr);
		if (!matcher.matches()) {
			matcher = KEY_NAME_PATTERN.matcher(valueStr);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Unable to parse " + valueStr +
						" using patterns " + KEY_NAME_PATTERN + " or " + KEY_ID_PATTERN);
			}
			return KeyFactory.createKey(matcher.group(1), matcher.group(2));
		}
		return KeyFactory.createKey(matcher.group(1), Long.parseLong(matcher.group(2)));
	}

	private FetchOptions createFetchOptions(int limit) {
		FetchOptions fetchOptions = withDefaults();
		fetchOptions.limit(limit);
		fetchOptions.chunkSize(limit < 1000 ? limit : (limit / 10)); // don't really know what to put here
		return fetchOptions;
	}

	private Query addFilters(Query q, List<String> filters) {
		List<Filter> predicates = Lists.newArrayList();
		logger.warn("{}", filters);
		for(String filter : filters) {
			Pattern typedFieldValue = Pattern.compile(
					"^([^<>=!]+) ([<>=!]+) (String|Long|Key|Boolean|Email|Null)\\(([^\\)]*)\\)$",
					Pattern.CASE_INSENSITIVE);
			Matcher matcher = typedFieldValue.matcher(filter);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Unable to parse filter `" + filter + "`.");
			}
			String field = matcher.group(1);
			String operatorS = matcher.group(2);
			String typeS = matcher.group(3);
			String valueStr = matcher.group(4);

			Query.FilterOperator operator = toFilterOperator(operatorS);
			Row.Value.Type type = Type.valueOf(typeS.toUpperCase());

			if (field.equals(Entity.KEY_RESERVED_PROPERTY)) {
				type = Type.KEY;
			}

			Object value = type.fromString(valueStr);

			FilterPredicate predicate = new FilterPredicate(field, operator, value);
			predicates.add(predicate);
		}
		if (predicates.size() == 1) {
			q.setFilter(predicates.get(0));
		} else if (predicates.size() > 1) {
			q.setFilter(new CompositeFilter(CompositeFilterOperator.AND, predicates));
		}
		return q;
	}

	private FilterOperator toFilterOperator(String operatorS) {
		for(FilterOperator filterOperator : FilterOperator.values()) {
			if (filterOperator.toString().equals(operatorS)) {
				return filterOperator;
			}
		}
		throw new IllegalArgumentException("No FilterOperator for {}" + operatorS);
	}

	private Row.Value toRowValue(Object value) throws IOException {
		String valueStr;
		if (value == null) {
			valueStr = "null";
		} else if (value instanceof Date) {
			long ms = ((Date) value).getTime();
			DateTime dt = new DateTime(ms);
			valueStr = ISODateTimeFormat.dateTime().print(dt);
		} else if (value instanceof EmbeddedEntity) {
			EmbeddedEntity embeddedEntity = (EmbeddedEntity) value;
			StringBuilder sb = new StringBuilder();
			Map<String, Object> props = embeddedEntity.getProperties();
			for(String key : props.keySet()) {
				String val = String.valueOf(props.get(key));
				sb.append(key).append(": ").append(val).append('\n');
			}
			valueStr = "<EmbeddedEntity:\n" + sb + ">";
		} else {
			valueStr = String.valueOf(value);
		}

		Type type = value == null ? Type.NULL : Type.fromClass(value.getClass());

		if (valueStr.length() > MAX_VALUE_LENGTH) {
			valueStr = valueStr.substring(0, MAX_VALUE_LENGTH);
		}
		return new Row.Value(valueStr, type);
	}

}
