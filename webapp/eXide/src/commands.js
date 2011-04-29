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
	
	return {
		init: function(editor) {
		    canon.addCommand({
		    	name: "saveDocument",
		    	exec: function (env, args, request) {
		    		eXide.app.saveDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "runQuery",
		    	exec: function (env, args, request) {
		    		eXide.app.runQuery();
		    	}
		    });
		    canon.addCommand({
		    	name: "openDocument",
		    	exec: function (env, args, request) {
		    		eXide.app.openDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "newDocument",
		    	exec: function (env, args, request) {
		    		eXide.app.newDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "closeDocument",
		    	exec: function (env, args, request) {
		    		eXide.app.closeDocument();
		    	}
		    });
		    canon.addCommand({
		    	name: "autocomplete",
		    	exec: function(env, args, request) {
		    		editor.autocomplete();
		    	}
		    });
		    canon.addCommand({
		    	name: "nextTab",
		    	exec: function(env, args, request) {
		    		editor.nextTab();
		    	}
		    });
		    canon.addCommand({
		    	name: "previousTab",
		    	exec: function(env, args, request) {
		    		editor.previousTab();
		    	}
		    });
		    canon.addCommand({
		    	name: "functionDoc",
		    	exec: function(env, args, request) {
		    		editor.showFunctionDoc();
		    	}
		    });
		    canon.addCommand({
		    	name: "gotoDefinition",
		    	exec: function(env, args, request) {
		    		editor.gotoDefinition();
		    	}
		    });
		    canon.addCommand({
		    	name: "indentOrParam",
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
		    	exec: function(env, args, request) {
		    		var doc = editor.getActiveDocument();
		    		doc.template = null;
		    		env.editor.clearSelection();
		    	}
		    });
		}
	};
}());