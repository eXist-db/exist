/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

eXide.namespace("eXide.edit.Document");

/**
 * Represents an open document.
 */
eXide.edit.Document = (function() {
	
	Constr = function(name, path, session) {
		this.name = name;
		this.path = path;
		this.mime = "application/xquery";
		this.syntax = "xquery";
		this.saved = false;
		this.editable = true;
		this.functions = [];
		
		this.$session = session;
	};
	
	Constr.prototype = {
		
		getText: function() {
			return this.$session.getValue();
		},
		
		getName: function() {
			return this.name;
		},
		
		getPath: function() {
			return this.path;
		},
		
		getBasePath: function() {
			return this.path.replace(/(^.+)\/[^\/]*$/, "$1");
		},
		
		getMime: function() {
			return this.mime;
		},
		
		getSyntax: function() {
			return this.syntax;
		},
		
		isSaved: function() {
			return this.saved;
		},
		
		isEditable: function() {
			return this.editable;
		},
		
		isXQuery: function() {
			return this.mime == "application/xquery";
		}
	};
	return Constr;
}());

eXide.namespace("eXide.edit.Editor");

/**
 * The main editor component. Handles the ACE editor as well as tabs, keybindings, commands...
 */
eXide.edit.Editor = (function () {
	
	var Renderer = require("ace/virtual_renderer").VirtualRenderer;
	var Editor = require("ace/editor").Editor;
	var EditSession = require("ace/edit_session").EditSession;
    var UndoManager = require("ace/undomanager").UndoManager;

    var RE_FUNC_NAME = /^[\$\w:\-_\.]+/;
    
	Constr = function(container) {
		var $this = this;
		$this.container = container;
		$this.documents = [];
		$this.activeDoc = null;
		$this.tabCounter = 0;
		$this.newDocCounter = 0;
		$this.pendingCheck = false;
		$this.events = {
				"activate": [],
				"validate": [],
				"close": []
		};
		
		eXide.edit.commands.init($this);
		
		var catalog = require("pilot/plugin_manager").catalog;
	    catalog.registerPlugins([ "pilot/index" ]);
	    
	    var renderer = new Renderer($this.container, "ace/theme/eclipse");
	    renderer.setShowGutter(true);
	    
		this.editor = new Editor(renderer);
		
	    this.editor.setKeyboardHandler(eXide.keyboard.getKeybinding());
	    
	    this.outline = new eXide.edit.Outline(this);
	    
	    this.status = document.getElementById("error-status");
	    $(this.status).click(function (ev) {
	    	ev.preventDefault();
	    	var path = this.pathname;
	    	var line = this.hash.substring(1);
	    	var doc = $this.getDocument(path);
	    	if (doc) {
	    		$this.switchTo(doc);
	    		$this.editor.gotoLine(parseInt(line) + 1);
	    	}
	    });
	    
	    this.lastChangeEvent = new Date().getTime();
		this.validateTimeout = null;
	};
	
	Constr.prototype = {

		init: function() {
		    if (this.documents.length == 0)
		    	this.newDocument();
		},
		
		getActiveDocument: function() {
			return this.activeDoc;
		},
		
		getText: function() {
			return this.activeDoc.getText();
		},
		
		newDocument: function() {
			var $this = this;
			var newDocId = 0;
			for (var i = 0; i < $this.documents.length; i++) {
				var doc = $this.documents[i];
				if (doc.path.match(/^__new__(\d+)/)) {
					newDocId = parseInt(RegExp.$1);
				}
			}
			newDocId++;
			var newDocument = new eXide.edit.Document("new-document " + newDocId,
					"__new__" + newDocId, new EditSession("xquery version \"1.0\";\n"));
			newDocument.$session.setUndoManager(new UndoManager());
			newDocument.$session.addEventListener("change", function () {
				newDocument.saved = false;
				$this.triggerCheck();
			});
			var XQueryMode = require("ace/mode/xquery").Mode;
			newDocument.$session.setMode(new XQueryMode());
			
			this.addTab(newDocument);
			
			this.editor.setSession(newDocument.$session);
			this.editor.focus();
		},
		
		newDocumentWithText: function(data, mime, resource) {
			var doc = new eXide.edit.Document(resource.name, resource.path, new EditSession(data));
			doc.editable = resource.writable;
			doc.mime = mime;
			doc.syntax = eXide.util.mimeTypes.getLangFromMime(mime);
			doc.saved = false;
			this.$initDocument(doc);
		},
		
		openDocument: function(data, mime, resource) {
			var $this = this;
			if (!resource.writable)
				eXide.util.message("Opening " + resource.path + " readonly!");
			else
				eXide.util.message("Opening " + resource.path);

			var doc = new eXide.edit.Document(resource.name, resource.path, new EditSession(data));
			doc.editable = resource.writable;
			doc.mime = mime;
			doc.syntax = eXide.util.mimeTypes.getLangFromMime(mime);
			doc.saved = true;
			$.log("opening %s, mime: %s, syntax: %s", resource.name, doc.mime, doc.syntax);
			this.$initDocument(doc);
		},
		
		$initDocument: function (doc) {
			var $this = this;
			$this.$setMode(doc);
			doc.$session.setUndoManager(new UndoManager());
			doc.$session.addEventListener("change", function () {
				if (doc.saved) {
					doc.saved = false;
					$this.updateTabStatus(doc.path, doc);
				}
				$this.triggerCheck();
			});
			$this.addTab(doc);
			
			$this.editor.setSession(doc.$session);
			$this.editor.resize();
			$this.editor.focus();
		},
		
		setMode: function(mode) {
			this.activeDoc.syntax = mode;
			this.$setMode(this.activeDoc, true);
		},
		
		$setMode: function(doc, setMime) {
			switch (doc.syntax) {
			case "xquery":
				var XQueryMode = require("ace/mode/xquery").Mode;
				doc.$session.setMode(new XQueryMode());
				if (setMime)
					doc.mime = "application/xquery";
				break;
			case "xml":
				var XmlMode = require("ace/mode/xml").Mode;
				doc.$session.setMode(new XmlMode());
				if (setMime)
					doc.mime = "application/xml";
				break;
			case "html":
				var HtmlMode = require("ace/mode/html").Mode;
				doc.$session.setMode(new HtmlMode());
				if (setMime)
					doc.mime = "text/html";
				break;
			case "javascript":
				var JavascriptMode = require("ace/mode/javascript").Mode;
				doc.$session.setMode(new JavascriptMode());
				if (setMime)
					doc.mime = "application/x-javascript";
				break;
			case "css":
				var CssMode = require("ace/mode/css").Mode;
				doc.$session.setMode(new CssMode());
				if (setMime)
					doc.mime = "text/css";
				break;
			}
		},
		
		closeDocument: function() {
			this.$triggerEvent("close", [this.activeDoc]);
			$("#tabs a[title=" + this.activeDoc.path + "]").parent().remove();
			for (var i = 0; i < this.documents.length; i++) {
				if (this.documents[i].path == this.activeDoc.path) {
					this.documents.splice(i, 1);
				}
			}
			if (this.documents.length == 0)
				this.newDocument();
			else {
				this.activeDoc = this.documents[this.documents.length - 1];
				$("#tabs a[title=" + this.activeDoc.path + "]").addClass("active");
				this.editor.setSession(this.activeDoc.$session);
				this.editor.resize();
				this.$triggerEvent("activate", [this.activeDoc]);
			}
		},
		
		saveDocument: function(resource, successHandler, errorHandler) {
			var $this = this;
			var oldPath = $this.activeDoc.path;
			var oldName = $this.activeDoc.name;
			if (resource) {
				$this.activeDoc.path = resource.path,
				$this.activeDoc.name = resource.name
			}
			
			eXide.util.message("Storing resource " + $this.activeDoc.name + "...");
			
			var params = {
					path: $this.activeDoc.path,
					data: $this.activeDoc.getText()
			};
			if ($this.activeDoc.mime)
				params.mime = $this.activeDoc.mime;
			$.ajax({
				url: "store.xql",
				type: "POST",
				data: params,
				dataType: "json",
				success: function (data) {
				    if (data.status == "error") {
						if (errorHandler) {
							errorHandler.apply($this.activeDoc, [data.message]);
						} else {
							eXide.util.error(data.message);
						}
					} else {
						$this.activeDoc.saved = true;
						$this.updateTabStatus(oldPath, $this.activeDoc);
						if (successHandler) {
							successHandler.apply($this.activeDoc);
						} else {
							eXide.util.message($this.activeDoc.name + " stored.");
						}
					}
				},
				error: function (xhr, status) {
					// reset old path and name
					$this.activeDoc.path = oldPath;
					$this.activeDoc.name = oldName;
					if (errorHandler) {
						errorHandler.apply($this.activeDoc, xhr.responseText);
					} else {
						eXide.util.error(xhr.responseText);
					}
				}
			});
		},

		/**
		 * Scan open documents and return the one matching path
		 */
		getDocument: function(path) {
			path = eXide.util.normalizePath(path);
			for (var i = 0; i < this.documents.length; i++) {
				if (this.documents[i].path == path)
					return this.documents[i];
			}
		},
		
		autocomplete: function() {
			if (!this.activeDoc.isXQuery()) {
				return;
			}
			var lang = require("pilot/lang");
			var Range = require("ace/range").Range;
			
		    var sel   = this.editor.getSelection();
		    var session   = this.editor.getSession();
		    
			var lead = sel.getSelectionLead();
			var line = session.getDisplayLine(lead.row);
			var start = lead.column - 1;
			var end = lead.column;
			while (start >= 0) {
				var ch = line.substring(start, end);
				if (ch.match(/^\$[\w:\-_\.]+$/)) {
					break;
				}
				if (!ch.match(/^[\w:\-_\.]+$/)) {
					start++;
					break;
				}
				start--;
			}
			var token = line.substring(start, end);
			$.log("completing token: %s", token);
			var range = new Range(lead.row, start, lead.row, end);

			var pos = this.editor.renderer.textToScreenCoordinates(lead.row, lead.column);
			var editorHeight = $(this.container).height();
			if (pos.pageY + 150 > editorHeight) {
				pos.pageY = editorHeight - 150;
				$.log("window height: %i, pageY: %i", editorHeight, pos.pageY);
			}
			$("#autocomplete-box").css({ left: pos.pageX + "px", top: (pos.pageY + 10) + "px" });
			$("#autocomplete-help").css({ left: (pos.pageX + 324) + "px", top: (pos.pageY + 10) + "px" });
			
			if (token.length == 0) {
				this.templateLookup(this.activeDoc, token, range, true);
			} else {
				this.functionLookup(this.activeDoc, token, range, true);
			}
			return true;
		},
		
		$localVars: function(prefix, wordrange, complete) {
			var variables = [];
			var stopRegex = /declare function|};/;
			var varRegex = /let \$[\w\:]+|for \$[\w\:]+|\$[\w\:]+\)/;
			var getVarRegex = /\$[\w\:]+/;
			var nameRegex = new RegExp("^\\" + prefix);
			var session = this.editor.getSession();
			var row = wordrange.start.row;
			while (row > -1) {
				var line = session.getDisplayLine(row);
				var m;
				if (m = line.match(varRegex)) {
					$.log("Var: %s", m[0]);
					var name = m[0].match(getVarRegex);
					if (name[0].match(nameRegex)) {
						variables.push({
							name: name[0],
							type: "variable"
						});
					}
				}
				if (line.match(stopRegex)) {
					$.log("Stop: %s", line);
					return variables;
				}
				row--;
			}
			return variables;
		},
		
		functionLookup: function(doc, prefix, wordrange, complete) {
			var $this = this;
			// Call docs.xql to retrieve declared functions and variables
			$.ajax({
				url: "docs.xql",
				dataType: "text",
				type: "POST",
				data: { prefix: prefix},
				
				success: function (data) {
					data = $.parseJSON(data);
					
					var funcs = [];
					var regexStr;
					var isVar = prefix.substring(0, 1) == "$";
					
					if (isVar) {
						regexStr = "^\\" + prefix;
						funcs = $this.$localVars(prefix, wordrange, complete);
					} else {
						regexStr = "^" + prefix;
					}
					var regex = new RegExp(regexStr);
					
					// add local functions to the set
					var localFuncs = $this.activeDoc.functions;
					$.each(localFuncs, function (i, func) {
						if (func.name.match(regex)) {
							funcs.push(func);
						}
					});
					
					if (data)
						funcs = funcs.concat(data);
					
					// Create popup menu
					// add function defs
					var popupItems = [];
					for (var i = 0; i < funcs.length; i++) {
						var item = { 
								label: funcs[i].signature ? funcs[i].signature : funcs[i].name,
								type: funcs[i].type
						};
						if (funcs[i].help) {
							item.tooltip = funcs[i].help;
						}
						popupItems.push(item);
					}
					
					$this.$addTemplates(prefix, popupItems);
					
					$this.$showPopup(doc, wordrange, popupItems);
				},
				error: function(xhr, msg) {
					eXide.util.error(msg);
				}
			});
		},
		
		templateLookup: function(doc, prefix, wordrange, complete) {
			var popupItems = [];
			this.$addTemplates(prefix, popupItems);
			this.$showPopup(doc, wordrange, popupItems);
		},
		
		$addTemplates: function (prefix, popupItems) {
			// add templates
			var templates = this.outline.getTemplates(prefix);
			for (var i = 0; i < templates.length; i++) {
				var item = {
						type: "template",
						label: "[S] " + templates[i].name,
						tooltip: templates[i].help,
						template: templates[i].template
				};
				popupItems.push(item);
			}
		},
		
		$showPopup: function (doc, wordrange, popupItems) {
			// display popup
			var $this = this;
			eXide.util.popup($("#autocomplete-box"), $("#autocomplete-help"), popupItems,
					function (selected) {
						if (selected) {
							var expansion = selected.label;
							if (selected.type == "template") {
								expansion = selected.template;
							} else if (selected.type == "function") {
								expansion = eXide.util.parseSignature(expansion);
							}
							
							doc.template = new eXide.edit.Template($this, wordrange, expansion, selected.type);
							doc.template.insert();
						}
						$this.editor.focus();
					}
			);
		},
		
		getFunctionAtCursor: function (lead) {
			var row = lead.row;
		    var session = this.editor.getSession();
			var line = session.getDisplayLine(row);
			var start = lead.column;
			do {
				start--;
			} while (start >= 0 && line.charAt(start).match(RE_FUNC_NAME));
			start++;
			var end = lead.column;
			while (end < line.length && line.charAt(end).match(RE_FUNC_NAME)) {
				end++;
			}
			return line.substring(start, end);
		},
		
		showFunctionDoc: function () {
			var sel = this.editor.getSelection();
			var lead = sel.getSelectionLead();
			
			var pos = this.editor.renderer.textToScreenCoordinates(lead.row, lead.column);
			$("#autocomplete-box").css({ left: pos.pageX + "px", top: (pos.pageY + 20) + "px" });
			$("#autocomplete-help").css({ left: (pos.pageX + 324) + "px", top: (pos.pageY + 20) + "px" });
			var func = this.getFunctionAtCursor(lead);
			this.functionLookup(this.activeDoc, func, null, false);
		},
		
		gotoDefinition: function () {
			var sel = this.editor.getSelection();
			var lead = sel.getSelectionLead();
			var funcName = this.getFunctionAtCursor(lead);
			$.log("funcName = %s", funcName);
			if (funcName) {
				this.outline.gotoDefinition(this.activeDoc, funcName);
			}
		},
		
		gotoFunction: function (name) {
			var prefix = this.getModuleNamespacePrefix();
			if (prefix != null) {
				name = name.replace(/[^:]+:/, prefix + ":");
			}

			$.log("Goto function %s", name);
			var regexp = new RegExp("function\\s+" + name + "\\s*\\(");
			var len = this.activeDoc.$session.getLength();
			for (var i = 0; i < len; i++) {
				var line = this.activeDoc.$session.getLine(i);
				if (line.match(regexp)) {
					this.editor.gotoLine(i + 1);
					this.editor.focus();
					return;
				}
			}
		},
		
		gotoVarDecl: function (name) {
			var prefix = this.getModuleNamespacePrefix();
			if (prefix != null) {
				name = name.replace(/[^:]+:/, "$" + prefix + ":");
			}
			
			$.log("Goto variable declaration %s", name);
			var regexp = new RegExp("variable\\s+\\" + name);
			var len = this.activeDoc.$session.getLength();
			for (var i = 0; i < len; i++) {
				var line = this.activeDoc.$session.getLine(i);
				if (line.match(regexp)) {
					this.editor.gotoLine(i + 1);
					this.editor.focus();
					return;
				}
			}
		},
		
		getModuleNamespacePrefix: function () {
			var moduleRe = /^\s*module\s+namespace\s+([^=\s]+)\s*=/;
			var len = this.activeDoc.$session.getLength();
			for (var i = 0; i < len; i++) {
				var line = this.activeDoc.$session.getLine(i);
				var matches = line.match(moduleRe);
				if (matches) {
					return matches[1];
				}
			}
			return null;
		},
		
		resize: function () {
			this.editor.resize();
		},
		
		addTab: function(doc) {
			var $this = this;
			var tabId = "t" + $this.tabCounter++;
			var label = doc.name;
			if (!doc.saved)
				label += "*";
			
			$("#tabs li a").removeClass("active");
			
			var li = document.createElement("li");
			var tab = document.createElement("a");
			tab.appendChild(document.createTextNode(label));
			tab.className = "tab active";
			tab.id = tabId;
			tab.title = doc.path;
			li.appendChild(tab);
			$("#tabs").append(li);
			
			$(tab).click(function (ev) {
				ev.preventDefault();
				$this.switchTo(doc);
			});
			
			$this.activeDoc = doc;
			$this.documents.push(doc);
			$this.$triggerEvent("activate", [doc]);
		},
		
		nextTab: function() {
			if (this.documents.length < 2)
				return;
			var next = 0;
			for (var i = 0; i < this.documents.length; i++) {
				if (this.documents[i] == this.activeDoc) {
					next = i;
					break;
				}
			}
			if (next == this.documents.length - 1)
				next = 0;
			else
				next++;
			this.switchTo(this.documents[next]);
		},
		
		previousTab: function() {
			if (this.documents.length < 2)
				return;
			var next = 0;
			for (var i = 0; i < this.documents.length; i++) {
				if (this.documents[i] == this.activeDoc) {
					next = i;
					break;
				}
			}
			if (next == 0)
				next = this.documents.length - 1;
			else
				next--;
			this.switchTo(this.documents[next]);
		},
		
		switchTo: function(doc) {
			this.editor.setSession(doc.$session);
			this.editor.resize();
			this.activeDoc = doc;
			$("#tabs a").each(function () {
				if (this.title == doc.path)
					$(this).addClass("active");
				else
					$(this).removeClass("active");
			});
			this.status.innerHTML = "";
			this.$triggerEvent("activate", [doc]);
		},
		
		updateTabStatus: function(oldPath, doc) {
			var label;
			if (!doc.saved)
				label = doc.name + "*";
			else
				label = doc.name;
			$("#tabs a[title=" + oldPath + "]").attr("title", doc.path).text(label);
		},
		
		setTheme: function(theme) {
			$.log("Changing theme to %s", theme);
			this.editor.setTheme(theme);
		},
		
		triggerCheck: function() {
			if (this.activeDoc.isXQuery()) { 
				var $this = this;
				if ($this.pendingCheck) {
					return;
				}
				var time = new Date().getTime();
				if ($this.validateTimeout && time - $this.lastChangeEvent < 2000) {
					clearTimeout($this.validateTimeout);
				}
				$this.lastChangeEvent = time;
				$this.validateTimeout = setTimeout(function() { $this.validateQuery.apply($this); }, 2000);
			}
		},

		validateQuery: function() {
			if (!this.activeDoc.isXQuery) {
				return;
			}
			this.pendingCheck = true;
			$.log("Running validation...");
			var $this = this;
			var code = $this.getText();
			var basePath = "xmldb:exist://" + $this.activeDoc.getBasePath();
			
			$.ajax({
				type: "POST",
				url: "compile.xql",
				data: {q: code, base: basePath},
				dataType: "json",
				success: function (data) {
					$this.compileError(data);
					$this.pendingCheck = false;
				},
				error: function (xhr, status) {
					$this.pendingCheck = false;
					$.log("Compile error: %s - %s", status, xhr.responseText);
				}
			});
			$this.$triggerEvent("validate", [$this.activeDoc]);
		},
		
		/*
		 * Cannot compile xquery: XPDY0002 : variable '$b' is not set. [at line 5, column 6, source: String]
		 */
		evalError: function(msg) {
			var str = /.*line\s(\d+)/i.exec(msg);
			var line = -1;
			if (str) {
				line = parseInt(str[1]);
			}
			$.log("error in line %d", str[1]);
			this.editor.focus();
			this.editor.gotoLine(line);
			
			var annotation = [{
					row: line - 1,
					text: msg,
					type: "error"
			}];
			this.status.innerHTML = msg;
			this.editor.getSession().setAnnotations(annotation);
		},
		
		/*
		 * { "result" : "fail", "error" : { "line" : "52", "column" : "43", "#text" : "XPDY0002
		 */
		compileError: function(data) {
			if (data.result == "fail") {
				var msg;
				if (data.error.line) {
					msg = data.error["#text"];
				} else {
					msg = data.error;
				}
				var str = /.*line:?\s(\d+)/i.exec(msg);
				var line = -1;
				if (str) {
					line = parseInt(str[1]) - 1;
				} else if (data.error.line) {
					line = parseInt(data.error.line) - 1;
				}
				var annotation = [{
					row: line,
					text: msg,
					type: "error"
				}];
				this.status.innerHTML = msg;
				this.status.href = this.activeDoc.path + "#" + line;
				this.editor.getSession().setAnnotations(annotation);
			} else {
				this.clearErrors();
				this.status.innerHTML = "";
			}
		},
		
		clearErrors: function () {
			this.editor.getSession().clearAnnotations();
		},
		
		saveState: function() {
			var i = 0;
			$.each(this.documents, function (index, doc) {
				if (doc.path.match('^__new__.*')) {
					localStorage["eXide." + i + ".path"] = doc.path;
					localStorage["eXide." + i + ".name"] = doc.name;
					localStorage["eXide." + i + ".mime"] = doc.mime;
					localStorage["eXide." + i + ".data"] = doc.getText();
				} else {
					localStorage["eXide." + i + ".path"] = doc.path;
					localStorage["eXide." + i + ".name"] = doc.name;
					localStorage["eXide." + i + ".mime"] = doc.mime;
					localStorage["eXide." + i + ".writable"] = (doc.editable ? "true" : "false");
					if (!doc.saved)
						localStorage["eXide." + i + ".data"] = doc.getText();
				}
				i++;
			});
			localStorage["eXide.documents"] = i;
		},
		
		addEventListener: function (name, obj, callback) {
			var event = this.events[name];
			if (event) {
				event.push({
					obj: obj,
					callback: callback
				});
			}
		},
		
		$triggerEvent: function (name, args) {
			var event = this.events[name];
			if (event) {
				for (var i = 0; i < event.length; i++) {
					event[i].callback.apply(event[i].obj, args);
				}
			}
		}
	};
	return Constr;
}());