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
var treeWidget = null;

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
	var oldTree = null;
	if (treeWidget)
		oldTree = treeWidget;
	treeWidget = new YAHOO.widget.TreeView('navtree');
	var rootNode = new YAHOO.widget.TextNode('Suite', treeWidget.getRoot(), true);
	displayGroup(responseRoot, rootNode, oldTree);

	treeWidget.draw();
	clearMessages();
}

function displayGroup(node, treeNode, oldTree) {
	for (var i = 0; i < node.childNodes.length; i++) {
		var child = node.childNodes[i];
		if (child.nodeName == 'group') {
			var name = child.getAttribute('name');
			var passed = child.getAttribute('passed');
			var failed = child.getAttribute('failed');
			var path = child.getAttribute('collection');
			var display = child.getAttribute('title') + ' [' + passed +
					'/' + failed + ']';
			var obj = { 
				label: display, 
				href: "javascript:loadTests('" + path + "', '" + name + "')",
				group: name
			};
			var expanded = false;
			if (oldTree != null) {
				var oldNode = oldTree.getNodeByProperty('group', name);
				if (node)
					expanded = oldNode.expanded;
			}
					
			var childTree = new YAHOO.widget.TextNode(obj, treeNode, expanded);
			if (child.hasChildNodes())
				displayGroup(child, childTree, oldTree);
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
		timer = setTimeout('reportProgress()', 1000);
	}
}

function testCompleted(request) {
	if (timer) {
		clearTimeout(timer);
		timer = null;
	}
	reportProgress();
	displayTree();
	loadTests(currentCollection, currentGroup);
	clearMessages();
	if (progress) {
		progress.finish();
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
	progress.setMessage('Processed ' + done + ' out of ' + total + ' tests...');
	progress.setFailed(failed);
	progress.setPassed(passed);
	if (timer)
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
    tree.style.height = ((document.body.clientHeight - tree.offsetTop) - 50) + "px";
    
    var panel = $('panel-right');
    panel.style.width = (document.body.clientWidth - 350) + 'px';
    panel.style.height = tree.style.height;
    
    var details = $('details');
    details.style.height = ( panel.offsetHeight - 160 ) + 'px';
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
		'		<table cellspacing="20">' +
		'			<tr>' +
		'				<td colspan="2" id="progress-message">Starting ...</td>' +
		'			</tr>' +
		'				<td>Passed:</td><td id="progress-passed">0</td>' +
		'			</tr>' +
		'			<tr>' +
		'				<td>Failed:</td><td id="progress-failed">0</td>' +
		'			</tr>' +
		'		</table>' +
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
	
	setPassed: function (passed) {
		$('progress-passed').innerHTML = passed;
	},
	
	setFailed: function (failed) {
		$('progress-failed').innerHTML = failed;
	},
	
	finish: function (html) {
		$('progress-dismiss').style.visibility = 'visible';
	}
}