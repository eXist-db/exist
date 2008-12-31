var Dom = YAHOO.util.Dom;

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
