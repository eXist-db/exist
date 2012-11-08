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
	'		<div id="progressPane">' +
	'			<div id="progressBar_bg">' +
	'				<div id="progressBar_outer">' +
	'					<div id="progressBar"></div>' +
	'				</div>' +
	'				<div id="progressBar_txt">0 %</div>' +
	'			</div>' +
	'		</div>' +
	'		<button type="button" id="progress-dismiss">Close</button>' +
	'</div>';
			
var timer = null;
var progress = null;
var progressBar = null;
var currentCollection = null;
var currentGroup = null;
var treeWidget = null;
var installWarning;
var mode;

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

    installWarning = new YAHOO.widget.Panel('installation', {
        height: (YAHOO.util.Dom.getViewportHeight() - 200) + 'px',
        width: (YAHOO.util.Dom.getViewportWidth() - 200) + 'px',
        modal: true,
        underlay: 'shadow',
        fixedcenter: true,
        close: false,
        visible: false,
        draggable: false
    });
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
    if (!node.hasChildNodes()) {
        installWarning.render(document.body);
        installWarning.show();
        return;
    }
    for (var i = 0; i < node.childNodes.length; i++) {
		var child = node.childNodes[i];
		if (child.nodeName == 'group') {
			var name = child.getAttribute('name');
			var passed = child.getAttribute('passed');
			var failed = child.getAttribute('failed');
            var errors = child.getAttribute('errors');
			var total = child.getAttribute('total');
			var path = child.getAttribute('collection');
			var percentage = 0.0;
			if (total > 0.0)
				percentage = passed / (total / 100);
			
			var display = child.getAttribute('title') + ' [' + passed +
					'/' + failed + "/" + percentage.toFixed(1) + 
					'%]';
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
	YAHOO.util.Connect.asyncRequest('GET', 'report.xql?'+ params, callback);
}

function details(testName) {
	var params = 'case=' + testName;
	var callback = {
		success: detailsLoaded,
		failure: requestFailed
	}
	YAHOO.util.Connect.asyncRequest('GET', 'report.xql?' + params, callback);
	displayMessage('Loading test details ...');
}

function detailsLoaded(request) {
	document.getElementById('details-content').innerHTML = request.responseText;
	activeTab = document.getElementById('summary');
	clearMessages();
	dp.SyntaxHighlighter.HighlightAll('code');
}

function runTest(collection, group) {    
    setMode();
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
		progress.setHeader('Running tests on ' + (mode == 'true' ? 'in-memory nodes' : 'persistent nodes') + '...');
		progress.setBody(PROGRESS_DIALOG);
		progress.render(document.body);
		
		document.getElementById('progress-dismiss').disabled = true;
		YAHOO.util.Event.addListener('progress-dismiss', 'click', 
			function (ev, progress) {
				progressBar = null;
				progress.hide();
				progress.destroy();
			}, progress
		);
		progress.show();
		
		var params = 'group=' + group + '&mode=' + mode;
		var callback = {
			success: testCompleted,
			failure: requestFailed
		}
		YAHOO.util.Connect.asyncRequest('GET', 'xqts.xql?' + params, callback);
		timer = setTimeout('reportProgress()', 5000);
	}
}

function setMode() {
    var select = document.getElementsByTagName('*')['processing'];
    mode = select.options[select.selectedIndex].value;
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
		document.getElementById('progress-dismiss').disabled = false;
		progressBar.finish();
		progressBar = null;
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
	
	if (progressBar == null) {
		progressBar = new ProgressBar(total);
	}
	progressBar.move(done);
	
	if (timer)
		timer = setTimeout('reportProgress()', 5000);
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

ProgressBar = function (max) {
	this.progressbar_steps = max;
}

ProgressBar.prototype.move = function (steps) {
	var progressBar_bg = document.getElementById('progressBar_bg');
	var progressbarWidth = progressBar_bg.clientWidth;
	var width = Math.ceil(progressbarWidth * (steps / this.progressbar_steps));
	YAHOO.util.Dom.setStyle('progressBar_outer', 'width', width + 'px');
	var percent = Math.ceil((steps / this.progressbar_steps)*100);
	document.getElementById('progressBar_txt').innerHTML = percent + '%';
}

ProgressBar.prototype.finish = function () {
	var progressBar_bg = document.getElementById('progressBar_bg');
	var progressbarWidth = progressBar_bg.clientWidth;
	YAHOO.util.Dom.setStyle('progressBar_outer', 'width', progressbarWidth + 'px');
	document.getElementById('progressBar_txt').innerHTML = '100%';
}
