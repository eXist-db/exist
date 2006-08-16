window.onload = init;
window.onresize = resize;

window.activeTab = null;

var PROGRESS_DIALOG =
	'<div id="progress-inner">' +
	'	<h1 id="progress-message">Starting ...</h1>' +
	'		<table cellspacing="20">' +
	'			<tr>' +
	'				<td id="progress-passed">0</td>' +
	'				<td>/</td>' +
	'				<td id="progress-failed">0</td>' +
	'			</tr>' +
	'		</table>' +
	'		<div id="dhtmlgoodies_progressPane">' +
	'			<div id="dhtmlgoodies_progressBar_bg">' +
	'				<div id="dhtmlgoodies_progressBar_outer">' +
	'					<div id="dhtmlgoodies_progressBar"></div>' +
	'				</div>' +
	'				<div id="dhtmlgoodies_progressBar_txt">0 %</div>' +
	'			</div>' +
	'		</div>' +
	'		<button type="button" id="progress-dismiss">Close</button>' +
	'</div>';
			
var timer = null;
var progress = null;
var progressbar_steps = 0;	// Total number of progress bar steps.
var currentCollection = null;
var currentGroup = null;
var treeWidget = null;


function init() {
	resize();
	displayTree();
	
	var tabs = YAHOO.util.Dom.getElementsByClassName('tab');
	for (var i = 0; i < tabs.length; i++) {
		YAHOO.util.Event.addListener(tabs[i], 'click',
			function (ev, tab) {
				if (window.activeTab)
					YAHOO.util.Dom.setStyle(window.activeTab, 'display', 'none');
				var targetId = tab.id.substring(4);
				window.activeTab = document.getElementById(targetId);
				YAHOO.util.Dom.setStyle(window.activeTab, 'display', '');
			}, tabs[i]
		);
	}
}

function displayTree() {
	displayMessage('Loading test groups ...');
	var callback = {
		success: treeLoaded,
		failure: requestFailed
	}
	YAHOO.util.Connect.asyncRequest('GET', 'report.xql?tree=y', callback);
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
            var errors = child.getAttribute('errors');
			var total = child.getAttribute('total');
			var path = child.getAttribute('collection');
			var display = child.getAttribute('title') + ' [' + passed +
					'/' + failed + "/" + errors +']';
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
	displayMessage('Loading tests ...');
	var params = 'group=' + collection + '&name=' + group;
	var callback = {
		success: function (response) {
			var tests = document.getElementById('testcases');
			tests.innerHTML = response.responseText;
			resize();
			clearMessages();
		},
		failure: requestFailed
	}
	YAHOO.util.Connect.asyncRequest('POST', 'report.xql', callback, params);
}

function details(testName) {
	var params = 'case=' + testName;
	var callback = {
		success: detailsLoaded,
		failure: requestFailed
	}
	YAHOO.util.Connect.asyncRequest('POST', 'report.xql', callback, params);
	displayMessage('Loading test details ...');
}

function detailsLoaded(request) {
	document.getElementById('details-content').innerHTML = request.responseText;
	activeTab = document.getElementById('summary');
	clearMessages();
	dp.SyntaxHighlighter.HighlightAll('code');
}

