var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event,
    log = YAHOO.log;

var EXPIRY_OFFSET = 30 * 24 * 60 * 60 * 1000;

var hitCount = 0;
var startOffset = 0;
var currentOffset = 0;
var endOffset = 0;

var timer = null;

// the currently edited resource
var currentResource = null;

// add resize listener
Event.addListener(window, 'resize', resize);

// Initialize after DOM was loaded
Event.onDOMReady(function () {
    currentResource = new Resource();
    var mainMenu = new YAHOO.widget.MenuBar('mainmenu', { autosubmenudisplay: true, hidedelay: 750, lazyload: true });
    mainMenu.render();

    // Register event handlers for various elements on the page.
    Event.addListener('file-open', 'click', loadQuery);
    Event.addListener('file-save', 'click', function () { doSave(false); });
    Event.addListener('file-save-as', 'click', function () { doSave(true); });
    Event.addListener('submit', 'click', execQuery);
    Event.addListener('clear', 'click', function () {
        newEditor();
    });
    Event.addListener('saved', 'change', selectStored);
    Event.addListener('export', 'click', exportData);
    Event.addListener('next', 'click', browseNext);
    Event.addListener('previous', 'click', browsePrevious);
    Event.addListener('file-save-example', 'click', saveQuery);
    Event.addListener('check', 'click', compileAndCheck);
    Event.addListener('query', 'change', checkQuery, false);
    Event.addListener('maximize', 'click', function () { resizeQueryBox(false); });
    Event.addListener('error-close', 'click', hideErrorPanel);

    YAHOO.widget.Logger.enableBrowserConsole();
    retrieveStored();
    resize();
    initSlots();
});

/**
 * Init the codepress editor with new content, using
 * the specified language for syntax highlighting.
 * 
 * @param content
 * @param language
 */
function initEditor(content, language) {
    var lang = language || 'xquery';
    document.getElementById('codeEditor').value = content;
}

/**
 * Clear the editor and create a new resource.
 */
function newEditor() {
    currentResource = new Resource();
    document.getElementById('codeEditor').value = '';
}

/**
 * Change the language currently used for syntax
 * highlighting. This will also change the mime-type
 * of the resource when stored.
 * 
 * @param language
 */
function editLanguage(language) {
//    var content = codeEditor.getCode();
//    codeEditor.edit(content, language);
    currentResource.setLang(language);
}

/**
 * Singleton object: maintains a mapping of mime-types
 * to languages for sytnax highlighting.
 * 
 * @param name
 * @param path
 * @param mimeType
 */
var MimeTypes = new function() {
    var TYPES = {
        'xml': ['text/xml', 'application/xml', 'application/xhtml+xml'],
        'xquery': ['application/xquery'],
        'css': ['text/css'],
        'html': ['text/html'],
        'javascript': ['application/x-javascript']
    };

    this.getLangFromMime = function(mimeType) {
        for (var lang in TYPES) {
            var syn = TYPES[lang];
            for (var i = 0; i < syn.length; i++) {
                if (mimeType == syn[i])
                    return lang;
            }
        }
        return 'xquery';
    };

    this.getMimeFromLang = function (lang) {
        var types = TYPES[lang];
        if (types)
            return types[0];
        else
            return 'application/xquery';
    };
};

/**
 * Track properties of the currently edited resource.
 * 
 * @param name
 * @param path
 * @param mimeType
 */
function Resource(name, path, mimeType) {
    this.name = name || 'New';
    this.path = path || null;
    this.data = null;
    this.setMimeType(mimeType);
    log('new resource: ' + this.name + '; mime = ' + this.mimeType + '; lang = ' + this.lang);
}

/**
 * Change the mime-type of the resource. This will also change
 * the language used for syntax highlighting.
 * 
 * @param mimeType
 */
Resource.prototype.setMimeType = function (mimeType) {
    this.mimeType = mimeType || 'application/xquery';
    this.mimeType = this.mimeType.replace(/(.*);.*$/, '$1');
    this.lang = MimeTypes.getLangFromMime(this.mimeType);
};

Resource.prototype.setLang = function (lang) {
    this.lang = lang;
    this.mimeType = MimeTypes.getMimeFromLang(lang);
};

Resource.prototype.isNew = function () {
    return this.path == null;
};

/** Resize the query result output div. We want this to have a fixed height,
 *  so it neatly fits into the browser window.
 */
