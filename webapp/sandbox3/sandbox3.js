$(function() {
	$(window).resize(resizeWindow);
	resizeWindow();
	
	editAreaLoader.init({
		id : "editor",
		syntax: "xml",
		start_highlight: true,
		is_multi_files: true,
		allow_toggle: false,
		allow_resize: "no",
		replace_tab_by_spaces: 4,
		toolbar: "sandbox_exec, sandbox_compile, |, sandbox_new, load, save, |, search, go_to_line, fullscreen, |, " +
				"undo, redo, |, select_font,|, syntax_selection, " +
				"reset_highlight, word_wrap, |, help",
		plugins: "sandbox",
		load_callback: "openDocument",
		EA_load_callback: "newDocument",
		save_callback: "saveDocument",
		syntax_selection_allow: "css,html,java,js,sql,tsql,xml,xquery"
	});
	
	$('#results-container .next').click(browseNext);
	$('#results-container .previous').click(browsePrevious);
	
	$("#collection-browser").collectionBrowser();
	
	$("#open-dialog").dialog({
		title: "Open File",
		modal: true,
        autoOpen: false,
        height: 400,
        width: 900,
        resize: function() { $("#collection-browser").collectionBrowser('resize'); },
		open: function() { $("#collection-browser").collectionBrowser('resize'); }
	});
	
	message("Press <code>Ctrl-Space</code> for function completion, <code>Ctrl-Enter</code> to execute query, " +
			"<code>Alt-Enter</code> to check for errors.");
});

var newDocumentCounter = 0;
var hitCount = 0;
var startOffset = 0;
var currentOffset = 0;
var endOffset = 0;

function newDocument() {
	editAreaLoader.openFile('editor', {
		id: "_new" + ++newDocumentCounter,
		title: "Unsaved " + newDocumentCounter,
		syntax: "xquery",
		text: "xquery version \"1.0\";\n\n"
	});
}

function openDocument() {
	$("#open-dialog").dialog("option", "buttons", { 
		"cancel": function() { $(this).dialog("close"); },
		"open": doOpenDocument
	});
	$("#open-dialog").dialog("open");
}

function saveDocument() {
	if (editAreaLoader.getCurrentFile('editor').id.substring(0, 4) == '_new') {
		$("#open-dialog").dialog("option", "buttons", { 
			"Cancel": function() { $(this).dialog("close"); },
			"Save": doSaveDocument,
		});
		$("#open-dialog").dialog("open");
	} else
		doSaveDocument();
}

function doSaveDocument() {
	var data = editAreaLoader.getCurrentFile('editor');
	var resource;
	if (data.id.substring(0, 4) == '_new')
		resource = $("#collection-browser").collectionBrowser("selection");
	else
		resource = data.id;
	var contentType = data.mimeType;
	if (contentType == null)
		contentType = MimeTypes.getMimeFromLang(data.syntax);
	
	var notice = $.pnotify({
		pnotify_text: "Storing resource...",
		pnotify_hide: false,
		pnotify_closer: false,
		pnotify_opacity: .75,
	});
	$(this).dialog("close");
	$.ajax({
		url: "../rest" + resource,
		type: "PUT",
		data: data.text,
		contentType: contentType,
		success: function () {
			notice.pnotify({
				pnotify_text: resource + " stored.",
				pnotify_hide: true,
				pnotify_closer: true,
			});
			if (data.id.substring(0, 4) == "_new") {
				editAreaLoader.closeFile('editor', data.id);
				data.id = resource;
				data.title = resource;
				editAreaLoader.openFile('editor', data);
			} else {
				editAreaLoader.setFileEditedMode("editor", data.id, false);
			}
		},
		error: function (xhr, status) {
			error(xhr.responseText);
		}
	});
}

function doOpenDocument() {
	var resource = $("#collection-browser").collectionBrowser("selection");
	$.ajax({
		url: "load.xql?path=" + resource,
		dataType: 'text',
		success: function (data, status, xhr) {
		message("Opening resource " + resource);
			var syntax = MimeTypes.getLangFromMime(xhr.getResponseHeader("Content-Type"));
			
			editAreaLoader.openFile("editor", {
				id: resource,
				title: resource,
				text: data,
				syntax: syntax,
				mimeType: xhr.getResponseHeader("Content-Type")
			});
		}
	});
	$(this).dialog("close");
}