function runTest(collection, group) {
	if (confirm('Launch test group: ' + group + '?')) {
		currentCollection = collection;
		currentGroup = group;
		
		progress = new YAHOO.widget.Panel('progress', {
			width: '400px',
			height: '250px',
			modal: true,
			underlay: 'shadow',
			fixedcenter: true,
			close: false,
			visible: false,
			draggable: false
		});
		progress.setHeader('Running tests ...');
		progress.setBody(PROGRESS_DIALOG);
		progress.render(document.body);
		
		document.getElementById('progress-dismiss').disabled = true;
//		YAHOO.util.Dom.setStyle('progress-dismiss', 'visibility', 'hidden');
		YAHOO.util.Event.addListener('progress-dismiss', 'click', 
			function (ev, progress) {
				progress.hide();
				progress.destroy();
			}, progress
		);
		progress.show();
		
		var params = 'group=' + group;
		var callback = {
			success: testCompleted,
			failure: requestFailed
		}
		YAHOO.util.Connect.asyncRequest('POST', 'xqts.xql', callback, params);
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
	moveProgressBar();
	progressbar_steps = false;
	if (progress) {
		document.getElementById('progress-dismiss').disabled = false;
//		YAHOO.util.Dom.setStyle('progress-dismiss', 'visibility', 'visible');
	}
}

function reportProgress() {
	var callback = {
		success: displayProgress,
		failure: requestFailed
	}
	YAHOO.util.Connect.asyncRequest('GET', 'progress.xql', callback);
}

function displayProgress(request) {
	if (timer) clearTimeout(timer);
	var xml = request.responseXML;
	var responseRoot = xml.documentElement;
	var done = responseRoot.getAttribute('done');
	var total = responseRoot.getAttribute('total');
	var passed = responseRoot.getAttribute('passed');
	var failed = responseRoot.getAttribute('failed');
	document.getElementById('progress-message').innerHTML = 
		'Processed ' + done + ' out of ' + total + ' tests...';
	document.getElementById('progress-passed').innerHTML = passed;
	document.getElementById('progress-failed').innerHTML = failed;
	
	if (progressbar_steps == 0) {
		progressbar_steps = total;
	}
	moveProgressBar(done);
	
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
	var $S = YAHOO.util.Dom.setStyle;
	var content = document.getElementById('content');
	$S(content, 'height', 
		(YAHOO.util.Dom.getViewportHeight() - content.offsetTop) + 'px');
	
	var left = document.getElementById('panel-left');
	var tree = document.getElementById('navtree');
	var h = (left.offsetHeight - tree.offsetTop - 15);
	$S(tree, 'height', h + 'px');

	var panel = document.getElementById('panel-right');
	var div = document.getElementById('tests');
	h = (left.offsetHeight - div.offsetTop);
	$S(div, 'height', h + 'px');
	
	panel = document.getElementById('details');
	div = document.getElementById('details-content');
	var tabs = document.getElementById('tabs');
	h = (panel.offsetHeight - tabs.offsetHeight - 8);
	$S(div, 'height', h + 'px');
}

function displayMessage(message) {
	var messages = document.getElementById('messages');
	messages.innerHTML = message;
}

function clearMessages() {
	document.getElementById('messages').innerHTML = '';
}


	
/* Don't change any of these variables */
var dhtmlgoodies_progressPane = false;
var dhtmlgoodies_progressBar_bg = false;
var dhtmlgoodies_progressBar_outer = false;
var dhtmlgoodies_progressBar_txt = false;
var progressbarWidth;

function moveProgressBar(steps){
	if(!dhtmlgoodies_progressBar_bg){
		dhtmlgoodies_progressPane = document.getElementById('dhtmlgoodies_progressPane');
		dhtmlgoodies_progressBar_bg = document.getElementById('dhtmlgoodies_progressBar_bg');
		dhtmlgoodies_progressBar_outer = document.getElementById('dhtmlgoodies_progressBar_outer');
		dhtmlgoodies_progressBar_txt = document.getElementById('dhtmlgoodies_progressBar_txt');
		progressbarWidth = dhtmlgoodies_progressBar_bg.clientWidth;
	}
	if(!steps){
		dhtmlgoodies_progressBar_outer.style.width = progressbarWidth + 'px';
		dhtmlgoodies_progressBar_txt.innerHTML = '100%';
		setTimeout('document.getElementById("dhtmlgoodies_progressPane").style.display="none"',50);
	} else {
		var width = Math.ceil(progressbarWidth * (steps / progressbar_steps));
		dhtmlgoodies_progressBar_outer.style.width = width + 'px';
		var percent = Math.ceil((steps / progressbar_steps)*100);
		dhtmlgoodies_progressBar_txt.innerHTML = percent + '%';
	}
}