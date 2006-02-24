window.onload = init;
window.onresize = resize;

var behaviourRules = {
	'#summary-link' : function (element) {
		element.onclick = function () {
			if (activeTab)
				Element.hide(activeTab);
			activeTab = $('summary');
			Element.show(activeTab);
		}
	},
	'#query-link' : function (element) {
		element.onclick = function () {
			if (activeTab)
				Element.hide(activeTab);
			activeTab = $('query');
			Element.show(activeTab);
		}
	},
	'#testresult-link' : function (element) {
		element.onclick = function () {
			if (activeTab)
				Element.hide(activeTab);
			activeTab = $('testresult');
			Element.show(activeTab);
		}
	},
	'#testdef-link' : function (element) {
		element.onclick = function () {
			if (activeTab)
				Element.hide(activeTab);
			activeTab = $('testdef');
			Element.show(activeTab);
		}
	}
};

var activeTab = null;
var timer = null;
var progress = null;
var currentCollection = null;
var currentGroup = null;

function init() {
	resize();
	displayTree();
	Behaviour.register(behaviourRules);
	Behaviour.apply();
}

function displayTree() {
	var ajax = new Ajax.Request('report.xql', {
			parameters: 'tree=y',
			method: 'post', onComplete: treeLoaded,
			onFailure: requestFailed
		});
	displayMessage('Loading test groups ...');
}

function treeLoaded(request) {
	var xml = request.responseXML;
	var responseRoot = xml.documentElement;
	var tree = new YAHOO.widget.TreeView('navtree');
	var treeRoot = tree.getRoot();
	var rootNode = new YAHOO.widget.TextNode('Suite', treeRoot, true);
	displayGroup(responseRoot, rootNode);
	tree.draw();
	clearMessages();
}

function displayGroup(node, treeNode) {
	for (var i = 0; i < node.childNodes.length; i++) {
		var child = node.childNodes[i];
		if (child.nodeName == 'group') {
			var name = child.getAttribute('name');
			var passed = child.getAttribute('passed');
			var failed = child.getAttribute('failed');
			var path = child.getAttribute('collection');
			var display = child.getAttribute('title') + ' [' + passed +
					'/' + failed + ']';
			var obj = { label: display, href: "javascript:loadTests('" + path + "', '" + name + "')" };
			var childTree = new YAHOO.widget.TextNode(obj, treeNode, false);
			if (child.hasChildNodes())
				displayGroup(child, childTree);
		}
	}
}

function loadTests(collection, group) {
	var params = 'group=' + collection + '&name=' + group;
	var ajax = new Ajax.Updater('testcases', "report.xql", {
			method: 'post', parameters: params, 
			onFailure: requestFailed
		});
}

function details(testName) {
	var params = 'case=' + testName;
	var ajax = new Ajax.Request('report.xql', {
			method: 'post', parameters: params, 
			onFailure: requestFailed, onComplete: detailsLoaded
		});
	displayMessage('Loading test details ...');
}

function detailsLoaded(request) {
	$('details-content').innerHTML = request.responseText;
	activeTab = $('summary');
	clearMessages();
}

function runTest(collection, group) {
	if (confirm('Launch test group: ' + group + '?')) {
		currentCollection = collection;
		currentGroup = group;
		
		var params = 'group=' + group;
		var ajax = new Ajax.Request('xqts.xql', {
				method: 'post', parameters: params, 
				onFailure: requestFailed, 
				onComplete: testCompleted
			});
		progress = new ProgressDialog('Testing ...');
		progress.setMessage('Running ...');
		timer = setTimeout('reportProgress()', 1000);
	}
}

function testCompleted(request) {
	displayTree();
	loadTests(currentCollection, currentGroup);
	clearMessages();
	if (progress) {
		progress.finish();
	}
	if (timer) {
		clearTimeout(timer);
		timer = null;
	}
}

function reportProgress() {
	var ajax = new Ajax.Request('progress.xql', {
			method: 'post',
			onFailure: requestFailed, 
			onComplete: displayProgress
		});
}

function displayProgress(request) {
	if (timer) clearTimeout(timer);
	var xml = request.responseXML;
	var responseRoot = xml.documentElement;
	var done = responseRoot.getAttribute('done');
	var total = responseRoot.getAttribute('total');
	var passed = responseRoot.getAttribute('passed');
	var failed = responseRoot.getAttribute('failed');
	progress.setMessage(
		'<p>Processed ' + done + ' out of ' + total + ' tests...</p>' +
		'<p>Failed: ' + failed + '</p>' +
		'<p>Passed: ' + passed + '</p>'
	);
	timer = setTimeout('reportProgress()', 1000);
}

function requestFailed(request) {
	displayMessage("Request to the server failed!");
	if (timer) {
		clearTimeout(timer);
		timer = null;
	}
}

function resize() {
	var tree = $('navtree');
    tree.style.height = ((document.body.clientHeight - tree.offsetTop) - 15) + "px";
    
    var panel = $('panel-right');
    panel.style.width = (document.body.clientWidth - 350) + 'px';
    panel.style.height = tree.style.height;
    
    var details = $('details');
    details.style.height = (panel.offsetHeight - details.offsetTop) + 'px';
}

function displayMessage(message) {
	var messages = $('messages');
	messages.innerHTML = message;
}

function clearMessages() {
	$('messages').innerHTML = '';
}

ProgressDialog = function (title) {
	var html = 
		'<div id="progress-dialog">' +
		'	<h1 id="progress-title">' + title + '</h1>' +
		'	<div id="progress-inner">' +
		'		<div id="progress-message"></div>' +
		'	</div>' +
		'	<button type="button" id="progress-dismiss">Close</button>' +
		'</div>';
	new Insertion.Bottom(document.body, html);
	var div = $('progress-dialog');
	div.style.display = 'block';
	div.style.position = 'absolute';
	div.style.left = ((document.body.clientWidth - $('progress-dialog').offsetWidth) / 2) + 'px';
	div.style.top = '25%';
	
	$('progress-dismiss').style.visibility = 'hidden';
	Event.observe('progress-dismiss', 'click', this.close, false);
}

ProgressDialog.prototype = {

	close: function () {
		var div = $('progress-dialog');
		div.parentNode.removeChild(div);
	},
	
	setMessage: function (html) {
		$('progress-message').innerHTML = html;
	},
	
	finish: function (html) {
		$('progress-dismiss').style.visibility = 'visible';
	}
}