function resizeWindow() {
	$("#results-container").height(Math.ceil($(window).height() - $("#results-container").offset().top - 30));
}

function checkQuery() {
	var editor = editAreaLoader.getCurrentFile('editor');
	var code = editor.text;
	$.ajax({
		url: "compile.xql",
		dataType: "xml",
		data: { "qu": code },
		success: function (xml) {
			var elem = xml.documentElement;
			if (elem.nodeName == 'error') {
		        var msg = $(elem).text();
		        $.pnotify({
					pnotify_title: 'Compilation Error',
					pnotify_text: msg,
					pnotify_type: 'error',
					pnotify_shadow: true,
					pnotify_hide: true
				});
		        var line = /line\s+\d+/.exec(msg);
		        if (line) {
		        	var l = /\d+/.exec(line);
		        	editAreaLoader.execCommand("editor", "go_to_line", "" + l);
		        }
			} else {
				message("Compilation PASSED.");
			}
		},
		error: function (xhr, status) {
			$.pnotify({
				pnotify_title: 'Shadow Error',
				pnotify_text: xhr.responseText,
				pnotify_type: 'error',
				pnotify_shadow: true
			});
		}
	});
}

function runQuery() {
	var editor = editAreaLoader.getCurrentFile('editor');
	var code = editor.text;
	$('#results-container .results').empty();
	$.ajax({
		url: "execute",
		dataType: "xml",
		data: { "qu": code },
		success: function (xml) {
			var elem = xml.documentElement;
			if (elem.nodeName == 'error') {
		        var msg = $(elem).text();
		        $.pnotify({
					pnotify_title: 'Compilation Error',
					pnotify_text: msg,
					pnotify_type: 'error',
					pnotify_shadow: true,
					pnotify_hide: true
				});
			} else {
				startOffset = 1;
				currentOffset = 1;
				hitCount = elem.getAttribute("hits");
				endOffset = startOffset + 10 - 1;
				if (hitCount < endOffset)
					endOffset = hitCount;
				message("Found " + hitCount + " in " + elem.getAttribute("elapsed"));
				retrieveNext();
			}
		},
		error: function (xhr, status) {
			$.pnotify({
				pnotify_title: 'Shadow Error',
				pnotify_text: xhr.responseText,
				pnotify_type: 'error',
				pnotify_shadow: true
			});
		}
	});
}

/** If there are more query results to load, retrieve
 *  the next result.
 */
function retrieveNext() {
    if (currentOffset > 0 && currentOffset <= endOffset) {
        var url = 'results/' + currentOffset;
		currentOffset++;
		$.ajax({
			url: url,
			dataType: 'html',
			success: function (data) {
				$('#results-container .results').append(data);
				$("#results-container .current").text("Showing results " + startOffset + " to " + (currentOffset - 1) +
						" of " + hitCount);
				retrieveNext();
			}
		});
	} else {
    }
}

/** Called if user clicks on "forward" link in query results. */
function browseNext() {
	if (currentOffset > 0 && endOffset < hitCount) {
		startOffset = currentOffset;
        var howmany = 10;
        endOffset = currentOffset + howmany;
		if (hitCount < endOffset)
			endOffset = hitCount;
		$("#results-container .results").empty();
		retrieveNext();
	}
	return false;
}

/** Called if user clicks on "previous" link in query results. */
function browsePrevious() {
	if (currentOffset > 0 && startOffset > 1) {
        var count = 10;
        startOffset = startOffset - count;
		if (startOffset < 1)
			startOffset = 1;
		currentOffset = startOffset;
		endOffset = currentOffset + (count - 1);
		if (hitCount < endOffset)
			endOffset = hitCount;
		$("#results-container .results").empty();
		retrieveNext();
	}
	return false;
}

function functionLookup(prefix, callback) {
	$.get("docs.xql", { prefix: prefix}, function (data) {
		callback(data);
	});
}

function message(message) {
	$.pnotify({
		pnotify_text: message,
		pnotify_shadow: true,
		pnotify_hide: true
	});
}

function error(message) {
	$.pnotify({
		pnotify_text: message,
		pnotify_type: 'error',
		pnotify_shadow: true,
		pnotify_hide: true
	});
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
        'js': ['application/x-javascript']
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