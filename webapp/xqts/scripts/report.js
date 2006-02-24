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
}

function treeLoaded(request) {
	var xml = request.responseXML;
	var responseRoot = xml.documentElement;
	var tree = new YAHOO.widget.TreeView('navtree');
	var treeRoot = tree.getRoot();
	var rootNode = new YAHOO.widget.TextNode('Suite', treeRoot, true);
	displayGroup(responseRoot, rootNode);
	tree.draw();
}

function displayGroup(node, treeNode) {
	for (var i = 0; i < node.childNodes.length; i++) {
		var child = node.childNodes[i];
		if (child.nodeName == 'group') {
			var passed = child.getAttribute('passed');
			var failed = child.getAttribute('failed');
			var path = child.getAttribute('collection');
			var display = child.getAttribute('title') + ' [' + passed +
					'/' + failed + ']';
			var obj = { label: display, href: "javascript:loadTests('" + path + "')" };
			var childTree = new YAHOO.widget.TextNode(obj, treeNode, false);
			if (child.hasChildNodes())
				displayGroup(child, childTree);
		}
	}
}

function loadTests(collection) {
	var params = 'group=' + collection;
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
}

function detailsLoaded(request) {
	$('details-content').innerHTML = request.responseText;
	activeTab = $('summary');
}

function requestFailed(request) {
	alert("Request to the server failed!");
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