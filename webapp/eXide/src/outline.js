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

eXide.namespace("eXide.edit.Outline");

/**
 * XQuery function outline view. Available functions and variables are
 * kept in the document instance. Templates are loaded once and kept in
 * this class.
 * 
 */
eXide.edit.Outline = (function () {
	
	var TYPE_FUNCTION = "function";
	var TYPE_VARIABLE = "variable";
	var TYPE_TEMPLATE = "template";
	
	Constr = function(editor) {
		editor.addEventListener("activate", this, this.updateOutline);
		editor.addEventListener("validate", this, this.updateOutline);
		editor.addEventListener("close", this, this.clearOutline);
		
		// pre-compile regexp needed by this class
		this.funcRe = /declare\s+function\s+(([^\(]+)\([^\)]*\))/;
		this.funcDefRe = /declare\s+function\s+[^\(]+\([^\)]*\)/g;
		this.varDefRe = /declare\s+variable\s+\$[^\s;]+/gm;
		this.varRe = /declare\s+variable\s+(\$[^\s;]+)/;
		this.parseImportRe = /import\s+module\s+namespace\s+[^=]+\s*=\s*["'][^"']+["']\s*at\s+["'][^"']+["']\s*;/g;
		this.moduleRe = /import\s+module\s+namespace\s+([^=\s]+)\s*=\s*["']([^"']+)["']\s*at\s+["']([^"']+)["']\s*;/;
		
		this.currentDoc = null;
		this.templates = [];
		
		this.$loadTemplates();
	};
	
	Constr.prototype = {
		
		getTemplates: function (prefix) {
			var re = new RegExp("^" + prefix);
			var matches = [];
			for (var i = 0; i < this.templates.length; i++) {
				if (this.templates[i].name.match(re)) {
					matches.push(this.templates[i]);
				}
			}
			return matches;
		},
		
		gotoDefinition: function(doc, name) {
			$.each(doc.functions, function (i, func) {
				if (name == func.name) {
					switch (func.type) {
					case TYPE_FUNCTION:
						eXide.app.findFunction(func.source == '' ? null : func.source, name);
						break;
					case TYPE_VARIABLE:
						eXide.app.findVarDecl(func.source == '' ? null : func.source, name);
						break;
					}
					return;
				}
			});
		},
		
		updateOutline: function(doc) {
			this.currentDoc = doc;
			doc.functions = [];
			$("#outline").empty();
			if (doc.getMime() == "application/xquery") {
				var code = doc.getText();
				this.$parseLocalFunctions(code, doc);
				this.$outlineUpdate(doc);
				var imports = this.$parseImports(code);
				if (imports)
					this.$resolveImports(doc, imports);
			}
		},
		
		clearOutline: function() {
			$("#outline").empty();
		},
		
		$parseLocalFunctions: function(text, doc) {
			doc.functions = [];
			
			var funcDefs = text.match(this.funcDefRe);
			if (funcDefs) {
				for (var i = 0; i < funcDefs.length; i++) {
					var func = this.funcRe.exec(funcDefs[i]);
					doc.functions.push({
						type: TYPE_FUNCTION,
						name: func[2],
						signature: func[1]
					});
				}
			}
			var varDefs = text.match(this.varDefRe);
			if (varDefs) {
				for (var i = 0; i < varDefs.length; i++) {
					var v = this.varRe.exec(varDefs[i]);
					doc.functions.push({
						type: TYPE_VARIABLE,
						name: v[1]
					});
				}
			}
		},
		
		$parseImports: function(code) {
			return code.match(this.parseImportRe);
		},
		
		$resolveImports: function(doc, imports) {
			var $this = this;
			var functions = [];
			
			var params = [];
			for (var i = 0; i < imports.length; i++) {
				var matches = this.moduleRe.exec(imports[i]);
				if (matches != null && matches.length == 4) {
					params.push("prefix=" + encodeURIComponent(matches[1]));
					params.push("uri=" + encodeURIComponent(matches[2]));
					params.push("source=" + encodeURIComponent(matches[3]));
				}
			}

			var basePath = "xmldb:exist://" + doc.getBasePath();
			params.push("base=" + encodeURIComponent(basePath));

			$.ajax({
				url: "outline",
				dataType: "json",
				type: "POST",
				data: params.join("&"),
				success: function (data) {
					if (data != null) {
						var modules = data.modules;
						for (var i = 0; i < modules.length; i++) {
							var funcs = modules[i].functions;
							if (funcs) {
								for (var j = 0; j < funcs.length; j++) {
									functions.push({
										type: TYPE_FUNCTION,
										name: funcs[j],
										signature: funcs[j],
										source: modules[i].source
									});
								}
							}
							var vars = modules[i].variables;
							if (vars) {
								for (var j = 0; j < vars.length; j++) {
									functions.push({
										type: TYPE_VARIABLE,
										name: "$" + vars[j],
										source: modules[i].source
									});
								}
							}
						}
						doc.functions = doc.functions.concat(functions);
						
						$this.$outlineUpdate(doc);
					}
				}
			});
			return functions;
		},
		
		$sortFunctions: function(doc) {
			doc.functions.sort(function (a, b) {
				return(a.name == b.name) ? 0 : (a.name > b.name) ? 1 : -1;
			});
		},
		
		$outlineUpdate: function(doc) {
			this.$sortFunctions(doc);
			
			if (this.currentDoc != doc)
				return;
			
			var layout = $("body").layout();
			layout.open("west");
			eXide.app.resize();
			
			var ul = $("#outline");
			ul.empty();
			for (var i = 0; i < doc.functions.length; i++) {
				var func = doc.functions[i];
				var li = document.createElement("li");
				var a = document.createElement("a");
				if (func.signature)
					a.title = func.signature;
				if (func.type == TYPE_FUNCTION)
					a.className = "t_function";
				else
					a.className = "t_variable";
				if (func.source)
					a.href = "#" + func.source;
				else
					a.href = "#";
				a.appendChild(document.createTextNode(func.name));
				li.appendChild(a);
				ul.append(li);
				
				$(a).click(function () {
					var path = this.hash.substring(1);
					if (this.className == "t_function") {
						eXide.app.findFunction(path == '' ? null : path, $(this).text());
					} else {
						eXide.app.findVarDecl(path == '' ? null : path, $(this).text());
					}
					return false;
				});
			}
		},
		
		$loadTemplates: function() {
			var $this = this;
			$.ajax({
				url: "templates/snippets.xml",
				dataType: "xml",
				type: "GET",
				success: function (xml) {
					$(xml).find("snippet").each(function () {
						var snippet = $(this);
						var abbrev = snippet.attr("abbrev");
						var description = snippet.find("description").text();
						var code = snippet.find("code").text();
						$this.templates.push({
							TYPE: TYPE_TEMPLATE,
							name: abbrev,
							help: description,
							template: code
						});
					});
				}
			});
		}
	};
	
	return Constr;
}());