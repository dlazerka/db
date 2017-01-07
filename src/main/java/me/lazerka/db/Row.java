package me.lazerka.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.users.User;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import me.lazerka.db.Row.Value;
import org.joda.time.DateTime;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author Dzmitry Lazerka
 */
public class Row extends LinkedHashMap<String, Value> {
    public static class Value {
        @JsonProperty
        String value;

        @JsonProperty
        Type type;

        public Value(String value, Type type) {
            this.value = value;
            this.type = type;
        }

	    /**
	     * As of 1.9.7.
	     */
	    public static enum Type {
		    INTEGER(Short.class, Integer.class, Long.class) {
			    @Override
			    Object fromString(String str) {
				    return Long.parseLong(str);
			    }
		    },
		    FLOATING(Float.class, Double.class) {
			    @Override
			    Object fromString(String str) {
				    return Double.parseDouble(str);
			    }
		    },
		    BOOLEAN(Boolean.class) {
			    @Override
			    Object fromString(String str) {
				    return Boolean.parseBoolean(str);
			    }
		    },
		    STRING(String.class) {
			    @Override
			    Object fromString(String str) {
				    return str;
			    }
		    },
		    TEXT(Text.class) {
			    @Override
			    Object fromString(String str) {
				    return new Text(str);
			    }
		    },

		    SHORT_BLOB(ShortBlob.class) {
			    @Override
			    Object fromString(String str) {
				    throw new UnsupportedOperationException(str);
			    }
		    },
		    BLOB(Blob.class) {
			    @Override
			    Object fromString(String str) {
				    throw new UnsupportedOperationException(str);
			    }
		    },
		    DATETIME(Date.class) {
			    @Override
			    Date fromString(String str) {
				    return DateTime.parse(str).toDate();
			    }
		    },

		    GEO(GeoPt.class) {
			    @Override
			    Object fromString(String str) {
				    int i = str.indexOf(',');
				    if (i == -1 || i == 0 || i == str.length() - 1) {
					    throw new IllegalArgumentException(str);
				    }
				    float lat = Float.parseFloat(str.substring(0, i));
				    float lon = Float.parseFloat(str.substring(i + 1, str.length()));
				    return new GeoPt(lat, lon);
			    }
		    },
		    POSTAL_ADDRESS(PostalAddress.class) {
			    @Override
			    Object fromString(String str) {
				    return new PostalAddress(str);
			    }
		    },
		    PHONE_NUMBER(PhoneNumber.class) {
			    @Override
			    Object fromString(String str) {
				    return new PhoneNumber(str);
			    }
		    },
		    EMAIL(Email.class) {
			    @Override
			    Object fromString(String str) {
				    return new Email(str);
			    }
		    },
		    USER(User.class) {
			    @Override
			    Object fromString(String str) {
				    List<String> split = Splitter.on(':').splitToList(str);
				    switch (split.size()) {
					    case 2:
						    return new User(split.get(0), split.get(1));
					    case 3:
						    return new User(split.get(0), split.get(1), split.get(2));
					    case 4:
						    return new User(split.get(0), split.get(1), split.get(2), split.get(3));
					    default:
						    throw new IllegalArgumentException(str);
				    }
			    }
		    },
		    IM_HANDLE(IMHandle.class) {
			    @Override
			    Object fromString(String str) {
				    List<String> split = Splitter.on(' ').splitToList(str);
				    checkArgument(split.size() == 2, str);

				    try {
					    return new IMHandle(
							    IMHandle.Scheme.valueOf(split.get(0)), split.get(1));
				    } catch (IllegalArgumentException iae) {
					    try {
						    return new IMHandle(new URL(split.get(0)), split.get(1));
					    } catch (MalformedURLException e) {
						    throw new IllegalArgumentException(
								    "Can not be parsed into a valid IMHandle: " + str
								    + " Protocol must either be a valid scheme or url.");
					    }
				    }
			    }
		    },
		    LINK(Link.class) {
			    @Override
			    Object fromString(String str) {
				    return new Link(str);
			    }
		    },
		    CATEGORY(Category.class) {
			    @Override
			    Object fromString(String str) {
				    return new Category(str);
			    }
		    },
		    RATING(Rating.class) {
			    @Override
			    Object fromString(String str) {
				    return new Rating(Integer.parseInt(str));
			    }
		    },

		    KEY(Key.class) {
			    Pattern idPattern = Pattern.compile("\\(([0-9]+)\\)$");

			    @Override
			    Key fromString(String str) {
				    checkArgument(str.charAt(str.length() - 1) == ')', str);
				    Matcher matcher = idPattern.matcher(str);
				    if (matcher.find()) {
					    long id = Long.parseLong(matcher.group(1));
					    int slashIndex = str.lastIndexOf('/');
					    if (slashIndex == -1) {
						    String kind = str.substring(0, matcher.start());
						    return KeyFactory.createKey(kind, id);
					    } else {
						    String kind = str.substring(slashIndex + 1, matcher.start());
						    Key parent = fromString(str.substring(0, slashIndex));
							return KeyFactory.createKey(parent, kind, id);
					    }
				    } else {
					    throw new UnsupportedOperationException("String key names not supported yet: " + str);
				    }
			    }
		    },
		    BLOB_KEY(BlobKey.class) {
			    @Override
			    Object fromString(String str) {
				    return new BlobKey(str);
			    }
		    },
		    EMBEDDED_ENTITY(EmbeddedEntity.class) {
			    @Override
			    Object fromString(String str) {
				    throw new UnsupportedOperationException(str);
			    }
		    },
		    NULL() {
			    @Override
			    Object fromString(String str) {
				    checkArgument(str.equals(""), str);
				    return null;
			    }
		    },
		    RAW_VALUE(RawValue.class) {
			    @Override
			    Object fromString(String str) {
				    throw new UnsupportedOperationException(str);
			    }
		    }
		    ;

		    private static final Map<Class<?>, Type> fromClass = new HashMap<>();
		    static {
			    for(Type type : Type.values()) {
				    for(Class<?> aClass : type.classes) {
					    fromClass.put(aClass, type);
				    }
			    }
		    }

		    abstract Object fromString(String str);

		    private final ImmutableSet<Class<?>> classes;

		    Type(Class<?>... classes) {
		        this.classes = ImmutableSet.copyOf(classes);
		    }

		    public static Type fromClass(Class<?> aClass) {
			    return fromClass.get(aClass);
		    }
	    }
    }
}
