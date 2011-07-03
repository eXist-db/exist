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
eXide.namespace("eXide.edit.commands");

/**
 * Register editor commands to be called from keybindings.
 */
eXide.edit.commands = (function () {

	var canon = require("pilot/canon");
	var useragent = require("pilot/useragent");
	
	function bindKey(win, mac) {
	    return {
	        win: win,
	        mac: mac,
	        sender: "editor"
	    };
	}
	
	return {
		
		init: function (editor) {
		    canon.addCommand({
		    	name: "saveDocument",
		    	bindKey: bindKey("Ctrl-Shift-S", "Command-Shift-S"),
		    	exec: function (env, args, request) {
		    		eXide.app.saveDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "runQuery",
		    	bindKey: bindKey("Ctrl-Return", "Command-Return"),
		    	exec: function (env, args, request) {
		    		eXide.app.runQuery();
		    	}
		    });
		    canon.addCommand({
		    	name: "openDocument",
		    	bindKey: bindKey("Ctrl-Shift-O", "Command-Shift-O"),
		    	exec: function (env, args, request) {
		    		eXide.app.openDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "newDocument",
		    	bindKey: bindKey("Ctrl-Shift-N", "Command-Shift-N"),
		    	exec: function (env, args, request) {
		    		eXide.app.newDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "closeDocument",
		    	bindKey: bindKey("Ctrl-Shift-W", "Command-Shift-W"),
		    	exec: function (env, args, request) {
		    		eXide.app.closeDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "autocomplete",
		    	bindKey: bindKey("Ctrl-Space", "Ctrl-Space"),
		    	exec: function(env, args, request) {
		    		editor.autocomplete();
		    	}
		    });
		    canon.addCommand({
		    	name: "nextTab",
		    	bindKey: bindKey("Ctrl-Shift-PageDown", "Command-Shift-PageDown"),
		    	exec: function(env, args, request) {
		    		editor.nextTab();
		    	}
		    });
		    canon.addCommand({
		    	name: "previousTab",
		    	bindKey: bindKey("Ctrl-Shift-PageUp", "Command-Shift-PageUp"),
		    	exec: function(env, args, request) {
		    		editor.previousTab();
		    	}
		    });
		    canon.addCommand({
		    	name: "functionDoc",
		    	bindKey: bindKey("F1", "F1"),
		    	exec: function(env, args, request) {
		    		editor.exec("showFunctionDoc");
		    	}
		    });
		    canon.addCommand({
		    	name: "gotoDefinition",
		    	bindKey: bindKey("F3", "F3"),
		    	exec: function(env, args, request) {
		    		editor.exec("gotoDefinition");
		    	}
		    });
		    canon.addCommand({
		    	name: "indentOrParam",
		    	bindKey: bindKey("Tab", "Tab"),
		    	exec: function(env, args, request) {
		    		// if there's active template code in the document, tab will
		    		// cycle through the template's params. Otherwise, it calls indent.
		    		var doc = editor.getActiveDocument();
		    		if (!(doc.template && doc.template.nextParam())) {
		    			env.editor.indent();
		    		}
		    	}
		    });
		    canon.addCommand({
		    	name: "escape",
		    	bindKey: bindKey("Esc", "Esc"),
		    	exec: function(env, args, request) {
		    		var doc = editor.getActiveDocument();
		    		doc.template = null;
		    		env.editor.clearSelection();
		    	}
		    });
		    canon.addCommand({
		    	name: "dbManager",
		    	bindKey: bindKey("Ctrl-Shift-M", "Command-Shift-M"),
		    	exec: function (env, args, request) {
		    		eXide.app.manage();
		    	}
		    })
		},
		
		help: function (container) {
			$(container).find("table").each(function () {
				this.innerHTML = "";
				var names = canon.getCommandNames();
				for (var i = 0; i < names.length; i++) {
					var cmd = canon.getCommand(names[i]);
					var tr = document.createElement("tr");
					var td = document.createElement("td");
					td.appendChild(document.createTextNode(names[i]));
					tr.appendChild(td);
					td = document.createElement("td");
					if (useragent.isMac)
						td.appendChild(document.createTextNode(cmd.bindKey.mac));
					else
						td.appendChild(document.createTextNode(cmd.bindKey.win));
					tr.appendChild(td);
					this.appendChild(tr);
				}
			});
		}
	};
}());