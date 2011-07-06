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
            autoFocus: false,
            keyboard: false,
            onActivate: function (dtnode) {
                var key = dtnode.data.key;
                $.log("activate %s: is writable: %s", key, dtnode.data.writable);
                $this.selected = key;
                $this.$triggerEvent("activate", [key, dtnode.data.writable]);
            },
            onPostInit: function(isReloading, isError) {
            	var dbNode = null;
            	if ($this.selected) {
            		dbNode = this.getNodeByKey($this.selected);
            	}
            	if (dbNode == null) {
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
		
		deleteCollection: function () {
			var $this = this;
			eXide.util.Dialog.input("Confirm Deletion", "Are you sure you want to delete collection " + $this.selected + "?",
					function () {
						$.getJSON("collections.xql", { 
							remove: $this.selected 
						},
						function (data) {
							$.log(data.status);
							if (data.status == "fail") {
								eXide.util.Dialog.warning("Delete Collection Error", data.message);
							} else {
								$this.reload();
							}
						}
					);
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

eXide.namespace("eXide.browse.ResourceBrowser");

/**
 * Manages a table view of resources within a collection
 */
eXide.browse.ResourceBrowser = (function () {
	
	var nameFormatter = function(row, cell, value, columnDef, dataContext) {
		if (dataContext.isCollection)
			return '<span class="collection"><img src="images/folder_add.png"/> ' + value + '</span>';
		else
			return value;
	};
	
	var columns = [
	               {id: "name", name:"Name", field: "name", width: 120, formatter: nameFormatter},
	               {id: "permissions", name: "Permissions", field: "permissions", width: 80},
	               {id: "owner", name: "Owner", field: "owner", width: 70},
	               {id: "group", name: "Group", field: "group", width: 70},
	               {id: "lastMod", name: "Last Modified", field: "last-modified", width: 115}
	              ];
	
	var gridOptionsOpen = {
			editable: false,
			multiSelect: false
	};
	var gridOptionsManage = {
			editable: false,
			multiSelect: true
	};
	
	Constr = function(container) {
		var $this = this;
		this.container = $(container);
		this.events = {
			"activate": [],
			"activateCollection": [],
			"activateParent": []
		};
		this.collection = null;
		this.data = [];
		this.grid = new Slick.Grid(this.container, this.data, columns, gridOptionsManage);
		var selectionModel = new Slick.RowSelectionModel({selectActiveRow: true});
		this.grid.setSelectionModel(selectionModel);
		selectionModel.onSelectedRangesChanged.subscribe(function(e, args) {
			var rows = selectionModel.getSelectedRows();
			var enableWrite = true;
			for (var i = 0; i < rows.length; i++) {
				if (!$this.data[rows[i]].writable) {
					enableWrite = false;
					break;
				}
			}
			var doc = rows.length == 1 && !$this.data[rows[0]].isCollection ? $this.data[rows[0]] : null;
			$this.$triggerEvent("activate", [ doc, enableWrite]);
		});
		this.grid.onDblClick.subscribe(function (e, args) {
			var cell = $this.grid.getCellFromEvent(e);
			if ($this.data[cell.row].isCollection) {
				// navigate to new collection
				var childColl = $this.collection + "/" + $this.data[cell.row].name;
				$this.$triggerEvent("activateCollection", [ childColl ]);
			}
		});
		this.grid.onKeyDown.subscribe(function (e) {
			if (!e.shiftKey && !e.altKey && !e.ctrlKey) {
				if (e.which == 13) {
					e.stopPropagation();
		            e.preventDefault();
		            var rows = selectionModel.getSelectedRows();
					if (rows.length == 1 && $this.data[rows[0]].isCollection) {
						// navigate to new collection
						var childColl = $this.collection + "/" + $this.data[rows[0]].name;
						$this.$triggerEvent("activateCollection", [ childColl ]);
					}
				} else if (e.which == 8) {
					var p = $this.collection.lastIndexOf("/");
					if (p > 0) {
						e.stopPropagation();
			            e.preventDefault();
			            if ($this.collection != "/db") {
			            	var parent = $this.collection.substring(0, p);
						
							// navigate to parent collection
							$this.$triggerEvent("activateCollection", [ parent ]);
						}
					}
				}
			}
		});
		this.grid.onViewportChanged.subscribe(function(e,args) {
            var vp = $this.grid.getViewport();
            $this.load(vp.top, vp.bottom);
        });
	}
	
	Constr.prototype = {
		
		setMode: function(value) {
			if (value == "manage") {
				this.grid.setOptions(gridOptionsManage);
			} else {
				this.grid.setOptions(gridOptionsOpen);
			}
		},
		
		resize: function () {
			this.grid.resizeCanvas();
			this.container.find(".grid-canvas").focus();
		},
		
		update: function(collection) {
			$.log("Opening collection %s", collection);
			this.collection = collection;
			this.grid.invalidate();
			this.data.length = 0;
			this.grid.getSelectionModel().setSelectedRanges([]);
			this.grid.onViewportChanged.notify();
		},
		
		load: function (start, end) {
			var $this = this;
			var params = { root: this.collection, view: "r", start: start, end: end };
			$.getJSON("collections.xql", params, function (data) {
				for (var i = start; i <= end; i++) {
					$this.grid.invalidateRow(i);
				}
				if (data && data.items) {
					$this.data.length = data.total;
					for (var i = 0; i < data.items.length; i++) {
						$this.data[start + i] = data.items[i];
					}
				} else {
					$this.data.length = 0;
				}
				$this.grid.updateRowCount();
				$this.grid.render();
				if (start == 0) {
					$this.grid.setActiveCell(0, 0);
					$this.grid.setSelectedRows([0]);
					$this.container.find(".grid-canvas").focus();
				}
			});
		},
		
		hasSelection: function () {
			var rows = this.grid.getSelectionModel().getSelectedRows();
			return rows && rows.length > 0;
		},
		
		deleteResource: function() {
			var selected = this.grid.getSelectionModel().getSelectedRows();
			if (selected.length == 0) {
				return;
			}
			var resources = [];
			for (var i = 0; i < selected.length; i++) {
				resources.push(this.data[selected[i]].name);
			}
			var $this = this;
			eXide.util.Dialog.input("Confirm Deletion", "Are you sure you want to delete the selected resources?",
					function () {
						$.log("Deleting resources %o", resources);
						$.getJSON("collections.xql", { 
							remove: resources,
							root: $this.collection
						},
						function (data) {
							$.log(data.status);
							if (data.status == "fail") {
								eXide.util.Dialog.warning("Delete Resource Error", data.message);
							} else {
								$this.reload();
							}
						}
					);
			});
		},
		
		reload: function() {
			this.update(this.collection);
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
 * File upload widget
 */
eXide.browse.Upload = (function () {
	
	Constr = function (container) {
		this.container = container;
		
		this.events = {
			"done": []
		};
		
		$("#file_upload").fileUploadUI({
			sequentialUploads: true,
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
		button.tabindex = 1;
		var img = document.createElement("img");
		img.src = "images/arrow_refresh.png";
		button.appendChild(img);
		$(button).click(function (ev) {
			$this.collections.reload();
		});
		toolbar.append(button);
		
		this.btnDeleteCollection = document.createElement("button");
		this.btnDeleteCollection.title = "Delete Collection";
		this.btnDeleteCollection.id = "eXide-browse-toolbar-delete-collection";
		this.btnDeleteCollection.tabindex = 2;
		img = document.createElement("img");
		img.src = "images/folder_delete.png";
		this.btnDeleteCollection.appendChild(img);
		$(this.btnDeleteCollection).click(function (ev) {
			ev.preventDefault();
			$this.collections.deleteCollection();
		});
		toolbar.append(this.btnDeleteCollection);
		
		this.btnCreateCollection = document.createElement("button");
		this.btnCreateCollection.title = "Create Collection";
		this.btnCreateCollection.id = "eXide-browse-toolbar-create";
		this.btnCreateCollection.tabindex = 3;
		img = document.createElement("img");
		img.src = "images/folder_add.png";
		this.btnCreateCollection.appendChild(img);
		$(this.btnCreateCollection).click(function (ev) {
			ev.preventDefault();
			$this.collections.createCollection();
		});
		toolbar.append(this.btnCreateCollection);
		
		this.btnUpload = document.createElement("button");
		this.btnUpload.title = "Upload Files";
		this.btnUpload.id = "eXide-browse-toolbar-upload";
		this.btnUpload.tabindex = 4;
		img = document.createElement("img");
		img.src = "images/database_add.png";
		this.btnUpload.appendChild(img);
		$(this.btnUpload).click(function (ev) {
			ev.preventDefault();
			$(".eXide-browse-resources", container).hide();
			$(".eXide-browse-upload", container).show();
		});
		toolbar.append(this.btnUpload);
		
		this.btnDeleteResource = document.createElement("button");
		this.btnDeleteResource.title = "Delete Resource";
		this.btnDeleteResource.id = "eXide-browse-toolbar-delete-resource";
		img = document.createElement("img");
		img.src = "images/page_delete.png";
		this.btnDeleteResource.appendChild(img);
		$(this.btnDeleteResource).click(function (ev) {
			ev.preventDefault();
			$this.resources.deleteResource();
		});
		toolbar.append(this.btnDeleteResource);
		
		button = document.createElement("button");
		button.title = "Open Selected";
		button.id = "eXide-browse-toolbar-open";
		button.tabindex = 5;
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
		
		this.collections.addEventListener("activate", this, this.onActivateCollection);
		this.collections.addEventListener("activate", this.resources, this.resources.update);
		this.collections.addEventListener("activate", this.upload, this.upload.update);
		this.resources.addEventListener("activate", this, this.onActivateResource);
		this.resources.addEventListener("activateCollection", this, this.onChangeCollection);
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
					center__contentSelector: ".eXide-browse-content",
					onresize: function () {
						this.resources.resize();
					}
				});
				this.resources.resize();
			}
		},
		
		reload: function(buttons, open) {
			if (buttons) {
				$(".eXide-browse-toolbar button", this.container).hide();
				for (var i = 0; i < buttons.length; i++) {
					$("#eXide-browse-toolbar-" + buttons[i]).show();
				}
			}
			if (open) {
				$(".eXide-browse-form", this.container).show();
				this.resources.setMode("open");
			} else {
				$(".eXide-browse-form", this.container).hide();
				this.resources.setMode("manage");
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
		
		onActivateResource: function (doc, writable) {
			if (doc) {
				$(this.selection).val(doc.name);
			} else {
				$(this.selection).val("");
			}
			if (writable) {
				$(this.btnDeleteCollection).css("display", "");
			} else {
				$(this.btnDeleteCollection).css("display", "none");
			}
		},
		
		onActivateCollection: function (key, writable) {
			if (writable) {
				$(this.btnCreateCollection).css("display", "");
				$(this.btnUpload).css("display", "");
				$(this.btnDeleteCollection).css("display", "");
				$(this.btnDeleteResource).css("display", "");
			} else {
				$(this.btnCreateCollection).css("display", "none");
				$(this.btnUpload).css("display", "none");
				$(this.btnDeleteCollection).css("display", "none");
				$(this.btnDeleteResource).css("display", "none");
			}
				
			this.resources.update(key, writable);
			this.upload.update(key, writable);
		},
		
		onChangeCollection: function (path) {
			this.collections.selected = path;
			this.collections.reload();
			this.resources.update(this.collections.getSelection());
		}
	};
	
	return Constr;
}());
