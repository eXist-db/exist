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
eXide.namespace("eXide.keyboard");

/**
 * Default keybinding.
 * 
 * TODO: select different keybinding for Mac
 */
eXide.keyboard = (function () {
	
	var HashHandler = require("ace/keyboard/hash_handler").HashHandler;
	
	var keybindingWindows = {
			"selectall": "Ctrl-A",
		    "removeline": "Ctrl-D",
		    "gotoline": "Ctrl-L",
		    "togglecomment": "Ctrl-7",
		    "findnext": "Ctrl-K",
		    "findprevious": "Ctrl-Shift-K",
		    "find": "Ctrl-F",
		    "replace": "Ctrl-R",
		    "undo": "Ctrl-Z",
		    "redo": "Ctrl-Shift-Z|Ctrl-Y",
		    "overwrite": "Insert",
		    "copylinesup": "Ctrl-Alt-Up",
		    "movelinesup": "Alt-Up",
		    "selecttostart": "Alt-Shift-Up",
		    "gotostart": "Ctrl-Home|Ctrl-Up",
		    "selectup": "Shift-Up",
		    "golineup": "Up",
		    "copylinesdown": "Ctrl-Alt-Down",
		    "movelinesdown": "Alt-Down",
		    "selecttoend": "Alt-Shift-Down",
		    "gotoend": "Ctrl-End|Ctrl-Down",
		    "selectdown": "Shift-Down",
		    "golinedown": "Down",
		    "selectwordleft": "Ctrl-Shift-Left",
		    "gotowordleft": "Ctrl-Left",
		    "selecttolinestart": "Alt-Shift-Left",
		    "gotolinestart": "Alt-Left|Home",
		    "selectleft": "Shift-Left",
		    "gotoleft": "Left",
		    "selectwordright": "Ctrl-Shift-Right",
		    "gotowordright": "Ctrl-Right",
		    "selecttolineend": "Alt-Shift-Right",
		    "gotolineend": "Alt-Right|End",
		    "selectright": "Shift-Right",
		    "gotoright": "Right",
		    "selectpagedown": "Shift-PageDown",
		    "gotopagedown": "PageDown",
		    "selectpageup": "Shift-PageUp",
		    "gotopageup": "PageUp",
		    "selectlinestart": "Shift-Home",
		    "selectlineend": "Shift-End",
		    "del": "Delete",
		    "backspace": "Backspace",
		    "outdent": "Shift-Tab",
		    "indentOrParam": "Tab",
		    "saveDocument": "Ctrl-Shift-S",
		    "openDocument": "Ctrl-Shift-O",
		    "newDocument": "Ctrl-Shift-N",
		    "closeDocument": "Ctrl-Shift-W",
		    "runQuery": "Ctrl-Return",
		    "autocomplete": "Ctrl-Space",
		    "escape": "Esc",
		    "functionDoc": "Ctrl-Shift-H|F1",
		    "nextTab": "Ctrl-Shift-PageDown",
		    "previousTab": "Ctrl-Shift-PageUp",
		    "gotoDefinition" : "Ctrl-Shift-B|F3"
	};
	
	return {
		
		getKeybinding: function() {
			return new HashHandler(keybindingWindows);
		},
		
		help: function (container) {
			$(container).find("table").each(function () {
				this.innerHTML = "";
				for (var command in keybindingWindows) {
					if (command != 'reverse') {
						var tr = document.createElement("tr");
						var td = document.createElement("td");
						td.appendChild(document.createTextNode(command));
						tr.appendChild(td);
						td = document.createElement("td");
						td.appendChild(document.createTextNode(keybindingWindows[command]));
						tr.appendChild(td);
						this.appendChild(tr);
					}
				}
			});
		}
	};
}());