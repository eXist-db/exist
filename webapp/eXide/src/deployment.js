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
eXide.namespace("eXide.edit.PackageEditor");

/**
 * Edit deployment descriptors.
 */
eXide.edit.PackageEditor = (function () {
	
	Constr = function () {
		var $this = this;
		this.directory = null;
		this.container = $("#sandbox_dialog_deploy");
		if (this.container.length == 0) {
			var div = document.createElement("div");
			div.id = "sandbox_dialog_deploy";
			document.body.appendChild(div);
			this.container = $(div);
		}
		this.container.dialog({
			title: "Deployment Editor",
			modal: true,
			autoOpen: false,
			width: 480,
			height: 600
		});
		
		this.syncDialog = $("#synchronize-dialog");
		this.syncDialog.dialog({
			title: "Synchronize to Directory",
			modal: true,
			autoOpen: false,
			width: 500,
			height: 400,
			buttons: {
				"Synchronize": function () {
					var dir = $this.syncDialog.find("input[name=dir]").val();
					if (!dir || dir.length == 0) {
						$("#synchronize-report").text("No output directory specified!");
						return;
					}
					$("#synchronize-report").text("Synchronization in progress ...");
					$("#synchronize-report").load("synchronize.xql", {
						collection: $this.collection,
						start: $this.syncDialog.find("input[name=date]").val(),
						dir: dir
					});
					this.directory = dir;
				},
				"Close": function () { $(this).dialog("close"); }
			}
		});
		if (this.directory) {
			this.syncDialog.find("input[name=dir]").val(this.directory);
		}
	};
	
	Constr.prototype = {
			
			/**
			 * Open the deployment editor wizard
			 */
			open: function(collection) {
				var $this = this;
				var params = null;
				if (collection)
					params = { "collection": collection };
				$.ajax({
					url: "deployment.xql",
					type: "POST",
					data: params,
					success: function (data) {
						$this.container.html(data);
						$this.container.form({
							done: function () {
								var params = $this.container.find("form").serialize();
								$.ajax({
									url: "deployment.xql",
									type: "POST",
									data: params,
									success: function () {
										$this.container.dialog("close");
									},
									error: function (xhr, status) {
										eXide.util.error(xhr.responseText);
									}
								});
							},
							cancel: function () {
								$this.container.dialog("close");
							}
						});
						$this.container.find(".author-repeat").repeat("#author-add-trigger", { 
							deleteTrigger: "#author-remove-trigger"
						});
						$this.container.dialog("open");
					},
					error: function (xhr, status) {
						eXide.util.error(xhr.responseText);
					}
				});
			},
			
			/**
			 * Deploy the current application package.
			 */
			deploy: function(collection) {
				var $this = this;
				$.ajax({
					url: "deployment.xql",
					type: "POST",
					dataType: "json",
					data: { "collection": collection, "deploy": "true" },
					success: function (data) {
						var url = location.protocol + "//" + location.hostname + ":" + location.port + "/exist/apps/" + data + "/";
						eXide.util.Dialog.message("Application Deployed", "<p>The application has been deployed. On a standard " +
								"installation the following link should open it:</p>" +
								"<center><a href=\"" + url + "\" target=\"_new\">" + url + "</a></center>");
					},
					error: function (xhr, status) {
						eXide.util.Dialog.warning("Deployment Error", "<p>An error has been reported by the database:</p>" +
								"<p>" + xhr.responseText + "</p>");
					}
				});
			},
			
			/**
			 * Synchronize current application package to file system directory.
			 */
			synchronize: function (collection) {
				var $this = this;
				$.getJSON("deployment.xql", { info: collection }, function (data) {
					if (!data.isAdmin) {
						eXide.util.error("You need to be logged in as an admin user with dba role " +
								"to use this feature.");
						return;
					}
					$this.collection = data.root;
					$this.syncDialog.find("input[name=date]").val(data.deployed);
					$this.syncDialog.dialog("open");
				});
			},
			
			runApp: function (collection) {
				var $this = this;
				$.getJSON("deployment.xql", { info: collection }, function (data) {
					if (!data.isAdmin) {
						eXide.util.error("You need to be logged in as an admin user with dba role " +
								"to use this feature.");
						return;
					}
					var link = "/exist/apps/" + data.root.replace(/^\/db\//, "") + "/";
					eXide.util.Dialog.message("Run Application", "<p>Click on the following link to open your application:</p>" +
							"<center><a href=\"" + link + "\" target=\"_new\">" + link + "</a></center>");
				});
			},
			
			saveState: function () {
				localStorage["eXide.synchronize.dir"] = this.directory;
			},
			
			restoreState: function() {
				this.directory = localStorage["eXide.synchronize.dir"];
			}
	};
	
	return Constr;
}());