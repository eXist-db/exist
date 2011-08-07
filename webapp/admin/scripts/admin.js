$(document).ready(function(){
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
	$.get('proc.xql', { "mode": "p" }, function (data) {
        $('#scheduled-jobs-container').html(data);
        setTimeout('reloadScheduledJobs()', 3000);
	});
}

function reloadJobs() {
	$.get('proc.xql', { "mode": "p" }, function (data) {
    	$('#processes-container').innerHTML = data;
		setTimeout('reloadJobs()', 3000);
	});
}

function reloadQueries() {
	$.get('proc.xql', { mode: "q" }, function (response) {
    	$('#xqueries-container').innerHTML = response.responseText;
		setTimeout('reloadQueries()', 5000);
	});
}

function displayDiff(id, resource, revision) {
	var div = document.getElementById(id);
	if (yDom.getStyle(div, 'display') == 'none')
		yDom.setStyle(div, 'display', '');
	else
		yDom.setStyle(div, 'display', 'none');
	
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
