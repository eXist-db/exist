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

// main entry point
$(document).ready(function() {
	eXide.app.init();
	
	if (/^\?open=/.test(window.location.search)) {
		$.log("parameters: %s", window.location.search);
		eXide.app.findDocument(window.location.search.substring(6));
	}
});

eXide.namespace("eXide.app");

/**
 * Static class for the main application. Controls the GUI.
 */
eXide.app = (function() {
	
	var editor;

	var deploymentEditor;
	var dbBrowser;
	
	var hitCount = 0;
	var startOffset = 0;
	var currentOffset = 0;
	var endOffset = 0;
	
	var login = null;
	
	var preferences = {
			theme: "ace/theme/dawn",
			fontSize: "12px",
			showInvisibles: false,
			showPrintMargin: true,
			showHScroll: false
	};
	
	return {

		init: function() {
			editor = new eXide.edit.Editor(document.getElementById("editor"));
			deploymentEditor = new eXide.edit.PackageEditor(document.getElementById("deployment-editor"));
			dbBrowser = new eXide.browse.Browser(document.getElementById("open-dialog"));
			
			eXide.app.initGUI();
			
			eXide.app.restoreState();
		    
		    editor.init();
		    editor.addEventListener("outlineChange", eXide.app.onOutlineChange);
		    eXide.app.resize();

			$(window).resize(eXide.app.resize);
			
			$(window).unload(function () {
				eXide.app.saveState();
			});
		},
		
		resize: function() {
			var panel = $("#editor");
			var header = $(".header");
//			panel.width($(".ui-layout-center").innerWidth() - 20);
//			panel.css("width", "100%");
//			panel.height($(".ui-layout-center").innerHeight() - header.height());
			editor.resize();
		},

		newDocument: function() {
			editor.newDocument();
		},

		findDocument: function(path) {
			var doc = editor.getDocument(path);
			if (doc == null) {
				var resource = {
						name: path.match(/[^\/]+$/),
						path: path
				};
				eXide.app.$doOpenDocument(resource);
			} else {
				editor.switchTo(doc);
			}
		},
		
		locate: function(type, path, symbol) {
			if (path == null) {
				editor.exec("locate", type, symbol);
			} else {
				$.log("Locating %s in document %s", symbol, path);
				var doc = editor.getDocument(path);
				if (doc == null) {
					var resource = {
							name: path.match(/[^\/]+$/),
							path: path
					};
					eXide.app.$doOpenDocument(resource, function() {
						editor.exec("locate", type, symbol);
					});
				} else {
					editor.switchTo(doc);
					editor.exec("locate", type, symbol);
				}
			}
		},
		
		openDocument: function() {
			dbBrowser.reload(["reload"], true);
			$("#open-dialog").dialog("option", "title", "Open Document");
			$("#open-dialog").dialog("option", "buttons", { 
				"cancel": function() { $(this).dialog("close"); editor.focus(); },
				"open": eXide.app.openSelectedDocument
			});
			$("#open-dialog").dialog("open");
		},

		openSelectedDocument: function() {
			var resource = dbBrowser.getSelection();
			if (resource) {
				eXide.app.$doOpenDocument(resource);
			}
			$("#open-dialog").dialog("close");
		},

		$doOpenDocument: function(resource, callback) {
			resource.path = eXide.util.normalizePath(resource.path);
			$.ajax({
				url: "load.xql?path=" + resource.path,
				dataType: 'text',
				success: function (data, status, xhr) {
					var mime = eXide.util.mimeTypes.getMime(xhr.getResponseHeader("Content-Type"));
					editor.openDocument(data, mime, resource);
					if (callback) {
						callback.call(null, resource);
					}
				},
				error: function (xhr, status) {
					eXide.util.error("Failed to load document " + resource.path + ": " + 
							xhr.status + " " + xhr.statusText);
				}
			});
		},

		closeDocument: function() {
			if (!editor.getActiveDocument().isSaved()) {
				$("#dialog-confirm-close").dialog({
					resizable: false,
					height:140,
					modal: true,
					buttons: {
						"Close": function() {
							$( this ).dialog( "close" );
							editor.closeDocument();
						},
						Cancel: function() {
							$( this ).dialog( "close" );
						}
					}
				});
			} else {
				editor.closeDocument();
			}
		},
		
		saveDocument: function() {
			if (editor.getActiveDocument().getPath().match('^__new__')) {
				dbBrowser.reload(["reload", "create"], true);
				$("#open-dialog").dialog("option", "title", "Save Document");
				$("#open-dialog").dialog("option", "buttons", { 
					"Cancel": function() { $(this).dialog("close"); },
					"Save": function() {
						editor.saveDocument(dbBrowser.getSelection(), function () {
							$("#open-dialog").dialog("close");
						}, function (msg) {
							eXide.util.Dialog.warning("Failed to Save Document", msg);
						});
					}
				});
				$("#open-dialog").dialog("open");
			} else {
				editor.saveDocument(null, function () {
					eXide.util.message(editor.getActiveDocument().getName() + " stored.");
				}, function (msg) {
					eXide.util.Dialog.warning("Failed to Save Document", msg);
				});
			}
		},

		download: function() {
			var doc = editor.getActiveDocument();
			if (doc.getPath().match("^__new__") || !doc.isSaved()) {
				eXide.util.error("There are unsaved changes in the document. Please save it first.");
				return;
			}
			window.location.href = "load.xql?download=true&path=" + encodeURIComponent(doc.getPath());
		},
		
		runQuery: function() {
			editor.updateStatus("Running query ...");
			var code = editor.getText();
			var moduleLoadPath = "xmldb:exist://" + editor.getActiveDocument().getBasePath();
			$('#results-container .results').empty();
			$.ajax({
				type: "POST",
				url: "execute",
				dataType: "xml",
				data: { "qu": code, "base": moduleLoadPath },
				success: function (xml) {
					var elem = xml.documentElement;
					if (elem.nodeName == 'error') {
				        var msg = $(elem).text();
				        eXide.util.error(msg, "Compilation Error");
				        editor.evalError(msg);
					} else {
						editor.updateStatus("");
						editor.clearErrors();
						var layout = $("body").layout();
						layout.open("south");
						layout.sizePane("south", 300);
						eXide.app.resize();
						
						startOffset = 1;
						currentOffset = 1;
						hitCount = elem.getAttribute("hits");
						endOffset = startOffset + 10 - 1;
						if (hitCount < endOffset)
							endOffset = hitCount;
						eXide.util.message("Found " + hitCount + " in " + elem.getAttribute("elapsed"));
						eXide.app.retrieveNext();
					}
				},
				error: function (xhr, status) {
					eXide.util.error(xhr.responseText, "Server Error");
				}
			});
		},

		checkQuery: function() {
			editor.validate();
		},

		/** If there are more query results to load, retrieve
		 *  the next result.
		 */
		retrieveNext: function() {
			$.log("retrieveNext: %d", currentOffset);
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
						$("#results-container .pos:last a").click(function () {
							eXide.app.findDocument(this.pathname);
							return false;
						});
						eXide.app.retrieveNext();
					}
				});
			} else {
		    }
		},

		/** Called if user clicks on "forward" link in query results. */
		browseNext: function() {
			if (currentOffset > 0 && endOffset < hitCount) {
				startOffset = currentOffset;
		        var howmany = 10;
		        endOffset = currentOffset + howmany - 1;
				if (hitCount < endOffset)
					endOffset = hitCount;
				$("#results-container .results").empty();
				eXide.app.retrieveNext();
			}
			return false;
		},
		
		/** Called if user clicks on "previous" link in query results. */
		browsePrevious: function() {
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
				eXide.app.retrieveNext();
			}
			return false;
		},
		
		manage: function() {
			if (!eXide.app.$checkLogin())
				return;
			dbBrowser.reload(["reload", "create", "upload", "open"], false);
			$("#open-dialog").dialog("option", "title", "DB Manager");
			$("#open-dialog").dialog("option", "buttons", { 
				"Close": function() { $(this).dialog("close"); }
			});
			$("#open-dialog").dialog("open");
		},
		
		/** Open deployment settings for current app */
		deploymentSettings: function() {
			var path = editor.getActiveDocument().getPath();
			var collection = /^(.*)\/[^\/]+$/.exec(path);
			if (!collection)
				return;
			if (!eXide.app.$checkLogin())
				return;
			$.log("Editing deployment settings for collection: %s", collection[1]);
			deploymentEditor.open(collection[1]);
		},
		
		newDeployment: function() {
			if (!eXide.app.$checkLogin())
				return;
			deploymentEditor.open();
		},
		
		deploy: function() {
			var path = editor.getActiveDocument().getPath();
			var collection = /^(.*)\/[^\/]+$/.exec(path);
			if (!collection) {
				eXide.util.error("The file open in the editor does not belong to an application package!")
				return false;
			}
			eXide.app.$checkLogin();
			$.log("Deploying application from collection: %s", collection[1]);
			deploymentEditor.deploy(collection[1]);
			return false;
		},
		
		synchronize: function() {
			var path = editor.getActiveDocument().getPath();
			var collection = /^(.*)\/[^\/]+$/.exec(path);
			if (!collection)
				return;
			deploymentEditor.synchronize(collection[1]);
		},
		
		openApp: function () {
			var path = editor.getActiveDocument().getPath();
			var collection = /^(.*)\/[^\/]+$/.exec(path);
			if (!collection)
				return;
			deploymentEditor.runApp(collection[1]);
		},
		
		preferences: function () {
			var form = $("#preferences-dialog form");
			$("select[name=theme]", form).val(preferences.theme);
			$("select[name=font-size]", form).val(preferences.fontSize);
			$("input[name=show-invisibles]", form).attr("checked", preferences.showInvisibles);
			$("input[name=print-margin]", form).attr("checked", preferences.showPrintMargin);
			$("#preferences-dialog").dialog("open");
		},
		
		applyPreferences: function () {
			$.log("preferences: %o", preferences);
			editor.setTheme(preferences.theme);
			editor.editor.setShowInvisibles(preferences.showInvisibles);
			editor.editor.renderer.setShowPrintMargin(preferences.showPrintMargin);
//			editor.editor.renderer.setHScrollBarAlwaysVisible(preferences.showHScroll);
			$("#editor").css("font-size", preferences.fontSize);
			editor.resize();
		},
		
		restoreState: function() {
			if (!eXide.util.supportsHtml5Storage)
				return false;
			for (conf in preferences) {
				var key = "eXide.preferences." + conf;
				if (localStorage[key]) {
					if (localStorage[key] == "true")
						preferences[conf] = true;
					else if (localStorage[key] == "false")
						preferences[conf] = false;
					else
						preferences[conf] = localStorage[key];
				}
			}
			eXide.app.applyPreferences();
			
			var docCount = localStorage["eXide.documents"];
			if (!docCount)
				docCount = 0;
			for (var i = 0; i < docCount; i++) {
				var doc = {
						path: localStorage["eXide." + i + ".path"],
						name: localStorage["eXide." + i + ".name"],
						writable: (localStorage["eXide." + i + ".writable"] == "true"),
						line: parseInt(localStorage["eXide." + i + ".last-line"])
				};
				$.log("doc.line = %i", doc.line);
				var data = localStorage["eXide." + i + ".data"];
				if (data) {
					editor.newDocumentWithText(data, localStorage["eXide." + i + ".mime"], doc);
				} else {
					eXide.app.$doOpenDocument(doc);
				}
			}
			deploymentEditor.restoreState();
			return true;
		},
		
		saveState: function() {
			if (!eXide.util.supportsHtml5Storage)
				return;
			localStorage.clear();
			for (conf in preferences) {
				localStorage["eXide.preferences." + conf] = preferences[conf];
			}
			
			editor.saveState();
			deploymentEditor.saveState();
		},
		
		ping: function() {
			$.ajax({
				url: "index.html",
				type: "HEAD",
				success: function () {
					setTimeout(function () { eXide.app.ping(); }, 30000);
				},
				error: function (xhr, textStatus) {
					$.log("ping failed: %s", textStatus);
					eXide.app.login = null;
					$("#user").empty();
					$("#login").text("Login");
				}
			});
		},
		
		$checkLogin: function () {
			if (eXide.app.login)
				return true;
			eXide.util.error("Warning: you are not logged in.");
			return false;
		},
		
		initGUI: function() {
			$("body").layout({
				enableCursorHotkey: false,
				north__size: 78,
				north__resizable: false,
				north__closable: false,
				north__spacing_open: 0,
				south__minSize: 200,
				south__initClosed: true,
				west__size: 200,
				west__initClosed: true,
				west__contentSelector: ".content",
				center__minSize: 300,
				center__onresize: eXide.app.resize,
				center__contentSelector: ".content"
			});
			
			$(".menu ul li").hover(function () {
				$("ul", this).slideDown(200);
			}, function () {
				$("ul", this).slideUp(200);
			});
			
			$("#open-dialog").dialog({
				title: "Open File",
				modal: false,
		        autoOpen: false,
		        height: 480,
		        width: 700,
				open: function() { dbBrowser.init(); },
				resize: function() { dbBrowser.resize(); }
			});
			$("#preferences-dialog").dialog({
				title: "Preferences",
				modal: true,
				autoOpen: false,
				height: 400,
				width: 600,
				buttons: {
					"Cancel": function () { $(this).dialog("close"); editor.focus(); },
					"Save": function () {
						var form = $("form", this);
						preferences.theme = $("select[name=theme]", form).val();
						preferences.fontSize = $("select[name=font-size]", form).val();
						preferences.showInvisibles = $("input[name=show-invisibles]", form).is(":checked");
						preferences.showPrintMargin = $("input[name=print-margin]", form).is(":checked");
						eXide.app.applyPreferences();
						
						$(this).dialog("close");
						editor.focus();
					}
				}
			});
			$("#login-dialog").dialog({
				title: "Login",
				modal: true,
				autoOpen: false,
				buttons: {
					"Login": function() {
						$.ajax({
							url: "login",
							data: $("#login-form").serialize(),
							success: function (data) {
								eXide.app.login = $("#login-form input[name=user]").val();
								$.log("Logged in as %s", eXide.app.login);
								$("#login-dialog").dialog("close");
								$("#user").text("Logged in as " + eXide.app.login + ". ");
								$("#login").text("Logout");
								setTimeout(function () { eXide.app.ping(); }, 30000);
								editor.focus();
							},
							error: function () {
								$("#login-error").text("Login failed. Bad username or password.");
								$("#login-dialog input:first").focus();
							}
						});
					},
					"Cancel": function () { $(this).dialog("close"); editor.focus(); }
				},
				open: function() {
					// clear form fields
					$(this).find("input").val("");
					$(this).find("input:first").focus();
					$("#login-error").empty();
					
					var dialog = $(this);
					dialog.find("input").keyup(function (e) {
						if (e.keyCode == 13) {
				           dialog.parent().find(".ui-dialog-buttonpane button:first").trigger("click");
				        }
					});
				}
			});
			$("#keyboard-help").dialog({
				title: "Keyboard Shortcuts",
				modal: false,
				autoOpen: false,
				height: 400,
				buttons: {
					"Close": function () { $(this).dialog("close"); }
				},
				open: function () {
					eXide.edit.commands.help($("#keyboard-help"));
				}
			});
			$("#help-shortcuts").click(function (ev) {
				ev.preventDefault();
				$("#keyboard-help").dialog("open");
			});
			// initialize buttons and menu events
			var button = $("#open").button({
				icons: {
					primary: "ui-icon-folder-open"
				}
			});
			button.click(eXide.app.openDocument);
			$("#menu-file-open").click(eXide.app.openDocument);
			
			button = $("#close").button({
				icons: {
					primary: "ui-icon-close"
				}
			});
			button.click(eXide.app.closeDocument);
			$("#menu-file-close").click(eXide.app.closeDocument);
			
			button = $("#new").button({
				icons: {
					primary: "ui-icon-document"
				}
			});
			button.click(eXide.app.newDocument);
			$("#menu-file-new").click(eXide.app.newDocument);
			
			button = $("#run").button({
				icons: {
					primary: "ui-icon-play"
				}
			});
			button.click(eXide.app.runQuery);
			button = $("#validate").button({
				icons: {
					primary: "ui-icon-check"
				}
			});
			button.click(eXide.app.checkQuery);
			button = $("#save").button({
				icons: {
					primary: "ui-icon-disk"
				}
			});
			button.click(eXide.app.saveDocument);
			$("#menu-file-save").click(eXide.app.saveDocument);
			
			button = $("#download").button({
				icons: {
					primary: "ui-icon-transferthick-e-w"
				}
			});
			button.click(eXide.app.download);
			$("#menu-file-download").click(eXide.app.download);
			$("#menu-file-manager").click(eXide.app.manage);
			// menu-only events
			$("#menu-deploy-new").click(eXide.app.newDeployment);
			$("#menu-deploy-edit").click(eXide.app.deploymentSettings);
			$("#menu-deploy-deploy").click(eXide.app.deploy);
			$("#menu-deploy-sync").click(eXide.app.synchronize);
			$("#menu-edit-undo").click(function (ev) {
				ev.preventDefault();
				editor.editor.undo();
			});
			$("#menu-edit-redo").click(function (ev) {
				ev.preventDefault();
				editor.editor.redo();
			});
			$("#menu-edit-preferences").click(eXide.app.preferences);
			
			$("#menu-deploy-run").click(eXide.app.openApp);
			
			// syntax drop down
			$("#syntax").change(function () {
				editor.setMode($(this).val());
			});
			// register listener to update syntax drop down
			editor.addEventListener("activate", null, function (doc) {
				$("#syntax").val(doc.getSyntax());
			});
			
			$("#login").click(function (ev) {
				ev.preventDefault();
				if (eXide.app.login) {
					// logout
					$.get("logout");
					$("#user").empty();
					$("#login").text("Login");
					eXide.app.login = null;
				} else {
					$("#login-dialog").dialog("open");
				}
			});
			$('#results-container .next').click(eXide.app.browseNext);
			$('#results-container .previous').click(eXide.app.browsePrevious);
		}
	};
}());