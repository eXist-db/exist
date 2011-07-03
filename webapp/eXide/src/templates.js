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
eXide.namespace("eXide.edit.Template");

/**
 * Represents the last inserted template. Cycles through parameters.
 */
eXide.edit.Template = (function () {
	
	var LINE_REGEX = /\n/;
	
	Constr = function(editor, range, code, type) {
		var lines = code.split(LINE_REGEX).length;
		this.code = code;
		this.editor = editor.editor;
		this.range = range;
		this.type = type;
		this.startLine = range.start.row;
		this.endLine = this.startLine + lines - 1;
		$.log("startLine = %i, endLine = %i", this.startLine, this.endLine);
		this.currentLine = this.startLine;
		this.startColumn = range.start.column;
		this.lineOffset = this.startColumn;
		this.regex = /\$[\w\-:_]+/g;
		if (this.startColumn > 0)
			this.regex.lastIndex = this.startColumn;
	}
	
	Constr.prototype = {
		
		/**
		 * Insert the template code into the edited document.
		 */
		insert: function() {
			this.editor.getSession().remove(this.range);
			this.editor.insert(this.code);
			var sel = this.editor.getSelection();
			var lead = sel.getSelectionLead();
			if (this.code.substring(0, 1) != "$" && lead.column > 0)
				this.editor.navigateLeft();
			if (this.type != "variable")
				this.nextParam();
			this.editor.focus();
		},
		
		/**
		 * Cycle through parameters. Returns true if another parameter was found,
		 * false to stop template mode.
		 */
		nextParam: function() {
			var session = this.editor.getSession();
			var sel = this.editor.getSelection();
			var lead = sel.getSelectionLead();
			
			$.log("lead.row = %i startLine = %i", lead.row, this.startLine);
			// return immediately if the cursor is outside the template
			if (lead.row < this.startLine || lead.row > this.endLine)
				return false;
			
			var loop = false;
			var found = false;
			while (this.currentLine <= this.endLine) {
				var line = session.getDisplayLine(this.currentLine);
				$.log("Checking line %s", line);
				var match = this.regex.exec(line);
				if (match) {
					$.log("Matched %s", match[0]);
					sel.setSelectionAnchor(this.currentLine, match.index);
					sel.selectTo(this.currentLine, match.index + match[0].length);
					this.lineOffset = match.index;
					found = true;
					break;
				} else {
					this.lineOffset = 0;
					this.currentLine++;
				}
				if (this.currentLine > this.endLine && !loop) {
					$.log("loop %i", this.startColumn);
					this.currentLine = this.startLine;
					if (this.startColumn > 0) {
						this.lineOffset = this.startColumn;
						this.regex.lastIndex = this.lineOffset;
					}
					loop = true;
				}
			}
			return found;
		}
	};
	return Constr;
}());