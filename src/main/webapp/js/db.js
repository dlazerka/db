String.prototype.escapeHTML = function () {
    return(
        this.replace(/&/g,'&amp;').
            replace(/>/g,'&gt;').
            replace(/</g,'&lt;').
            replace(/"/g,'&quot;')
    );
};
const ROOT = '/db';
function fillFields(query) {
	$('#kind').val(query.kind);
	if (query.ancestor) $('#ancestor').val(query.ancestor);
	$('#limit').val(query.limit);
	query.filters.forEach(function(filter){
		var div = addFilterInput();
		var regExp = new RegExp('^([^<>=!]+) ([<>=!]+) (String|Long|Key|Boolean|Email|Null)\\(([^\\)]*)\\)$', 'i');
		var match = regExp.exec(filter);
		if (!match || !match[0]) {
			throw new Error('Cannot parse filter: ' + filter);
		}
		$('.name', div).val(match[1]);
		$('.operator option[value="' + match[2] + '"]', div).attr('selected', true);
		$('.value', div).val(match[4]);
		$('.type option[value="' + match[3] +'"]', div).attr('selected', true);
	});
}
function getValidatedQuery() {
	var query = {};
	var kind = $('#kind').val();
	var ancestor = $('#ancestor').val();
	if (!kind && !ancestor) {  // parse location.hash
		if (!location.hash) {
			return null;
		}
		query = location.hash.substring(1);
		query = $.parseJSON(query);
		fillFields(query);
	} else {
		if (kind) query.kind = kind;
		if (query && ancestor) query.ancestor = ancestor;
		var filters = [];
		$('.filter').each(function(i, div) {
			var name = $('.name', div).val();
			var operator = $('.operator', div).val();
			var value = $('.value', div).val();
			var type = $('.type', div).val();
			var active = $('.active:checked', div).length > 0;
			if (!name || !active) return;
			filters.push(name + ' ' + operator + ' ' + type + '(' +value + ')');
		});
		if (filters) query.filters = filters;
		query.limit = $('#limit').val();
		document.location.hash = JSON.stringify(query);
	}
	return query;
}
function refreshKinds() {
	$.get(ROOT + '/kind', {}, 'json').done(function(kinds, textStatus, jqXHR) {
		localStorage.setItem("kinds", kinds);
		$.each(kinds, function(i, kind) {
			$('datalist#kinds').append('<option value="' + kind + '"/>')
		});

		if (!$('#kind').val() && kinds.length) {
			$('#kind').val(kinds[0]);
		}
	});
}
function refresh() {
	var query = getValidatedQuery();
	if (!query) return;
	document.title = 'Loading...';

	$.get(ROOT + '/entity', $.param(query, true)).done(function(data, textStatus, jqXHR) {
		document.title = (query.ancestor == undefined ? '' : query.ancestor) + ' ' + query.kind;
		if (data.error) {
			alert(data.error);
			return;
		}
		// name -> column index
		var columns = {};
		// array of arrays
		var matrix = [];
		data.forEach(function(entity) {
			var row = [];
			for (column in entity) {
				column = column.escapeHTML();
				if (columns[column] === undefined) {
					columns[column] = Object.keys(columns).length;
				}
				row[columns[column]] = entity[column];
			}
			matrix.push(row);
		});
		// string builder for innerHtml
		var sb = [];
		var columnsCount = Object.keys(columns).length;
		if (!columnsCount) {
			columns['no entities'] = 0;
		}
//		sb.push('<th><input type="checkbox" onchange="$(\'[name=row]\').attr(\'checked\', this.checked);"/></th>');
		Object.keys(columns).forEach(function(column) {
			sb.push('<th>' + column + '</th>');
		});
		var thead = $('#table > thead');
		thead.html(sb.join());
		sb = [];
		matrix.forEach(function(row) {
			sb.push('<tr>');
//			sb.push('<td><input type="checkbox" name="row" value="' + row[0].value.escapeHTML() + '"/></td>');
			for (var i = 0; i < columnsCount; i++) {
				if (row[i]) {
					sb.push('\t<td title="' + row[i].type + '">' + row[i].value.escapeHTML() + '</td>');
				} else {
					sb.push('\t<td></td>');
				}
			}
			sb.push('</tr>');
		});
		var tbody = $('#table > tbody');
		tbody.html(sb.join());

		$('#count').text(matrix.length);
		if (matrix.length == query.limit) {
			$('#count').text($('#count').text() + "+");
		}
	});
}
function delete_() {
	var query = getValidatedQuery();
	if (!query) return;
	// no, we cannot send data through options, because jquery bug http://bugs.jquery.com/ticket/11586
	var data = $.param(query, false);
	$.ajax(ROOT + '/entity?' + data, {
			method: 'DELETE'
	}).done(function(data, textStatus, jqXHR) {
		refresh();
		alert('Deleted ' + data + ' entities.');
	});
}
function count() {
	var query = getValidatedQuery();
	if (!query) return;
	query.count = true;
	$.get(ROOT + '/entity/count', $.param(query, false))
	.done(function(data, textStatus, jqXHR) {
		var plus = count == query.limit ? '+' : '';
		$('#count').text(data + plus);
	});
}
function addFilterInput() {
	var lastFilter = $('.filter:last');
	if (!$('.name', lastFilter).val()) return lastFilter;
	var clone = lastFilter.clone();
	lastFilter.after(clone);
	return clone;
}
$(function() {
	$.ajaxSetup({
		type: 'GET',
		timeout: 100000,
		headers: {
			'X-Login-Return-Url': location.href
		},
		error: function(jqXHR, textStatus, errorThrown) {
			if (jqXHR.status == 403) {
				var loginUrl = jqXHR.getResponseHeader('X-Login-URL');
				if (loginUrl != null) {
					location.href = loginUrl;
				}
			}
			document.title = textStatus;
			alert(errorThrown);
		}
	});
	refresh();
	refreshKinds();
});
