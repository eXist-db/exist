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
	'#export-resource' : function (element) {
			element.onclick = function() {
				new XOpenDialog('export-resource', X_SELECT_RESOURCE, '/db');
				return false;
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
			Event.observe(element, 'change', checkQuery, false);
			Event.observe(element, 'keyup', checkQuery, false);
	},
	'#show-options' : function (element) {
			element.onclick = function() {
				var panel = $('save-panel');
				if (!document.savePanelShow) {
					this.innerHTML = "Hide Options";
					//Effect.BlindDown('save-panel');
					Effect.toggle('save-panel', 'blind');
					document.savePanelShow = true;
				} else {
					this.innerHTML = "Show Options";
					//Effect.BlindUp('save-panel');
					Effect.toggle('save-panel', 'blind');
					document.savePanelShow = false;
				}
				return false;
			}
	},
	'#maximize' : function (element) {
			element.onclick = function() {
				resizeQueryBox(false);
				return false;
			}
	}
};
Behaviour.register(behaviourRules);

var EXPIRY_OFFSET = 30 * 24 * 60 * 60 * 1000;

var hitCount = 0;
var startOffset = 0;
var currentOffset = 0;
var endOffset = 0;

var timer = null;

/** onLoad handler to initialize display */
function init() {
	var savePanel = $('save-panel');
	document.savePanelShow = false;
	savePanel.style.display = 'none';
    resize();
    retrieveStored();
    Behaviour.apply();	// we need to call behaviour again after this handler
    initSlots();
}
		
/** Resize the query result output div. We want this to have a fixed height,
 *  so it neatly fits into the browser window.
 */
function resize() {
	var output = $('output');
    output.style.height = (document.body.clientHeight - output.offsetTop - 15) + "px";
    $('query').style.width = ($('query-panel').offsetWidth - 15) + 'px';
}