function resize() {
    var editor = document.getElementById('codeEditor');
    Dom.setStyle(editor, 'width', (document.getElementById('query-panel').offsetWidth - 15) + 'px');
    Dom.setStyle(editor, 'height', '175px');
    var output = document.getElementById('output');
    Dom.setStyle(output, 'height', (document.body.clientHeight - output.offsetTop) - 15 + "px");
}

/**
 * Maximize/restore the editor panel.
 * 
 * @param minimizeOnly
 */
function resizeQueryBox(minimizeOnly) {
	var element = document.getElementById('maximize');
    var editor = document.getElementById('codeEditor');
    var newHeight;
    var anim;
    if (document.quMaximized) {
        newHeight = 170;
        anim = new YAHOO.util.Anim(editor, { height: { to: newHeight } });
        anim.animate();
        document.quMaximized = false;
        element.innerHTML = "Maximize";
    } else if (!minimizeOnly) {
        newHeight = (document.body.clientHeight - editor.offsetTop - 50);
        anim = new YAHOO.util.Anim(editor, { height: { to: newHeight } });
        anim.animate();
        document.quMaximized = true;
		element.innerHTML = "Minimize";
	}
	return false;
}

/**
 * Load a resource from the database. Set mime-type and language
 * of the currently edited resource.
 */
function loadQuery() {
    var dialog = new DBBrowser({createNew: false, title: 'Load'});
    dialog.closeEvent.subscribe(function (type, args) {
        log('Loading query from ' + args[1]);
        var callback = {
            success: function (response) {
                currentResource = new Resource(args[0], args[1], response.getResponseHeader['Content-Type']);
                currentResource.data = response.responseText;
                initEditor(currentResource.data, currentResource.lang);
            },
            failure: requestFailed
        };
        YAHOO.util.Connect.asyncRequest('GET', 'load.xql?load=' + encodeURIComponent(args[1]), callback);
    });
    dialog.show();
}

/**
 * Save the currently edited resource. If the resource has not been saved
 * before or saveAs is true, a file selection dialog is shown.
 * 
 * @param saveAs
 */
function doSave(saveAs) {
    if (saveAs || currentResource.isNew()) {
        var dialog = new DBBrowser({createNew: true, title: 'Save As'});
        dialog.closeEvent.subscribe(function (type, args) {
            log('Storing query to ' + args[1] + '/' + args[0]);
            var code = document.getElementById('codeEditor').value;
            var callback = {
                success: function (response) {
                    currentResource.name = args[0];
                    currentResource.path = args[1];
                    message('File saved to db.');
                },
                failure: requestFailed
            };
            YAHOO.util.Connect.setDefaultPostHeader(false);
            YAHOO.util.Connect.initHeader('Content-Type', currentResource.mimeType);
            YAHOO.util.Connect.asyncRequest('PUT', '/exist/rest' + args[1] + '/' + args[0], callback, code);
            YAHOO.util.Connect.setDefaultPostHeader(true);
        });
        dialog.show();
    } else {
        log('Storing query ' + currentResource.name + ' to ' + currentResource.path);
        var code = document.getElementById('codeEditor').value;
        var callback = {
            success: function (response) {
                message('File saved to ' + currentResource.path);
            },
            failure: requestFailed
        };
        YAHOO.util.Connect.setDefaultPostHeader(false);
        YAHOO.util.Connect.initHeader('Content-Type', currentResource.mimeType);
        YAHOO.util.Connect.asyncRequest('PUT', '/exist/rest' + currentResource.path, callback, code);
        YAHOO.util.Connect.setDefaultPostHeader(true);
    }
}

/** Retrieve the list of stored queries and populate the select box. */
function retrieveStored() {
	// workaround: adding <option>'s to a select doesn't work in
	// IE, so we replace the whole select here.
    var callback = {
        success: function (response) {
            var saved = document.getElementById('saved');
            var queries = document.getElementById('queries');
            queries.removeChild(saved);
            appendNode(queries, response.responseText);
            Event.addListener('saved', 'change', selectStored);
        },
        failure: requestFailed
    };
    log('Loading query examples ...');
    YAHOO.util.Connect.asyncRequest('GET', 'get-examples.xql', callback);
}

/**
 * User selected on of the example queries.
 */
function selectStored() {
    var saved = document.getElementById('saved');
    initEditor(saved.options[saved.selectedIndex].value);
    currentResource = new Resource();
    currentResource.data = saved.options[saved.selectedIndex].value;
    log('Query examples loaded.');
}

