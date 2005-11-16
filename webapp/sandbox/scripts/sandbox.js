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
	'#next' : function (element) {
			element.onclick = browseNext;
	},
	'#previous' : function (element) {
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
				var panel = $('save-panel');
				if (!document.savePanelShow) {
					this.innerHTML = "Hide Options";
					panel.style.display = '';
					new Effect.Size( panel, null, document.savePanelHeight, 200, 8, 
						{complete:function() { panel.style.overflow = "visible"; }});
					updateCollections();
					document.savePanelShow = true;
				} else {
					this.innerHTML = "Show Options";
					panel.style.overflow = "hidden";
					new Effect.Size( panel, null, 1, 200, 8 );
					document.savePanelShow = false;
				}
				return false;
			}
	}
};
Behaviour.register(behaviourRules);

/** onLoad handler to initialize display */
function init() {
	var savePanel = $('save-panel');
	document.savePanelHeight = savePanel.offsetHeight;
	document.savePanelShow = false;
	savePanel.style.display = 'none';
	savePanel.style.height = '1px';
	
    resize();
    retrieveStored();
    Behaviour.apply();	// we need to call behaviour again after this handler
}

/** Resize the query result output div. We want this to have a fixed height,
 *  so it neatly fits into the browser window.
 */
function resize() {
	var output = $('output');
    output.style.height = (document.body.clientHeight - output.offsetTop - 15) + "px";
}

/** Retrieve the list of stored queries and populate the select box. */
function retrieveStored() {
	// workaround: adding <option>'s to a select doesn't work in
	// IE, so we replace the whole select here.
	var ajax = new Ajax.Request("get-examples.xql", {
		method: 'post',
		onComplete: function(request) {
			Element.remove('saved');
			new Insertion.Bottom('queries', request.responseText);
			Behaviour.apply();
		},
		onFailure: requestFailed
	});
}

/** Retrieve a list of available collections and populate the collections
  * select box
  */
function updateCollections() {
	var ajax = new Ajax.Request("list-collections.xql", {
		method: 'post',
		onComplete: function(request) {
			Element.remove('collection');
			new Insertion.Bottom('export-options', request.responseText);
			Behaviour.apply();
		},
		onFailure: requestFailed
	});
}

/** Called if the user clicked 'export'. */
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

/** Response handler: check if an error occurred. */
function exportResponse(request) {
	var root = request.responseXML.documentElement;
	if (root.nodeName == 'error') {
		$('errors').innerHTML = getElementValue(root);
	} else {
		$('errors').innerHTML = "Query results exported.";
	}
}

/** Called if the user presses a key in the
 *  query box. Send an AJAX request to the server and
 *  check the query.
 */
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
	
/** Called if the user clicked on the "send" button.
 *  Execute the query.
 */	
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

/** Response handler: query executed, check for errors and
 *  initialize loading.
 */
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

/** Called if user clicks on "forward" link in query results. */
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

/** Called if user clicks on "previous" link in query results. */
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

/** If there are more query results to load, retrieve
 *  the next result.
 */
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

/** Response handler: insert the retrieved result. */
function itemRetrieved(request) {
	new Insertion.Bottom('output', request.responseText);
	retrieveNext();
}

/** Save the current query */
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