function resizeQueryBox(minimizeOnly) {
	var element = $('maximize');
	var panel = $('query');
	if (document.quMaximized) {
		panel.style.height = 170;
		document.quMaximized = false;
		element.innerHTML = "Maximize";
	} else if (!minimizeOnly) {
		panel.style.height = (document.body.clientHeight - panel.offsetTop - 50);
		document.quMaximized = true;
		element.innerHTML = "Minimize";
	}
	return false;
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
	if (!hitCount || hitCount < 1) {
		alert("There are no records to export!");
		return;
	}
	var docName = $('export-resource').innerHTML;
	if (docName == '') {
		alert("Please enter a name for the exported document.");
		return;
	}
	var p = docName.lastIndexOf('/');
	var collection = docName.substring(0, p);
	docName = docName.substring(p + 1);
	alert("doc: " + docName + "; " + collection);
	if (confirm('Export ' + hitCount + ' records to document ' +
		docName + '?')) {
		var params = 'export=' + escape(docName) + '&collection=' + escape(collection) +
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
function checkQuery(event) {
	if (timer) clearTimeout(timer);
	switch (event.keyCode) {
		case Event.KEY_RIGHT :
		case Event.KEY_LEFT :
		case Event.KEY_UP :
		case Event.KEY_DOWN :
		case Event.KEY_ESC :
			return;
	}
	timer = setTimeout('compileAndCheck()', 1000);
}

function compileAndCheck() {
	var query = $F('query');
	if (query) {
		$('errors').innerHTML = 'Checking syntax ...';
		var params = 'check=' + escapeQuery(query);
		var ajax = new Ajax.Updater('errors', "sandbox.xql", {
				method: 'post', parameters: params, 
				onFailure: requestFailed
			});
	}
	timer = null;
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
	resizeQueryBox(true);
	var params = 'qu=' + escapeQuery(query);
	var ajax = new Ajax.Request("sandbox.xql", {
			method: 'post', parameters: params, 
			onComplete: showQueryResponse,
			onFailure: requestFailed
		});
	$('errors').innerHTML = 'Query sent ...';
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
		startOffset = 1;
		currentOffset = 1;
		hitCount = hits;
		var howmany = $('howmany');
		endOffset = startOffset + (howmany.options[howmany.selectedIndex].text - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
			
		$('current').innerHTML = "Showing items " + startOffset + 
			" to " + endOffset;
		$('errors').innerHTML = 'Retrieving results ...';
		retrieveNext();
	}
}

/** Called if user clicks on "forward" link in query results. */
function browseNext() {
	if (currentOffset > 0 && endOffset < hitCount) {
		startOffset = currentOffset;
		endOffset = currentOffset + ($F('howmany') - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
		$('output').innerHTML = "";
		$('current').innerHTML = "Showing items " + startOffset + 
			" to " + endOffset;
		retrieveNext();
	}
	return false;
}

/** Called if user clicks on "previous" link in query results. */
function browsePrevious() {
	if (currentOffset > 0 && startOffset > 1) {
		startOffset = startOffset - $F('howmany');
		if (startOffset < 1)
			startOffset = 1;
		currentOffset = startOffset;
		endOffset = currentOffset + ($F('howmany') - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
		$('output').innerHTML = "";
		$('current').innerHTML = "Showing items " + startOffset + 
			" to " + endOffset;
		retrieveNext();
	}
	return false;
}

function requestFailed(request) {
	$('query-result').innertHTML =
		"The request to the server failed.";
}

/** If there are more query results to load, retrieve
 *  the next result.
 */
function retrieveNext() {
	if (currentOffset > 0 && currentOffset <= endOffset) {
		var params = 'num=' + currentOffset;
		currentOffset++;
		var ajax = new Ajax.Request("sandbox.xql", {
				method: 'get', parameters: params, 
				onComplete: itemRetrieved,
				onFailure: requestFailed
			});
	} else {
		$('errors').innerHTML = '';
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
	var params = 'qu=' + escapeQuery(query) + '&save=' + description;
	var ajax = new Ajax.Request("sandbox.xql", {
				method: 'post', parameters: params, 
				onComplete: queryStored,
				onFailure: requestFailed
			});
}

function queryStored(request) {
	retrieveStored();
}

function initSlots() {
	if (document.cookie.length > 0) {
		var clen = document.cookie.length;
		for (i = 0; i < clen; ) {
			var j = document.cookie.indexOf('=', i);
			var name = document.cookie.substring(i, j);
			if (name.substring(0, 4) == 'slot') {
				var slot = $(name);
				slot.parentNode.className = 'used';
				slot.innerHTML = getCookieVal(j + 1).substring(0, 12).escapeHTML();
			}
			i = document.cookie.indexOf(' ', i) + 1;
			if (i == 0) break;
		}
	}
}

function switchSlot(node) {
	var id = node.id;
	var currentQuery = $F('query');
	var cookie = getCookie(id);
	if (cookie != null)
		$('query').value = cookie;
	if (currentQuery.length > 0) {
		if (cookie != null)
			deleteCookie(id, cookie);
		var expiryDate = new Date();
		expiryDate.setTime(expiryDate.getTime() + EXPIRY_OFFSET);
		document.cookie = id + '=' + escape(currentQuery) +
			';expires=' + expiryDate.toGMTString();
		node.parentNode.className = 'used';
		node.innerHTML = currentQuery.substring(0, 12);
	}
}

function getCookie(name) {
	var arg = name + '=';
	var alen = arg.length;
	var clen = document.cookie.length;
	for (i = 0; i < clen;) {
		var j = i + alen;
		if (document.cookie.substring(i, j) == arg) {
			return getCookieVal(j);
		}			
		i = document.cookie.indexOf(" ", i) + 1;
		if (i == 0) break;
	}
	return null;
}

function getCookieVal(offset) {
	var endstr = document.cookie.indexOf(';', offset);
	if (endstr == -1)
		endstr = document.cookie.length;
	return unescape(document.cookie.substring(offset, endstr));
}

function deleteCookie(name, value) {
	var exp = new Date();
	exp.setTime(exp.getTime() - 1);
	document.cookie = name + '=' + escape(value) +
		'; expires=' + exp.toGMTString();
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

function escapeQuery(query) {
	return encodeURIComponent(query);
}