/** Called if the user clicked 'export'. */
function exportData() {
    var wrapperId = Dom.generateId();
    var dialog = new DBBrowser({
        createNew: true, title: 'Export Query Results',
        html:
            '<fieldset class="wrapper-select">' +
            '   <label>Wrapper element (optional)</label>' +
            '   <input type="text" id="' + wrapperId + '"/>' +
            '</fieldset>'
    });
    dialog.closeEvent.subscribe(function (type, args) {
        var wrapper = document.getElementById(wrapperId).value;
        var docName = args[0];
        var collection = args[1];

        log("Exporting to doc: " + docName + "; " + collection);
        var params = 'export=' + docName + '&collection=' + collection +
            '&wrapper=' + wrapper;
        var callback = {
            success: function (response) {
                message('File saved to db.');
            },
            failure: requestFailed
        };
        YAHOO.util.Connect.asyncRequest('POST', 'sandbox.xql', callback, params);
    });
    dialog.show();
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

/**
 * Compile the query and display errors.
 */
function compileAndCheck() {
    log('Checking syntax...');
    message('Checking syntax ...');
    var code = document.getElementById('codeEditor').value;
    var params = 'check=' + escapeQuery(code);
    var callback = {
        success: function (response) {
            var xml = response.responseXML;
            if (!xml) {
                error(response.responseText);
                return;
            }
            var root = xml.documentElement;
            if (!root.hasChildNodes())
                clearErrors();
            else {
                var msg = '';
                var node = root.firstChild;
                while (node != null) {
                    msg += node.nodeValue;
                    node = node.nextSibling;
                }
                error(msg);
            }
        },
        failure: requestFailed
    };
    YAHOO.util.Connect.asyncRequest('POST', 'sandbox.xql', callback, params);
	timer = null;
}

/** Called if the user clicked on the "send" button.
 *  Execute the query.
 */	
function execQuery() {
	document.getElementById('output').innerHTML = '';
	clearErrors();
    var code = document.getElementById('codeEditor').value;
    if (code.length == 0)
		return;
	resizeQueryBox(true);
	var params = 'qu=' + escapeQuery(code);
    var callback = {
        success: showQueryResponse,
        failure: requestFailed
    };
    YAHOO.util.Connect.asyncRequest('POST', 'execute', callback, params);
	message('Query sent ...');
}

/** Response handler: query executed, check for errors and
 *  initialize loading.
 */
function showQueryResponse(request) {
    var xml = request.responseXML;
    if (!xml) {
        error(request.responseText);
        return;
    }
	var root = xml.documentElement;
	if (root.nodeName == 'error') {
        var msg = '';
        var node = root.firstChild;
        while (node != null) {
            msg += node.nodeValue;
            node = node.nextSibling;
        }
        error(msg);
	} else {
        var hits = root.getAttribute('hits');
        log('Found: ' + hits);
        var elapsed = root.getAttribute('elapsed');
		document.getElementById('query-result').innerHTML =
			"Found " + hits + " in " + elapsed + " seconds.";
		startOffset = 1;
		currentOffset = 1;
		hitCount = hits;
		var howmany = document.getElementById('howmany');
		endOffset = startOffset + (howmany.options[howmany.selectedIndex].text - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
			
		document.getElementById('current').innerHTML = "Showing items " + startOffset +
			" to " + endOffset;
		message('Retrieving results ...', false);
		retrieveNext();
	}
}

/** Called if user clicks on "forward" link in query results. */
function browseNext() {
	if (currentOffset > 0 && endOffset < hitCount) {
		startOffset = currentOffset;
        var howmany = Dom.get('howmany');
        endOffset = currentOffset + (howmany.options[howmany.selectedIndex].value - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
		document.getElementById('output').innerHTML = "";
		document.getElementById('current').innerHTML = "Showing items " + startOffset +
			" to " + endOffset;
		retrieveNext();
	}
	return false;
}

/** Called if user clicks on "previous" link in query results. */
function browsePrevious() {
	if (currentOffset > 0 && startOffset > 1) {
        var howmany = Dom.get('howmany');
        var count = howmany.options[howmany.selectedIndex].value;
        startOffset = startOffset - count;
		if (startOffset < 1)
			startOffset = 1;
		currentOffset = startOffset;
		endOffset = currentOffset + (count - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
		document.getElementById('output').innerHTML = "";
		document.getElementById('current').innerHTML = "Showing items " + startOffset +
			" to " + endOffset;
		retrieveNext();
	}
	return false;
}

function requestFailed(request) {
	document.getElementById('query-result').innertHTML =
		"The request to the server failed.";
}

/** If there are more query results to load, retrieve
 *  the next result.
 */
function retrieveNext() {
    if (currentOffset > 0 && currentOffset <= endOffset) {
        var url = 'results/' + currentOffset;
		currentOffset++;
        var callback = {
            success: itemRetrieved,
            failure: requestFailed
        };
        YAHOO.util.Connect.asyncRequest('GET', url, callback);
	} else {
        message('', false);
    }
}

/** Response handler: insert the retrieved result. */
function itemRetrieved(request) {
    var output = document.getElementById('output');
    appendNode(output, request.responseText);
    retrieveNext();
}

function appendNode(el, html) {
    if(el.insertAdjacentHTML) {
        el.insertAdjacentHTML('BeforeEnd', html);
        return el.lastChild;
    } else {
        if(el.lastChild) {
            var range = el.ownerDocument.createRange();
            range.setStartAfter(el.lastChild);
            var frag = range.createContextualFragment(html);
            el.appendChild(frag);
            return el.lastChild;
        } else {
            el.innerHTML = html;
            return el.lastChild;
        }
    }
}

/** Save the current query as example code */
function saveQuery() {
    var code = document.getElementById('codeEditor').value;
	if (code.length == 0) {
		message('Document does not contain any text!');
		return;
	}
    var saveDialog = new YAHOO.widget.Dialog(Dom.generateId(),
        {
            width: '300px',
            fixedcenter: true,
            visible: false,
            buttons: [
                    {text: 'Cancel', handler: function () { this.hide(); this.destroy(); }, isDefault: true},
                   {text: 'Save', handler: doSaveQuery, isDefault: false}
            ]
        });
    saveDialog.setBody(
        '<fieldset>' +
        '   <label for="description">Description</label>' +
        '   <input type="text"/>' +
        '</fieldset>'
    );
    saveDialog.validate = function () {
        var description = this.body.getElementsByTagName('input')[0].value;
        if (description == '')
            return false;
        else
            return true;
    };
    saveDialog.render(document.body);
    saveDialog.show();
}

/** Save the query to examples.xml */
function doSaveQuery() {
    var description = this.body.getElementsByTagName('input')[0].value;
    var code = document.getElementById('codeEditor').value;
	if (code == '') {
		alert("No query to save!");
		return;
	}
	var params = 'qu=' + escapeQuery(code) + '&save=' + description;
    var callback = {
        success: queryStored,
        failure: requestFailed
    };
    YAHOO.util.Connect.asyncRequest('POST', 'sandbox.xql', callback, params);
    this.hide();
    this.destroy();
}

function queryStored(request) {
    message('Query saved as example.');
    retrieveStored();
}

/** Initialize cookies and slots */
function initSlots() {
	if (document.cookie.length > 0) {
		var clen = document.cookie.length;
		for (i = 0; i < clen; ) {
			var j = document.cookie.indexOf('=', i);
			var name = document.cookie.substring(i, j);
			if (name.substring(0, 4) == 'slot') {
				var slot = document.getElementById(name);
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
    var currentQuery = document.getElementById('codeEditor').value;
	var cookie = getCookie(id);
	if (cookie != null) {
        initEditor(cookie, 'xquery');
        currentResource = new Resource();
        currentResource.data = cookie;
    }
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

function escapeQuery(query) {
	return encodeURIComponent(query);
}

/** Display a message in the message area on top of the page */
function message(msg, timeout) {
    document.getElementById('messages').innerHTML = msg;
    if (timeout)
        setTimeout(function() { document.getElementById('messages').innerHTML = ''; }, 3000);
}

/** Display an error message in the error panel */
function error(msg) {
    var html =
        '<a href="#" class="errors-found" onclick="showErrorPanel()">Errors found. Click for more...</a>';
    document.getElementById('messages').innerHTML = html;
    document.getElementById('error-text').innerHTML = msg;
}

/** Clear all error messages */
function clearErrors() {
    document.getElementById('error-text').innerHTML = '';
    document.getElementById('messages').innerHTML = '';
    hideErrorPanel();
}

function showErrorPanel() {
    var panel = document.getElementById('errors');
    if (Dom.getStyle(panel, 'display') == 'none') {
        Dom.setStyle(panel, 'height', '0px');
        Dom.setStyle(panel, 'display', '');
        var anim = new YAHOO.util.Anim(panel, { height: { to: 80 } }, .5);
        anim.animate();
    }
}

function hideErrorPanel() {
    var panel = document.getElementById('errors');
    if (Dom.getStyle(panel, 'display') != 'none') {
        var anim = new YAHOO.util.Anim(panel, { height: { to: 0 } }, .5);
        anim.onComplete.subscribe(function () { Dom.setStyle(panel, 'display', 'none'); });
        anim.animate();
    }
}

/**
 * File dialog to browse the database contents of the local
 * eXist instance. Can be used to either select an existing
  * resource or to create a new one. 
 * @param config
 */
function DBBrowser(config) {
    config = config || {};
    this.inputId = Dom.generateId();
    this.createNew = config.createNew || false;
    this.collection = config.collection || '/db';
    var id = config.id || Dom.generateId();
    var title = config.title || 'Select';
    var tableId = Dom.generateId();
    var customHtml = config.html;

    var html =  '<div id="' + tableId + '" class="dbbrowser-table"></div>';
    if (this.createNew)
        html +=
            '<fieldset>' +
            '   <label for="path">Filename</label>' +
            '   <input type="text" id="' + this.inputId + '" value=""/>' +
            '</fieldset>';
    if (customHtml)
        html += customHtml;
    DBBrowser.superclass.constructor.call(this, id, {
        postmethod: 'manual',
        width: "500px",
        close: true,
        fixedcenter: true,
        draggable: true,
        visible: false,
        constraintoviewport: true,
        buttons: [
            { text: "Cancel", handler: function () { this.hide(); this.destroy(); }, isDefault: true },
            { text: "OK", handler: function () { this.done(); }, isDefault: true }
        ]
    });
    this.closeEvent = new YAHOO.util.CustomEvent('closeEvent');
    
    this.setHeader(title);
    this.setBody(html);
    this.render(document.body);
    Dom.addClass(this.body, 'file-dialog');
    
    var formatName = function(cell, record, column, data) {
        if (record.getData('type') == 'collection')
            Dom.addClass(cell, 'is-collection');
        else
            Dom.addClass(cell, 'is-resource');
        cell.innerHTML = data;
    };
    var ds = new YAHOO.util.DataSource('browse.xql?');
    ds.responseType = YAHOO.util.DataSource.TYPE_XML;
    ds.responseSchema = {
        resultNode: 'item',
        fields: ['name', 'type','path','mime', 'size']
    };
    var columnDefs = [
        { key: 'name', label: 'Name', formatter: formatName },
        { key: 'mime', label: 'Mimetype' },
        { key: 'size', label: 'Size' }
    ];
    this.dt = new YAHOO.widget.DataTable(tableId, columnDefs, ds,
        {initialRequest: 'collection=' + this.collection, selectionMode: 'single',
        scrollable:false});
    this.dt.subscribe("rowMouseoverEvent", this.dt.onEventHighlightRow);
    this.dt.subscribe("rowMouseoutEvent", this.dt.onEventUnhighlightRow);

    this.dt.subscribe('rowClickEvent', this.onRowClick, this, true);
};

YAHOO.lang.extend(DBBrowser, YAHOO.widget.Dialog);

DBBrowser.prototype.onRowClick = function (args) {
    var record = this.dt.getRecord(args.target);
    if (record.getData('type') == 'resource') {
        this.dt.unselectAllRows();
        this.dt.selectRow(args.target);
        if (this.createNew)
            document.getElementById(this.inputId).value = record.getData('name');
    } else {
        this.collection = record.getData('path');
        log('Browsing collection ' + this.collection);
        this.dt.getDataSource().sendRequest('collection=' + this.collection, this.dt.onDataReturnInitializeTable, this.dt);
    }
};

DBBrowser.prototype.done = function () {
    if (this.createNew) {
        var name = document.getElementById(this.inputId).value;
        this.closeEvent.fire(name, this.collection);
    } else {
        var selected = this.dt.getSelectedRows();
        if (selected && selected.length > 0) {
            var row = this.dt.getRecord(selected[0]);
            this.closeEvent.fire(row.getData('name'), row.getData('path'));
        }
    }
    this.destroy();
};
