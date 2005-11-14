// Call init() after the document was loaded
window.onload = init;
window.onresize = resize;

// Register event handlers for various elements on the page.
// The behaviour library allows to register handlers via CSS
// selectors. This way, we can keep the HTML Javascript-clean.
var behaviourRules = {
	'#submit' : function (element) {
			element.onclick = execQuery;
	},
	'#clear' : function (element) {
			element.onclick = function() {
				$('query').value = '';
			}
	},
	'#saved' : function (element) {
			element.onchange = function() {
				var saved = $('saved');
				$('query').value = $F('saved');
				$('description').value = saved.options[saved.selectedIndex].text;
			}
	},
	'#next-link' : function (element) {
			element.onclick = browseNext;
	},
	'#prev-link' : function (element) {
			element.onclick = browsePrevious;
	},
	'#save' : function (element) {
			element.onclick = saveQuery;
	},
	'#check' : function (element) {
			element.onclick = checkQuery;
	},
	'#export' : function (element) {
			element.onclick = exportData;
	},
	'#query' : function (element) {
			element.onchange = checkQuery;
			element.onkeyup = checkQuery;
	},
	'#show-options' : function (element) {
			element.onclick = function() {
				if ($('save-panel').style.display == 'none') {
					this.innerHTML = "Hide Options";
					new Effect.BlindDown('save-panel');
					updateCollections();
				} else {
					this.innerHTML = "Show Options";
					new Effect.BlindUp('save-panel');
				}
				return false;
			}
	}
};
Behaviour.register(behaviourRules);

function init() {
    Element.hide('save-panel');
    resize();
    Behaviour.apply();	// we need to call behaviour again after this handler
    retrieveStored();
}

function resize() {
	var output = $('output');
    output.style.height = (document.body.clientHeight - output.offsetTop - 10) + "px";
}

function retrieveStored() {
	var ajax = new Ajax.Updater('saved', "get-examples.xql", {
			method: 'post',
			onFailure: requestFailed
		});
}

function updateCollections() {
	var ajax = new Ajax.Updater('collection', "list-collections.xql", {
			method: 'post',
			onFailure: requestFailed
		});
}

function exportData() {
	if (!document.hitCount || document.hitCount < 1) {
		alert("There are no records to export!");
		return;
	}
	var docName = $F('docname');
	if (docName == '') {
		alert("Please enter a name for the exported document.");
		return;
	}
	if (confirm('Export ' + document.hitCount + ' records to document ' +
		docName + '?')) {
		var params = 'export=' + escape(docName) + '&collection=' + escape($F('collection')) +
			'&wrapper=' + escape($F('wrapper'));
		var ajax = new Ajax.Request("sandbox.xql", {
				method: 'post', parameters: params,
				onComplete: exportResponse,
				onFailure: requestFailed
			});
	}
}

function exportResponse(request) {
	var root = request.responseXML.documentElement;
	if (root.nodeName == 'error') {
		$('errors').innerHTML = getElementValue(root);
	} else {
		$('errors').innerHTML = "Query results exported.";
	}
}

function checkQuery() {
	var query = $F('query');
	if (query) {
		var params = 'check=' + escape(query);
		var ajax = new Ajax.Updater('errors', "sandbox.xql", {
				method: 'post', parameters: params, 
				onFailure: requestFailed
			});
	}
}
		
function execQuery() {
	$('output').innerHTML = '';
	$('errors').innerHTML = '';
	var query = $F('query');
	if (query.length == 0)
		return;
	var params = 'qu=' + escape(query);
	var ajax = new Ajax.Request("sandbox.xql", {
			method: 'post', parameters: params, 
			onComplete: showQueryResponse,
			onFailure: requestFailed
		});
}

function showQueryResponse(request) {
	var root = request.responseXML.documentElement;
	if (root.nodeName == 'error') {
		$('errors').innerHTML = getElementValue(root);
	} else {
		var hits = root.getAttribute('hits');
		var elapsed = root.getAttribute('elapsed');
		$('query-result').innerHTML = 
			"Found " + hits + " in " + elapsed + " seconds.";
	
		document.startOffset = 1;
		document.currentOffset = 1;
		document.hitCount = hits;
		document.endOffset = document.startOffset + ($F('howmany') - 1);
		if (document.hitCount < document.endOffset)
			document.endOffset = document.hitCount;
			
		$('current').innerHTML = "Showing items " + document.startOffset + 
			" to " + document.endOffset;
		retrieveNext();
	}
}

function browseNext() {
	if (document.currentOffset > 0 && document.endOffset < document.hitCount) {
		document.startOffset = document.currentOffset;
		document.endOffset = document.currentOffset + ($F('howmany') - 1);
		if (document.hitCount < document.endOffset)
			document.endOffset = document.hitCount;
		$('output').innerHTML = "";
		$('current').innerHTML = "Showing items " + document.startOffset + 
			" to " + document.endOffset;
		retrieveNext();
	}
}

function browsePrevious() {
	if (document.currentOffset > 0 && document.startOffset > 1) {
		document.startOffset = document.startOffset - $F('howmany');
		if (document.startOffset < 1)
			document.startOffset = 1;
		document.currentOffset = document.startOffset;
		document.endOffset = document.currentOffset + ($F('howmany') - 1);
		if (document.hitCount < document.endOffset)
			document.endOffset = document.hitCount;
		$('output').innerHTML = "";
		$('current').innerHTML = "Showing items " + document.startOffset + 
			" to " + document.endOffset;
		retrieveNext();
	}
}

function requestFailed(request) {
	$('query-result').innertHTML =
		"The request to the server failed.";
}

function retrieveNext() {
	if (document.currentOffset > 0 && document.currentOffset <= document.endOffset) {
		var params = 'num=' + document.currentOffset;
		document.currentOffset++;
		var ajax = new Ajax.Request("sandbox.xql", {
				method: 'get', parameters: params, 
				onComplete: itemRetrieved,
				onFailure: requestFailed
			});
	}
}

function itemRetrieved(request) {
	new Insertion.Bottom('output', request.responseText);
	retrieveNext();
}

function saveQuery() {
	var description = $F('description');
	if (description == '') {
		alert("Please enter a description for the query!");
		return;
	}
	var query = $F('query');
	if (query == '') {
		alert("No query to save!");
		return;
	}
	var params = 'qu=' + query + '&save=' + description;
	var ajax = new Ajax.Request("sandbox.xql", {
				method: 'post', parameters: params, 
				onComplete: queryStored,
				onFailure: requestFailed
			});
}

function queryStored(request) {
	retrieveStored();
}

function getElementValue(node) {
	var val = '';
	var child = node.firstChild;
	while (child) {
		val += child.nodeValue;
		child = child.nextSibling;
	}
	return val;
}