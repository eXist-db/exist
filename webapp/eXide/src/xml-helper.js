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
eXide.namespace("eXide.edit.XMLModeHelper");

/**
 * XML specific helper methods.
 */
eXide.edit.XMLModeHelper = (function () {
	
	Constr = function(editor) {
		this.parent = editor;
		this.editor = this.parent.editor;
		
		this.addCommand("closeTag", this.closeTag);
	}
	
	eXide.util.oop.inherit(Constr, eXide.edit.ModeHelper);
	
	Constr.prototype.closeTag = function (doc, text, row) {
		var basePath = "xmldb:exist://" + doc.getBasePath();
		var $this = this;
		$.ajax({
			type: "POST",
			url: "validate-xml.xql",
			data: { xml: text },
			dataType: "json",
			success: function (data) {
				if (data.status && data.status == "invalid") {
					var line = parseInt(data.message.line) - 1;
					if (line <= row) {
						var tag = /element type \"([^\"]+)\"/.exec(data.message["#text"]);
						if (tag.length > 0) {
							$this.editor.insert(tag[1] + ">");
						}
					}
				}
			},
			error: function (xhr, status) {
			}
		});
	}
	
	Constr.prototype.validate = function(doc, code, onComplete) {
		var $this = this;
		$.ajax({
			type: "POST",
			url: "validate-xml.xql",
			data: { xml: code },
			dataType: "json",
			success: function (data) {
				$this.compileError(data, doc);
				onComplete.call(this, true);
			},
			error: function (xhr, status) {
				onComplete.call(this, true);
				$.log("Compile error: %s - %s", status, xhr.responseText);
			}
		});
	}
	
	Constr.prototype.compileError = function(data, doc) {
		$.log("Validation returned %o", data);
		if (data.status && data.status == "invalid") {
			var annotation = [{
				row: parseInt(data.message.line) - 1,
				text: data.message["#text"],
				type: "error"
			}];
			$.log("annotation: %o", annotation);
			this.parent.updateStatus(data.message["#text"], doc.getPath() + "#" + data.message.line);
			doc.getSession().setAnnotations(annotation);
		} else {
			this.parent.clearErrors();
			this.parent.updateStatus("");
		}
	}
	
	return Constr;
}());