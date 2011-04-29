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
		
		var header = document.createElement("div");
		header.className = "eXide-browse-toolbar";
		container.appendChild(header);
		
		var button = document.createElement("button");
		button.title = "Reload";
		var img = document.createElement("img");
		img.src = "images/arrow_refresh.png";
		button.appendChild(img);
		$(button).click(function (ev) {
			ev.preventDefault();
			$this.reload();
		});
		header.appendChild(button);
		
		var treeDiv = document.createElement("div");
		treeDiv.className = "eXide-browse-collections eXide-browse-content";
		container.appendChild(treeDiv);
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
		this.container = container;
		this.events = {
			"activate": []
		};
		
		var table = document.createElement("table");
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
		container.appendChild(table);
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

eXide.namespace("eXide.browse.Browser");

/**
 * Main interface for the open and save dialogs. Uses
 * a ResourceBrowser and CollectionBrowser within a jquery.layout
 * panel.
 */
eXide.browse.Browser = (function () {
	
	Constr = function (container) {
		var layoutDiv = document.createElement("div");
		layoutDiv.className = "eXide-browse-panel";
		container.appendChild(layoutDiv);
		
		var cdiv = document.createElement("div");
		cdiv.className = "eXide-browse-collections ui-layout-west";
		layoutDiv.appendChild(cdiv);
		
		var rdiv = document.createElement("div");
		rdiv.className = "eXide-browse-resources ui-layout-center";
		layoutDiv.appendChild(rdiv);
		
		var fdiv = document.createElement("div");
		fdiv.className = "eXide-browse-form";
		fdiv.style.paddingTop = "10px";
		var label = document.createElement("label");
		label.appendChild(document.createTextNode("Selection: "));
		fdiv.appendChild(label);
		var input = document.createElement("input");
		input.name = "resource";
		input.type = "text";
		fdiv.appendChild(input);
		container.appendChild(fdiv);
		
		this.selection = input;
		this.container = container;
		this.resources = new eXide.browse.ResourceBrowser(rdiv);
		this.collections = new eXide.browse.CollectionBrowser(cdiv);
		this.initialized = false;
		
		this.collections.addEventListener("activate", this.resources, this.resources.update);
		this.resources.addEventListener("activate", this, this.$resourceSelected);
	}
	
	Constr.prototype = {
		
		/**
		 * jquery.layout needs to be initialized when the containing div
		 * becomes visible. This does not happen until the dialog is shown
		 * the first time.
		 */
		init: function() {
			var h = $(this.container).innerHeight() - 
			$(".eXide-browse-form", this.container).height() - 25;
			$.log("height: %i", h);
			$(".eXide-browse-panel", this.container).height(h);
			if (!this.initialized) {
				$(".eXide-browse-panel", this.container).layout({
					enableCursorHotkey: false,
					south__resizable: false,
					west__size: 200,
					west__initClosed: false,
					west__contentSelector: ".eXide-browse-content",
					center__minSize: 300
				});
				this.initialized = true;
			}
		},
		
		reload: function() {
			this.collections.reload();
			this.resources.update(this.collections.getSelection());
			$(this.selection).val("");
		},
		
		getSelection: function () {
			var name = $(this.selection).val();
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