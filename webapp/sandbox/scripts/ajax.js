var X_SELECT_RESOURCE = 'resource';
var X_SELECT_COLLECTION = 'collection';

var openDialog;

XOpenDialog = function(target, mode, root) {
	this.target = target;
	this.selectMode = mode;
	
	var html = 
		'<div id="xmldb-open">' +
		'	<a href="#" id="xmldb-close"><img src="window_close_grey.gif" border="0"/></a>' +
		'	<h1 id="xmldb-title">Open Resource</h1>' +
		'	<div id="xmldb-inner">' +
		'		<input type="text" name="path" id="xmldb-path" />' +
		'		<div id="xmldb-open-content"></div>' +
		'		<button type="button" id="xmldb-submit">Select</button>' +
		'	</div>' +
		'</div>';
	new Insertion.Bottom(document.body, html);
	Event.observe('xmldb-close', 'click', this.close, false);
	Event.observe('xmldb-submit', 'click', submit, false);
	
	var div = $('xmldb-open');
	div.style.display = 'block';
	div.style.position = 'absolute';
	div.style.left = '25%';
	div.style.top = '25%';
	new Draggable(div, { revert: false, handle: 'xmldb-title' });
	
	this.submitButton = $('xmldb-submit');
	
	root = root || '/db';
	this.open(root);
	$('xmldb-path').value = root;
	openDialog = this;
}

XOpenDialog.prototype = {	

	open: function (path) {
		if (this.selectMode == X_SELECT_COLLECTION)
			this.submitButton.disabled = false;
			
		var params = 'collection=' + escape(path);
		var ajax = new Ajax.Request("browse.xql", {
			method: 'post', parameters: params,
			onComplete: this.handleResponse,
			onFailure: this.requestFailed
		});
	},
	
	close: function () {
		var div = $('xmldb-open');
		div.parentNode.removeChild(div);
	},
	
	submit: function () {
		var control = $(this.target);
		if (control.value)
			control.value = $F('xmldb-path');
		else
			control.innerHTML = $F('xmldb-path');
		this.close();
	},
	
	handleResponse: function (request) {
		// add collections
		var root = request.responseXML.documentElement;
		var rootCol = root.getAttribute('root');
		if (rootCol == '')
			rootCol = '/db';
		var html = '<ul id="xmldb-collections">' +
				'<li><a href="#" onclick="return selectCol(&quot;' +
				rootCol + '&quot;)">..</a></li>';
		var collections = root.getElementsByTagName('collection');
		for (var i = 0; i < collections.length; i++) {
			var node = collections[i];
			html += '<li><a href="#" onclick="return selectCol(&quot;' +
				node.getAttribute('path') + '&quot;)">' + 
				node.getAttribute('name') + '</a></li>';
		}
		html += '</ul>';
		if (openDialog.selectMode == X_SELECT_RESOURCE) {
			// add resources
			html += '<ul id="xmldb-resources">';
			var resources = root.getElementsByTagName('resource');
			for (var i = 0; i < resources.length; i++) {
				var node = resources[i];
				html += '<li><a href="#" onclick="return selectResource(&quot;' +
					node.getAttribute('path') + '&quot;)">' + 
					node.getAttribute('name') + '</a></li>';
			}
			html += '</ul>';
		}
		var div = $('xmldb-open-content');
		div.innerHTML = html;
	}
}

function selectCol(path) {
	$('xmldb-path').value = path;
	openDialog.open(path);
	return false;
}

function selectResource(path) {
	$('xmldb-path').value = path;
	return false;
}

function submit() {
	openDialog.submit();
	return false;
}