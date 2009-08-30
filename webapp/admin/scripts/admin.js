var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event;

Event.onDOMReady(function () {
    if (document.getElementById('xqueries-container')) {
    	setTimeout('reloadScheduledJobs()', 3000);
    	setTimeout('reloadJobs()', 3000);
    	setTimeout('reloadQueries()', 5000);
	}
	if (document.forms['f-trace']) {
	    profilingTabs.addListener('activeIndexChange', function (ev) {
	        document.forms['f-trace'].elements['tab'].value = ev.newValue;
	    }); 
	}
});

function reloadScheduledJobs() {
	var pdiv = document.getElementById('scheduled-jobs-container');
	var callback = {
		success: function (response) {
			pdiv.innerHTML = response.responseText;
			setTimeout('reloadScheduledJobs()', 3000);
		},
		failure: function (response) {
		}
	};
	YAHOO.util.Connect.asyncRequest('GET', 'proc.xql?mode=p', callback);
}

function reloadJobs() {
	var pdiv = document.getElementById('processes-container');
	var callback = {
		success: function (response) {
			pdiv.innerHTML = response.responseText;
			setTimeout('reloadJobs()', 3000);
		},
		failure: function (response) {
		}
	};
	YAHOO.util.Connect.asyncRequest('GET', 'proc.xql?mode=p', callback);
}

function reloadQueries() {
	var pdiv = document.getElementById('xqueries-container');
	var callback = {
		success: function (response) {
			pdiv.innerHTML = response.responseText;
			setTimeout('reloadQueries()', 5000);
		},
		failure: function (response) {
		}
	};
	YAHOO.util.Connect.asyncRequest('GET', 'proc.xql?mode=q', callback);
}

function displayDiff(id, resource, revision) {
	var div = document.getElementById(id);
	if (Dom.getStyle(div, 'display') == 'none')
		Dom.setStyle(div, 'display', '');
	else
		Dom.setStyle(div, 'display', 'none');
	
	if (div.innerHTML == '') {
		var callback = {
			success: function (response) {
				document.getElementById(id).innerHTML = 
					'<pre class="prettyprint lang-xml">' +
					escapeXML(response.responseText) +
					'</pre>';
				prettyPrint();
			},
			failure: function (response) {
				alert('Failed to retrieve diff: ' + response.responseText);
			}
		};
		var url = 'versions.xql?action=diff&resource=' + resource + '&rev=' + revision;
		YAHOO.util.Connect.asyncRequest('GET', url, callback);
	}
	return false;
}

function escapeXML(xml) {
	var out = '';
	for (var i = 0; i < xml.length; i++) {
		ch = xml.charAt(i);
		if (ch == '<') out += '&lt;'
		else if (ch == '>') out += '&gt;'
		else if (ch == '&') out += '&amp;'
		else out += ch;
	}
	return out;
}
