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
eXide.namespace("eXide.browse.CollectionBrowser");

/**
 * Manages a tree component for browsing collections
 */
eXide.browse.CollectionBrowser = (function () {
	
	Constr = function (container) {
		var $this = this;
		this.events = {
			"activate": []
		};
		this.selected = null;
		
		var treeDiv = document.createElement("div");
		treeDiv.className = "eXide-browse-collections eXide-browse-content";
		container.append(treeDiv);
		this.container = $(treeDiv);
		
		this.container.dynatree({
            persist: false,
            rootVisible: false,
            initAjax: { url: "collections.xql" },
            clickFolderMode: 1,
            onActivate: function (dtnode) {
                var key = dtnode.data.key;
                $.log("activate %s", key);
                $this.selected = key;
                $this.$triggerEvent("activate", [key]);
            },
            onPostInit: function(isReloading, isError) {
            	var dbNode;
            	if ($this.selected) {
            		$.log("prevKey: %s", $this.selected);
            		dbNode = this.getNodeByKey($this.selected);
            	} else {
            		dbNode = this.getNodeByKey("/db");
            	}
            	dbNode.activate();
            	dbNode.expand(true);
            }
        });
	}
	
	Constr.prototype = {
		getSelection: function () {
			return this.selected;
		},
		
		reload: function () {
			$.log("Reloading tree...");
			var tree = this.container.dynatree("getTree");
			tree.reload();
		},
		
		createCollection: function () {
			var $this = this;
			if (!eXide.app.$checkLogin())
				return;
			eXide.util.Dialog.input("Create Collection", 
					"<label for=\"collection\">Name: </label>" +
					"<input type=\"text\" name=\"collection\" id=\"eXide-browse-collection-name\"/>",
					function () {
						$.getJSON("collections.xql", { 
								create: $("#eXide-browse-collection-name").val(), 
								collection: $this.selected
							},
							function (data) {
								if (data.status == "fail") {
									eXide.util.Dialog.warning("Create Collection Error", data.message);
								} else {
									$this.reload();
								}
							}
						);
					}
			);
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

eXide.namespace("eXide.browse.ResourceBrowser");

/**
 * Manages a table view of resources within a collection
 */
eXide.browse.ResourceBrowser = (function () {
	
	var classes = ['name', 'permissions', 'owner', 'group', 'lastModified'];
	var head = ['Name', 'Permissions', 'Owner', 'Group', 'Last Modified'];
	
	Constr = function(container) {
		var $this = this;
		this.container = $(container);
		this.events = {
			"activate": []
		};
				
		var table = document.createElement("table");
		table.className = ".eXide-browse-content";
		var thead = document.createElement("thead");
		table.appendChild(thead);
		var tr = document.createElement("tr");
		thead.appendChild(tr);
		for (var i = 0; i < head.length; i++) {
			var th = document.createElement("th");
			th.appendChild(document.createTextNode(head[i]));
			tr.appendChild(th);
		}
		var tbody = document.createElement("tbody");
		table.appendChild(tbody);
		container.append(table);
		this.view = tbody;
		this.activeRow = null;
	}
	
	Constr.prototype = {
			
		update: function(collection) {
			$.log("Opening collection %s", collection);
			var $this = this;
			$this.activeRow = null;
			var params = { root: collection, view: "r" };
			$.getJSON("collections.xql", params, function (data) {
				$this.view.innerHTML = "";
	            if (data) {
		            for (var i = 0; i < data.length; i++) {
		            	var style = i % 2 == 0 ? 'even' : 'uneven';
		            	var tr = document.createElement("tr");
		            	tr.className = style;
		            	tr.style.cursor = "pointer";
		            	for (var j = 0; j < head.length; j++) {
		            		var td = document.createElement("td");
		            		td.className = classes[j];
		            		td.appendChild(document.createTextNode(data[i][j]));
		            		tr.appendChild(td);
		            	}
						$this.view.appendChild(tr);
						tr.doc = {
								name: data[i][0],
								path: collection + "/" + data[i][0],
								writable: data[i][5]
		        		};
						$(tr).click(function () {
							if ($this.activeRow) {
								$($this.activeRow).removeClass("eXide-browser-active");
							}
							$this.activeRow = this;
							$(this).addClass("eXide-browser-active");
							$this.$triggerEvent("activate", [ this.doc ]);
						});
		            }
	            }
			});
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

eXide.namespace("eXide.browse.Upload");

/**
 * Manages a table view of resources within a collection
 */
eXide.browse.Upload = (function () {
	
	Constr = function (container) {
		this.container = container;
		
		this.events = {
			"done": []
		};
		
		$("#file_upload").fileUploadUI({
	        uploadTable: $('#files'),
	        buildUploadRow: function (files, index, handler) {
	            return $('<tr><td>' + files[index].name + '<\/td>' +
	                    '<td class="file_upload_progress"><div><\/div><\/td>' +
	                    '<td class="file_upload_cancel">' +
	                    '<button class="ui-state-default ui-corner-all" title="Cancel">' +
	                    '<span class="ui-icon ui-icon-cancel">Cancel<\/span>' +
	                    '<\/button><\/td><\/tr>');
	        },
	        buildDownloadRow: function (info) {
	        	if (info.error) {
	        		return $("<tr><td>" + info.name + "</td><td>" + info.error + "</td></tr>");
	        	}
	        	return null;
	        }
	    });
		var $this = this;
		$("#eXide-browse-upload-done").button().click(function() {
			$('#files').empty();
			$this.$triggerEvent("done", []);
		});
	}
	
	Constr.prototype = {
		update: function(collection) {
			$("input[name=collection]", this.container).val(collection);
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

eXide.namespace("eXide.browse.Browser");

/**
 * Main interface for the open and save dialogs. Uses
 * a ResourceBrowser and CollectionBrowser within a jquery.layout
 * panel.
 */
eXide.browse.Browser = (function () {
	
	Constr = function (container) {
		var $this = this;
		var toolbar = $(".eXide-browse-toolbar", container);
		
		var button = document.createElement("button");
		button.title = "Reload";
		button.id = "eXide-browse-toolbar-reload";
		var img = document.createElement("img");
		img.src = "images/arrow_refresh.png";
		button.appendChild(img);
		$(button).click(function (ev) {
			$this.collections.reload();
		});
		toolbar.append(button);
		
		button = document.createElement("button");
		button.title = "Create Collection";
		button.id = "eXide-browse-toolbar-create";
		img = document.createElement("img");
		img.src = "images/folder_add.png";
		button.appendChild(img);
		$(button).click(function (ev) {
			ev.preventDefault();
			$this.collections.createCollection();
		});
		toolbar.append(button);
		
		button = document.createElement("button");
		button.title = "Upload Files";
		button.id = "eXide-browse-toolbar-upload";
		img = document.createElement("img");
		img.src = "images/database_add.png";
		button.appendChild(img);
		$(button).click(function (ev) {
			ev.preventDefault();
			$(".eXide-browse-resources", container).hide();
			$(".eXide-browse-upload", container).show();
		});
		toolbar.append(button);
		
		button = document.createElement("button");
		button.title = "Open Selected";
		button.id = "eXide-browse-toolbar-open";
		img = document.createElement("img");
		img.src = "images/page_edit.png";
		button.appendChild(img);
		$(button).click(function (ev) {
			ev.preventDefault();
			eXide.app.openSelectedDocument();
		});
		toolbar.append(button);
		
		this.selection = $(".eXide-browse-form input", container);
		this.container = container;
		this.resources = new eXide.browse.ResourceBrowser($(".eXide-browse-resources", container));
		this.collections = new eXide.browse.CollectionBrowser($(".eXide-browse-collections", container));
		this.upload = new eXide.browse.Upload($(".eXide-browse-upload", container).hide());
		this.layout = null;
		
		this.collections.addEventListener("activate", this.resources, this.resources.update);
		this.collections.addEventListener("activate", this.upload, this.upload.update);
		this.resources.addEventListener("activate", this, this.$resourceSelected);
		this.upload.addEventListener("done", this, function () {
			$(".eXide-browse-resources", container).show();
			$(".eXide-browse-upload", container).hide();
			this.reload();
		});
	}
	
	Constr.prototype = {
		
		/**
		 * jquery.layout needs to be initialized when the containing div
		 * becomes visible. This does not happen until the dialog is shown
		 * the first time.
		 */
		init: function() {
			var h = $(this.container).innerHeight()  - 
				$(".eXide-browse-form", this.container).height() - 25;
			$(".eXide-browse-panel", this.container).height(h);
			if (this.layout == null) {
				this.layout = $(".eXide-browse-panel", this.container).layout({
					enableCursorHotkey: false,
					north__resizable: false,
					north__closable: false,
					north__spacing_open: 0, 
					south__resizable: false,
					west__size: 200,
					west__initClosed: false,
					west__contentSelector: ".eXide-browse-content",
					center__minSize: 300,
					center__contentSelector: ".eXide-browse-content"
				});
			}
		},
		
		reload: function(buttons, selectInput) {
			if (buttons) {
				$(".eXide-browse-toolbar button", this.container).hide();
				for (var i = 0; i < buttons.length; i++) {
					$("#eXide-browse-toolbar-" + buttons[i]).show();
				}
			}
			if (selectInput) {
				$(".eXide-browse-form", this.container).show();
			} else {
				$(".eXide-browse-form", this.container).hide();
			}
			if (this.layout != null) {
				this.resize();
				this.collections.reload();
				this.resources.update(this.collections.getSelection());
				$(this.selection).val("");
			}
		},
		
		resize: function() {
			var h = $(this.container).innerHeight() - 
				$(".eXide-browse-form", this.container).height() - 25;
			$(".eXide-browse-panel", this.container).height(h);
			this.layout.resizeAll();
		},
		
		getSelection: function () {
			var name = $(this.selection).val();
			if (name == null || name == '')
				return null;
			return {
				name: name,
				path: this.collections.getSelection() + "/" + name,
				writable: true
			};
		},
		
		$resourceSelected: function (doc) {
			$(this.selection).val(doc.name);
		}
	};
	
	return Constr;
}());