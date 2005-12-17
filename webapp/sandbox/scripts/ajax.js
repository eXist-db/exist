var openDialog;

Ajax.SelectDialog = function(target, root) {
	this.target = target;
	
	var html = 
		'<div id="ajax-open">' +
		'	<a href="#" id="ajax-close">Close</a>' +
		'	<h1>Open Resource</h1>' +
		'	<input type="text" name="path" id="ajax-path" />' +
		'	<div id="ajax-open-content"></div>' +
		'	<button type="button" id="ajax-submit">Select</button>' +
		'</div>';
	new Insertion.Bottom(document.body, html);
	Event.observe('ajax-close', 'click', this.close, false);
	Event.observe('ajax-submit', 'click', submit, false);
	
	var div = $('ajax-open');
	div.style.display = 'block';
	div.style.position = 'absolute';
	div.style.left = '25%';
	div.style.top = '25%';
	div.style.border = '1px solid black';
	openDialog = this;
	root = root || '/db';
	this.open(root);
}

Ajax.SelectDialog.prototype = {
	
	open: function (path) {
		var params = 'collection=' + escape(path);
		var ajax = new Ajax.Request("browse.xql", {
			method: 'post', parameters: params,
			onComplete: this.handleResponse,
			onFailure: this.requestFailed
		});
	},
	
	close: function () {
		var div = $('ajax-open');
		div.parentNode.removeChild(div);
	},
	
	submit: function () {
		var control = $(this.target);
		control.value = $F('ajax-path');
		this.close();
	},
	
	handleResponse: function (request) {
		// add collections
		var html = '<ul class="ajax-collections">';
		var root = request.responseXML.documentElement;
		var collections = root.getElementsByTagName('collection');
		for (var i = 0; i < collections.length; i++) {
			var node = collections[i];
			html += '<li><a href="#" onclick="selectCol(&quot;' +
				node.getAttribute('path') + '&quot;)">' + 
				node.getAttribute('name') + '</a></li>';
		}
		html += '</ul>';
		// add resources
		html += '<ul class="ajax-resources">';
		var resources = root.getElementsByTagName('resource');
		for (var i = 0; i < resources.length; i++) {
			var node = resources[i];
			html += '<li><a href="#" onclick="selectResource(&quot;' +
				node.getAttribute('path') + '&quot;)">' + 
				node.getAttribute('name') + '</a></li>';
		}
		html += '</ul>';
		var div = $('ajax-open-content');
		div.innerHTML = html;
	}
}

function selectCol(path) {
	$('ajax-path').value = path;
	openDialog.open(path);
	return false;
}

function selectResource(path) {
	$('ajax-path').value = path;
	return false;
}

function submit() {
	openDialog.submit();
}