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
		this.helper = null;
		this.history = [];
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
		
		getSession: function() {
			return this.$session;
		},
		
		isSaved: function() {
			return this.saved;
		},
		
		isEditable: function() {
			return this.editable;
		},
		
		isXQuery: function() {
			return this.mime == "application/xquery";
		},
		
		setModeHelper: function(mode) {
			this.helper = mode;
		},
		
		getModeHelper: function() {
			return this.helper;
		},
		
		addToHistory: function(line) {
			this.history.push(line);
		},
		
		getLastLine: function() {
			return this.history.pop(line);
		},
		
		getCurrentLine: function() {
			var sel = this.$session.getSelection();
			var lead = sel.getSelectionLead();
			return lead.row;
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
    
    function parseErrMsg(error) {
		var msg;
		if (error.line) {
			msg = error["#text"];
		} else {
			msg = error;
		}
		var str = /.*line:?\s(\d+)/i.exec(msg);
		var line = -1;
		if (str) {
			line = parseInt(str[1]) - 1;
		} else if (error.line) {
			line = parseInt(error.line) - 1;
		}
		return { line: line, msg: msg };
	}
    
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
		this.editor.setBehavioursEnabled(true);
		
	    this.outline = new eXide.edit.Outline(this);
	    
	    // Set up the status bar
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
		
		// register mode helpers
		$this.modes = {
			"xquery": new eXide.edit.XQueryModeHelper($this),
			"xml": new eXide.edit.XMLModeHelper($this)
		};
	};
	
	Constr.prototype = {

		init: function() {
		    if (this.documents.length == 0)
		    	this.newDocument();
		},
		
		exec: function () {
			if (this.activeDoc.getModeHelper()) {
				var args = Array.prototype.slice.call(arguments, 1);
				this.activeDoc.getModeHelper().exec(arguments[0], this.activeDoc, args);
			}
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
			this.$initDocument(newDocument);
		},
		
		newDocumentWithText: function(data, mime, resource) {
			var doc = new eXide.edit.Document(resource.name, resource.path, new EditSession(data));
			doc.editable = resource.writable;
			doc.mime = mime;
			doc.syntax = eXide.util.mimeTypes.getLangFromMime(mime);
			doc.saved = false;
			if (resource.line) {
				doc.addToHistory(resource.line);
				this.editor.gotoLine(resource.line);
			}
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
			if (resource.line) {
				doc.addToHistory(resource.line);
				var sel = doc.$session.getSelection();
				sel.clearSelection();
				sel.moveCursorTo(resource.line, 1);
				doc.$session.setScrollTopRow(resource.line);
			}
			$.log("opening %s, mime: %s, syntax: %s, line: %i", resource.name, doc.mime, doc.syntax, resource.line);
			this.$initDocument(doc);
		},
		
		$initDocument: function (doc) {
			var $this = this;
			$this.$setMode(doc);
			doc.$session.setUndoManager(new UndoManager());
			doc.$session.addEventListener("change", function (ev) {
				if (doc.saved) {
					doc.saved = false;
					$this.updateTabStatus(doc.path, doc);
				}
				$this.triggerCheck();
//				$this.onInput(doc, ev.data);
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
			switch (doc.getSyntax()) {
			case "xquery":
				var XQueryMode = require("eXide/mode/xquery").Mode;
				doc.$session.setMode(new XQueryMode(this));
				if (setMime)
					doc.mime = "application/xquery";
				break;
			case "xml":
				var XMLMode = require("eXide/mode/xml").Mode;
				doc.$session.setMode(new XMLMode(this));
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
			doc.setModeHelper(this.modes[doc.getSyntax()]);
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

		/**
		 * Dispatch document change events to mode helpers.
		 */
		onInput: function (doc, delta) {
			var mode = doc.getModeHelper();
			if (mode && mode.onInput) {
				mode.onInput(doc, delta);
			}
		},
		
		autocomplete: function() {
			var mode = this.activeDoc.getModeHelper();
			if (mode && mode.autocomplete) {
				mode.autocomplete(this.activeDoc);
			}
		},
		
		getHeight: function () {
			return $(this.container).height();
		},
		
		resize: function () {
			this.editor.resize();
		},
		
		clearErrors: function () {
			this.editor.getSession().clearAnnotations();
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
			this.updateStatus("");
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
		
		/**
		 * Update the status bar.
		 */
		updateStatus: function(msg, href) {
			this.status.innerHTML = msg;
			if (href) {
				this.status.href = href;
			}
		},
		
		/**
		 * Trigger validation.
		 */
		triggerCheck: function() {
			var mode = this.activeDoc.getModeHelper();
			if (mode) { 
				var $this = this;
				if ($this.pendingCheck) {
					return;
				}
				var time = new Date().getTime();
				if ($this.validateTimeout && time - $this.lastChangeEvent < 2000) {
					clearTimeout($this.validateTimeout);
				}
				$this.lastChangeEvent = time;
				$this.validateTimeout = setTimeout(function() { 
						$this.validate.apply($this); 
					}, 2000);
			}
		},

		/**
		 * Validate the current document's text by calling validate on the
		 * mode helper.
		 */
		validate: function() {
			var $this = this;
			var mode = $this.activeDoc.getModeHelper();
			if (!(mode && mode.validate)) {
				return;
			}
			$this.pendingCheck = true;
			$.log("Running validation...");
			mode.validate($this.activeDoc, $this.getText(), function (success) {
				$this.pendingCheck = false;
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
		
		focus: function() {
			this.editor.focus();
		},
		
		saveState: function() {
			var i = 0;
			$.each(this.documents, function (index, doc) {
				if (doc.path.match('^__new__.*')) {
					localStorage["eXide." + i + ".path"] = doc.path;
					localStorage["eXide." + i + ".name"] = doc.name;
					localStorage["eXide." + i + ".mime"] = doc.mime;
					localStorage["eXide." + i + ".data"] = doc.getText();
					localStorage["eXide." + i + ".last-line"] = doc.getCurrentLine();
				} else {
					localStorage["eXide." + i + ".path"] = doc.path;
					localStorage["eXide." + i + ".name"] = doc.name;
					localStorage["eXide." + i + ".mime"] = doc.mime;
					localStorage["eXide." + i + ".writable"] = (doc.editable ? "true" : "false");
					localStorage["eXide." + i + ".last-line"] = doc.getCurrentLine();
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