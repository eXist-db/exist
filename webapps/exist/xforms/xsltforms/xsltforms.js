/* Rev. 525

Copyright (C) 2008-2012 agenceXML - Alain COUTHURES
Contact at : xsltforms@agencexml.com

Copyright (C) 2006 AJAXForms S.L.
Contact at: info@ajaxforms.com

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
	
*/
		
		
		
		
		
		
/*jshint noarg:false, forin:true, noempty:true, eqeqeq:true, evil:true, bitwise:true, loopfunc:true, scripturl:true, strict:true, undef:true, curly:true, browser:true, devel:true, maxerr:100, newcap:true */
//"use strict";
/*members */
/*global ActiveXObject, alert, Document, XDocument, Element, DOMParser, XMLSerializer, XSLTProcessor */
/*global tinyMCE */
/*global XMLDocument : true */
/*global XsltForms_browser : true, XsltForms_nodeType : true, XsltForms_schema : true */
/*global XsltForms_calendar : true, XsltForms_numberList : true, XsltForms_xmlevents : true */
/*global XsltForms_abstractAction : true, XsltForms_repeat : true, XsltForms_element : true */
/*global XsltForms_control : true, XsltForms_xpathCoreFunctions : true, XsltForms_xpathFunctionExceptions : true */
/*global XsltForms_idManager : true, XsltForms_xpath : true, XsltForms_listener : true */
/*global XsltForms_typeDefs : true, XsltForms_exprContext : true */
var XsltForms_globals = {

	fileVersion: "525",
	fileVersionNumber: 525,

	language: "navigator",
	debugMode: false,
	debugButtons: [
		{label: "Profiler", action: "XsltForms_globals.profiling();"}
		/*
		,{label: "Instance Viewer"}
		,{label: "Validator"},
		,{label: "XPath Evaluator"}
		*/
	],
	cont : 0,
	ready : false,
	body : null,
	models : [],
	changes : [],
	newChanges : [],
	building : false,
	posibleBlur : false,
	bindErrMsgs : [],		// binding-error messages gathered during refreshing
	htmltime: 0,
	creatingtime: 0,
	inittime: 0,
	refreshtime: 0,
	refreshcount: 0,
	counters: {
		group: 0,
		input: 0,
		item: 0,
		itemset: 0,
		label: 0,
		output: 0,
		repeat: 0,
		select: 0,
		trigger: 0
	},
	nbsubforms: 0,

		

	debugging : function() {
		if (document.documentElement.childNodes[0].nodeType === 8 || (XsltForms_browser.isIE && document.documentElement.childNodes[0].childNodes[1] && document.documentElement.childNodes[0].childNodes[1].nodeType === 8)) {
			var body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
			if (this.debugMode) {
				var dbg = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "div") : document.createElement("div");
				dbg.setAttribute("style", "border-bottom: thin solid #888888;");
				dbg.setAttribute("id", "xsltforms_debug");
				var img = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "img") : document.createElement("img");
				img.setAttribute("src", XsltForms_browser.ROOT+"magnify.png");
				img.setAttribute("style", "vertical-align:middle;border:0;");
				dbg.appendChild(img);
				var spn = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "span") : document.createElement("span");
				spn.setAttribute("style", "font-size:16pt");
				var txt = document.createTextNode(" Debug Mode");
				spn.appendChild(txt);
				dbg.appendChild(spn);
				var spn2 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "span") : document.createElement("span");
				spn2.setAttribute("style", "font-size:11pt");
				var txt2 = document.createTextNode(" ("+this.fileVersion+") \xA0\xA0\xA0");
				spn2.appendChild(txt2);
				dbg.appendChild(spn2);
				var a = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "a") : document.createElement("a");
				a.setAttribute("href", "http://www.w3.org/TR/xforms11/");
				a.setAttribute("style", "text-decoration:none;");
				var img2 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "img") : document.createElement("img");
				img2.setAttribute("src", XsltForms_browser.ROOT+"valid-xforms11.png");
				img2.setAttribute("style", "vertical-align:middle;border:0;");
				a.appendChild(img2);
				dbg.appendChild(a);
				var a2 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "a") : document.createElement("a");
				a2.setAttribute("href", "http://www.agencexml.com/xsltforms");
				a2.setAttribute("style", "text-decoration:none;");
				var img3 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "img") : document.createElement("img");
				img3.setAttribute("src", XsltForms_browser.ROOT+"poweredbyXSLTForms.png");
				img3.setAttribute("style", "vertical-align:middle;border:0;");
				a2.appendChild(img3);
				dbg.appendChild(a2);
				var spn3 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "span") : document.createElement("span");
				spn3.setAttribute("style", "font-size:11pt");
				var txt3 = document.createTextNode(" Press ");
				spn3.appendChild(txt3);
				dbg.appendChild(spn3);
				var a3 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "a") : document.createElement("a");
				a3.setAttribute("onClick", "XsltForms_globals.debugMode=false;XsltForms_globals.debugging();return false;");
				a3.setAttribute("style", "text-decoration:none;");
				a3.setAttribute("href", "#");
				var img4 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "img") : document.createElement("img");
				img4.setAttribute("src", XsltForms_browser.ROOT+"F1.png");
				img4.setAttribute("style", "vertical-align:middle;border:0;");
				a3.appendChild(img4);
				dbg.appendChild(a3);
				var spn4 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "span") : document.createElement("span");
				spn4.setAttribute("style", "font-size:11pt");
				var txt4 = document.createTextNode(" to toggle mode ");
				spn4.appendChild(txt4);
				dbg.appendChild(spn4);
				var br = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "br") : document.createElement("br");
				dbg.appendChild(br);
				var txt5 = document.createTextNode(" \xA0\xA0\xA0\xA0\xA0\xA0");
				dbg.appendChild(txt5);
				for (var i = 0, len = XsltForms_globals.debugButtons.length; i < len; i++) {
					if (XsltForms_globals.debugButtons[i].action) {
						var btn = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "button") : document.createElement("button");
						btn.setAttribute("type", "button");
						btn.setAttribute("onClick", XsltForms_globals.debugButtons[i].action);
						var txt6 = document.createTextNode(" "+XsltForms_globals.debugButtons[i].label+" ");
						btn.appendChild(txt6);
						dbg.appendChild(btn);
					} else {
						var a4 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "a") : document.createElement("a");
						a4.setAttribute("href", "http://www.agencexml.com/xsltforms");
						var txt7 = document.createTextNode(" Debugging extensions can be downloaded! ");
						a4.appendChild(txt7);
						dbg.appendChild(a4);
						break;
					}
				}
				var br2 = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "br") : document.createElement("br");
				dbg.appendChild(br2);
				var ifr = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "iframe") : document.createElement("iframe");
				ifr.setAttribute("src", "http://www.agencexml.com/direct/banner.htm");
				ifr.setAttribute("style", "width:100%;height:90px;border:none;margin:0;");
				ifr.setAttribute("frameborder", "0");
				dbg.appendChild(ifr);
				body.insertBefore(dbg, body.firstChild);
				document.getElementById("xsltforms_console").style.display = "block";
			} else {
				body.removeChild(document.getElementById("xsltforms_debug"));
				document.getElementById("xsltforms_console").style.display = "none";
			}
		}
	},

		

	xmlrequest : function(method, resource) {
		if (typeof method !== "string") {
			return '<error xmlns="">Invalid method "'+method+'"</error>';
		}
		method = method.toLowerCase();
		switch (method) {
			case "get":
				switch (resource) {
					case "xsltforms-profiler":
						return XsltForms_globals.profiling_data();
					default:
						return '<error xmlns="">Unknown resource "'+resource+'" for method "'+method+'"</error>';
				}
				break;
			case "put":
				return;
			default:
				return '<error xmlns="">Unknown method "'+method+'"</error>';
		}
	},

		

	profiling_data : function() {
		var s = '<xsltforms:dump xmlns:xsltforms="http://www.agencexml.com/xsltforms">';
		s += '<xsltforms:date>' + XsltForms_browser.i18n.format(new Date(), "yyyy-MM-ddThh:mm:ssz", true) + '</xsltforms:date>';
		s += '<xsltforms:location>' + window.location.href + '</xsltforms:location>';
		s += '<xsltforms:appcodename>' + navigator.appCodeName + '</xsltforms:appcodename>';
		s += '<xsltforms:appname>' + navigator.appName + '</xsltforms:appname>';
		s += '<xsltforms:appversion>' + navigator.appVersion + '</xsltforms:appversion>';
		s += '<xsltforms:platform>' + navigator.platform + '</xsltforms:platform>';
		s += '<xsltforms:useragent>' + navigator.userAgent + '</xsltforms:useragent>';
		s += '<xsltforms:xsltengine>' + this.xsltEngine + '</xsltforms:xsltengine>';
		var xsltsrc = '<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:msxsl="urn:schemas-microsoft-com:xslt">';
		xsltsrc += '	<xsl:output method="xml"/>';
		xsltsrc += '	<xsl:template match="/">';
		xsltsrc += '		<xsl:variable name="version">';
		xsltsrc += '			<xsl:if test="system-property(\'xsl:vendor\')=\'Microsoft\'">';
		xsltsrc += '				<xsl:value-of select="system-property(\'msxsl:version\')"/>';
		xsltsrc += '			</xsl:if>';
		xsltsrc += '		</xsl:variable>';
		xsltsrc += '		<properties><xsl:value-of select="concat(\'|\',system-property(\'xsl:vendor\'),\' \',system-property(\'xsl:vendor-url\'),\' \',$version,\'|\')"/></properties>';
		xsltsrc += '	</xsl:template>';
		xsltsrc += '</xsl:stylesheet>';
		var res = XsltForms_browser.transformText("<dummy/>", xsltsrc, true);
		var spres = res.split("|");
		s += '<xsltforms:xsltengine2>' + spres[1] + '</xsltforms:xsltengine2>';
		s += '<xsltforms:version>' + this.fileVersion + '</xsltforms:version>';
		s += '<xsltforms:instances>';
		var pos = 0;
		for (var m = 0, mlen = XsltForms_globals.models.length; m < mlen; m++) {
			if (XsltForms_globals.models[m].element.id !== XsltForms_browser.idPf + "model-config") {
				for (var id in XsltForms_globals.models[m].instances) {
					if (XsltForms_globals.models[m].instances.hasOwnProperty(id)) {
						var count = XsltForms_browser.selectNodesLength("descendant::node() | descendant::*/@*[not(starts-with(local-name(),'XsltForms_'))]", XsltForms_globals.models[m].instances[id].doc);
						s += '<xsltforms:instance id="' + id + '">' + count + '</xsltforms:instance>';
						pos++;
					}
				}
			}
		}
		s += '</xsltforms:instances>';
		s += '<xsltforms:controls>';
		s += '<xsltforms:control type="group">' + XsltForms_globals.counters.group + '</xsltforms:control>';
		s += '<xsltforms:control type="input">' + XsltForms_globals.counters.input + '</xsltforms:control>';
		s += '<xsltforms:control type="item">' + XsltForms_globals.counters.item + '</xsltforms:control>';
		s += '<xsltforms:control type="itemset">' + XsltForms_globals.counters.itemset + '</xsltforms:control>';
		s += '<xsltforms:control type="output">' + XsltForms_globals.counters.output + '</xsltforms:control>';
		s += '<xsltforms:control type="repeat">' + XsltForms_globals.counters.repeat + '</xsltforms:control>';
		s += '<xsltforms:control type="select">' + XsltForms_globals.counters.select + '</xsltforms:control>';
		s += '<xsltforms:control type="trigger">' + XsltForms_globals.counters.trigger + '</xsltforms:control>';
		s += '</xsltforms:controls>';
		var re = /<\w/g;
		var hc = 0;
		var bhtml = document.documentElement.innerHTML;
		while (re.exec(bhtml)) {
			hc++;
		}
		s += '<xsltforms:htmlelements>' + hc + '</xsltforms:htmlelements>';
		s += '<xsltforms:htmltime>' + this.htmltime + '</xsltforms:htmltime>';
		s += '<xsltforms:creatingtime>' + this.creatingtime + '</xsltforms:creatingtime>';
		s += '<xsltforms:inittime>' + this.inittime + '</xsltforms:inittime>';
		s += '<xsltforms:refreshcount>' + this.refreshcount + '</xsltforms:refreshcount>';
		s += '<xsltforms:refreshtime>' + this.refreshtime + '</xsltforms:refreshtime>';
		var exprtab = [];
		for (var expr in XsltForms_xpath.expressions) {
			if (XsltForms_xpath.expressions.hasOwnProperty(expr)) {
				exprtab[exprtab.length] = {expr: expr, evaltime: XsltForms_xpath.expressions[expr].evaltime};
			}
		}
		exprtab.sort(function(a,b) { return b.evaltime - a.evaltime; });
		var top = 0;
		s += '<xsltforms:xpaths>';
		if (exprtab.length > 0) {
			for (var i = 0; i < exprtab.length && i < 20; i++) {
				s += '<xsltforms:xpath expr="' + XsltForms_browser.escape(exprtab[i].expr) + '">' + exprtab[i].evaltime + '</xsltforms:xpath>';
				top += exprtab[i].evaltime;
			}
			if (exprtab.length > 20) {
				var others = 0;
				for (var j = 20; j < exprtab.length; j++) {
					others += exprtab[j].evaltime;
				}
				s += '<xsltforms:others count="' + (exprtab.length - 20) + '">' + others + '</xsltforms:others>';
				top += others;
			}
			s += '<xsltforms:total>' + top + '</xsltforms:total>';
		}
		s += '</xsltforms:xpaths>';
		s += '</xsltforms:dump>';
		return s;
	},

		

	profiling : function() {
		var req = XsltForms_browser.openRequest("GET", XsltForms_browser.ROOT + "XsltForms_profiler.xhtml", false);
		if (req.overrideMimeType) {
			req.overrideMimeType("application/xml");
		}
		try {        
			req.send(null);
		} catch(e) {
			alert("File not found: " + XsltForms_browser.ROOT + "XsltForms_profiler.xhtml");
		}
		if (req.status === 200) {
			var s = XsltForms_browser.transformText(req.responseText, XsltForms_browser.ROOT + "xsltforms.xsl", false, "xsltforms_debug", "false", "baseuri", XsltForms_browser.ROOT);
			if (s.substring(0, 21) === '<?xml version="1.0"?>') {
				s = '<!DOCTYPE HTML PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">' + s.substring(21);
			}
			var prow = window.open("about:blank","_blank");
			prow.document.write(s);
			prow.document.close();
		}
	},

		

	init : function() {
		XsltForms_browser.setValue(document.getElementById("statusPanel"), XsltForms_browser.i18n.get("status"));
		var b = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
		this.body = b;
		document.onhelp = function(){return false;};
		window.onhelp = function(){return false;};
		XsltForms_browser.events.attach(document, "keydown", function(evt) {
			if (evt.keyCode === 112) {
				XsltForms_globals.debugMode = !XsltForms_globals.debugMode;
				XsltForms_globals.debugging();
				if (evt.stopPropagation) {
					evt.stopPropagation();
					evt.preventDefault();
				} else {
					evt.cancelBubble = true;
				}
				return false;
			}
		});
		XsltForms_browser.events.attach(b, "click", function(evt) {
			var target = XsltForms_browser.events.getTarget(evt);
			var parent = target;
			while (parent && parent.nodeType === XsltForms_nodeType.ELEMENT) {
				if (XsltForms_browser.hasClass(parent, "xforms-repeat-item")) {
					XsltForms_repeat.selectItem(parent);
				}
				parent = parent.parentNode;
			}
			parent = target;
			while (parent && parent.nodeType === XsltForms_nodeType.ELEMENT) {
				var xf = parent.xfElement;
				if (xf) {
					if(typeof parent.node !== "undefined" && parent.node && xf.focus && !XsltForms_browser.getBoolMeta(parent.node, "readonly")) {
						var name = target.nodeName.toLowerCase();
						xf.focus(name === "input" || name === "textarea");
					}
					if(xf.click) {
						xf.click(target);
						break;
					}
				}
				parent = parent.parentNode;
			}
		} );
		XsltForms_browser.events.onunload = function() {
			XsltForms_globals.close();
		};
		this.openAction();
		XsltForms_xmlevents.dispatchList(this.models, "xforms-model-construct");
		this.refresh();
		this.closeAction();
		this.ready = true;
		XsltForms_browser.dialog.hide("statusPanel", false);
	},

		

	close : function() {
		if (XsltForms_globals.body) {
			this.openAction();
			//XsltForms_xmlevents.dispatchList(XsltForms_globals.models, "xforms-model-destruct");
			for (var i = 0, len = XsltForms_listener.destructs.length; i < len; i++) {
				XsltForms_listener.destructs[i].callback({target: XsltForms_listener.destructs[i].observer});
			}
			this.closeAction();
			XsltForms_idManager.clear();
			this.defaultModel = null;
			this.changes = [];
			this.models = [];
			this.body = null;
			this.cont = 0;
			this.dispose(document.documentElement);
			//XsltForms_browser.events.flush_();
			if (XsltForms_browser.events.cache) {
				for (var j = XsltForms_browser.events.cache.length - 1; j >= 0; j--) {
					var item = XsltForms_browser.events.cache[j];
					XsltForms_browser.events.detach(item[0], item[1], item[2], item[3]);
				}
			}
			XsltForms_listener.destructs = [];
			XsltForms_schema.all = {};
			XsltForms_typeDefs.initAll();
			XsltForms_calendar.INSTANCE = null;
			this.ready = false;
			this.building = false;
			XsltForms_globals.posibleBlur = false;
		}
	},

		

	openAction : function() {
		if (this.cont++ === 0) {
			XsltForms_browser.debugConsole.clear();
		}
	},

		

	closeAction : function() {
		if (this.cont === 1) {
			this.closeChanges();
		}
		this.cont--;
	},

		

	closeChanges : function() {
		var changes = this.changes;
		for (var i = 0, len = changes.length; i < len; i++) {
			var change = changes[i];
			if (change.instances) {//Model
				if (change.rebuilded) {
					XsltForms_xmlevents.dispatch(change, "xforms-rebuild");
				} else {
					XsltForms_xmlevents.dispatch(change, "xforms-recalculate");
				}
			//} else { // Repeat or tree
			}
		}
		if (changes.length > 0) {
			this.refresh();
			if (this.changes.length > 0) {
				this.closeChanges();
			}
		}
	},

		

	error : function(element, event, message, causeMessage) {
		XsltForms_browser.dialog.hide("statusPanel", false);
		XsltForms_browser.setValue(document.getElementById("statusPanel"), message);
		XsltForms_browser.dialog.show("statusPanel", null, false);
		if (element) {
			XsltForms_xmlevents.dispatch(element, event);
		}
		if (causeMessage) {
			message += " : " + causeMessage;
		}
		XsltForms_browser.debugConsole.write("Error: " + message);
		throw event;        
	},

		

	refresh : function() {
		var d1 = new Date();
		this.building = true;
		this.build(this.body, (this.defaultModel.getInstanceDocument() ? this.defaultModel.getInstanceDocument().documentElement : null), true);
		if (this.newChanges.length > 0) {
			this.changes = this.newChanges;
			this.newChanges = [];
		} else {
			this.changes.length = 0;
		}
		for (var i = 0, len = this.models.length; i < len; i++) {
			var model = this.models[i];
			if (model.newNodesChanged.length > 0 || model.newRebuilded) {
				model.nodesChanged = model.newNodesChanged;
				model.newNodesChanged = [];
				model.rebuilded = model.newRebuilded;
				model.newRebuilded = false;
			} else {
				model.nodesChanged.length = 0;
				model.rebuilded = false;
			}
		}
		this.building = false;
		// Throw any gathered binding-errors.
		//
		if (this.bindErrMsgs.length) {
			this.error(this.defaultModel, "xforms-binding-exception", "Binding Errors: \n" + this.bindErrMsgs.join("\n  "));
			this.bindErrMsgs = [];
		}
		var d2 = new Date();
		this.refreshtime += d2 - d1;
		this.refreshcount++;
	},

		

	build : function(element, ctx, selected) {
		if (element.nodeType !== XsltForms_nodeType.ELEMENT || element.id === "xsltforms_console" || element.hasXFElement === false) {
			return;
		}
		var xf = element.xfElement;
		var hasXFElement = !!xf;
//		if (element.id) XsltForms_browser.debugConsole.write("build1: " + element.id + " -> " + ctx.localName + " " + ctx.getAttribute("id") + " " + (element.node ? element.getAttribute("mixedrepeat") + " " + element.node.localName + " " + element.node.getAttribute("id") : ""));
		if (element.getAttribute("mixedrepeat") === "true") {
			//ctx = element.node || ctx;
			selected = element.selected;
		}
		//if (!ctx) { alert("XsltForms_globals.build " + element.id + " no ctx"); }
		if (xf) {
			xf.build(ctx);
//			if (element.id) XsltForms_browser.debugConsole.write("build2: " + element.id + " -> " + ctx.localName + " " + ctx.getAttribute("id") + " " + (element.node ? element.getAttribute("mixedrepeat") + " " + element.node.localName + " " + element.node.getAttribute("id") : ""));
			if (xf.isRepeat) {
				xf.refresh(selected);
			}
		}
//		XsltForms_browser.debugConsole.write("build3: " + element.localName + " " +(element.node ? "Node!" + element.node.localName + " " + element.node.getAttribute("id") + " <> " : "CTX ") + ctx.localName + " " + ctx.getAttribute("id"));
		ctx = element.node || ctx;
		var childs = element.children;
		var sel = element.selected;
		if (typeof sel !== "undefined") {
			selected = sel;
		}
		if (!xf || !xf.isRepeat || xf.nodes.length > 0) {
			for (var i = 0; i < childs.length && this.building; i++) {
				hasXFElement = (!childs[i].getAttribute("cloned") ? this.build(childs[i], ctx, selected) : false) || hasXFElement;
			}
		}
		if(this.building) {
			if (xf && xf.changed) {
				xf.refresh(selected);
				xf.changed = false;
			}
			if (!element.hasXFElement) {
				element.hasXFElement = hasXFElement;
			}
		}
		return hasXFElement;
	},

		

	addChange : function(element) {
		var list = this.building? this.newChanges : this.changes;
		if (!XsltForms_browser.inArray(element, list)) {
			list.push(element);
		}
	},

		

	dispose : function(element) {
		if (element.nodeType !== XsltForms_nodeType.ELEMENT || element.id === "xsltforms_console") {
			return;
		}
		element.listeners = null;
		element.node = null;
		element.hasXFElement = null;
		var xf = element.xfElement;
		if (xf) {
			xf.dispose();
		}
		var childs = element.childNodes;
		for (var i = 0; i < childs.length; i++) {
			this.dispose(childs[i]);
		}
	},

		

	blur : function(direct) {
		if ((direct || this.posibleBlur) && this.focus) {
			if (this.focus.element) {
				this.openAction();
				XsltForms_xmlevents.dispatch(this.focus, "DOMFocusOut");
				XsltForms_browser.setClass(this.focus.element, "xforms-focus", false);
				this.focus.blur();
				this.closeAction();
			}
			this.posibleBlur = false;
			this.focus = null;
		}
	}
};

	
		
		
		
function XsltForms_subform(subform, id, eltid) {
	this.subform = subform;
	this.id = id;
	this.eltid = eltid;
	if (eltid) {
		document.getElementById(eltid).xfSubform = this;
	}
	this.models = [];
	this.instances = [];
	this.binds = [];
	this.xpaths = [];
	this.subforms = [];
	this.ready = false;
	if (subform) {
		subform.subforms.push(this);
	}
	XsltForms_subform.subforms[id] = this;
}

XsltForms_subform.subforms = [];

		

XsltForms_subform.prototype.construct = function() {
	for (var i = 0, len = this.instances.length; i < len; i++) {
		this.instances[i].construct(this);
	}
	XsltForms_browser.forEach(this.binds, "refresh");
	this.ready = true;
};

		

XsltForms_subform.prototype.dispose = function() {
	var scriptelt = document.getElementById(this.id + "-script");
	scriptelt.parentNode.removeChild(scriptelt);
	for (var h = 0, len0 = this.subforms.length; h < len0; h++) {
		this.subforms[h].dispose();
	}
	this.subforms = null;
	for (var i = 0, len = this.models.length; i < len; i++) {
		this.models[i].dispose(this);
	}
	this.models = null;
	for (var j = 0, len2 = this.instances.length; j < len2; j++) {
		this.instances[j].dispose(this);
	}
	this.instances = null;
	for (var k = 0, len3 = this.xpaths.length; k < len3; k++) {
		this.xpaths[k].dispose(this);
	}
	this.xpaths = null;
	this.binds = null;
	XsltForms_globals.dispose(this.id);
	XsltForms_subform.subforms[this.id] = null;
	var parentform = this.subform;
	if (parentform) {
		var parentsubforms = parentform.subforms;
		for (var l = 0, len4 = parentsubforms.length; l < len4; l++) {
			if (parentsubforms[l] === this) {
				if (l < len4 - 1) {
					parentsubforms[l] = parentsubforms[len4 - 1];
				}
				parentsubforms.pop();
				break;
			}
		}
	}
};


	
		
		

var XsltForms_browser = {
	jsFileName : "xsltforms.js",

		

	isOpera : navigator.userAgent.match(/\bOpera\b/),
	isIE : navigator.userAgent.match(/\bMSIE\b/) && !navigator.userAgent.match(/\bOpera\b/),
	isIE9 : navigator.userAgent.match(/\bMSIE\b/) && !navigator.userAgent.match(/\bOpera\b/) && window.addEventListener,
	isIE6 : navigator.userAgent.match(/\bMSIE 6\.0/),
    isMozilla : navigator.userAgent.match(/\bGecko\b/),
	isFF2 : navigator.userAgent.match(/\bFirefox[\/\s]2\.\b/),
	isXhtml : false, // document.documentElement.namespaceURI === "http://www.w3.org/1999/xhtml",
	setClass : function(element, className, value) {
		XsltForms_browser.assert(element && className);
		if (value) {
			if (!this.hasClass(element, className)) {
				element.className += " " + className;
			}
		} else if (element.className) {
			element.className = element.className.replace(className, "");
		}
	},

		

	hasClass : function(element, className) {
		var cn = element.className;
		return XsltForms_browser.inArray(className, (cn && cn.split(" ")) || []);
	},
	initHover : function(element) {
		XsltForms_browser.events.attach(element, "mouseover", function(event) {
			XsltForms_browser.setClass(XsltForms_browser.events.getTarget(event), "hover", true);
		} );
		XsltForms_browser.events.attach(element, "mouseout", function(event) {
			XsltForms_browser.setClass(XsltForms_browser.events.getTarget(event), "hover", false);
		} );
	},
	getEventPos : function(ev) {
		ev = ev || window.event;
		return { x : ev.pageX || ev.clientX + window.document.body.scrollLeft || 0,
			y : ev.pageY || ev.clientY + window.document.body.scrollTop || 0 };
	},
	getAbsolutePos : function(e) {
		var r = XsltForms_browser.getPos(e);
		if (e.offsetParent) {
			var tmp = XsltForms_browser.getAbsolutePos(e.offsetParent);
			r.x += tmp.x;
			r.y += tmp.y;
		}
		return r;
	},
	getPos : function(e) {
		var is_div = /^div$/i.test(e.tagName);
		var r = {
			x: e.offsetLeft - (is_div && e.scrollLeft? e.scrollLeft : 0),
			y: e.offsetTop - (is_div && e.scrollTop? e.scrollTop : 0)
		};
		return r;
	},
	setPos : function(element, left, top) {
		if (element.offsetParent) {
			var tmp = XsltForms_browser.getAbsolutePos(element.offsetParent);
			left -= tmp.x;
			top -= tmp.y;
		}
		element.style.top = top + "px";
		element.style.left = left + "px";
	},

		

	loadProperties : function(name) {
		var uri = this.ROOT + name;
		var req = XsltForms_browser.openRequest("GET", uri, false);
		if (req.overrideMimeType) {
			req.overrideMimeType("application/xml");
		}
		try {        
			req.send(null);
		} catch(e) {
			alert("File not found: " + uri);
		}
		if (req.status === 200) {
			XsltForms_browser.loadNode(XsltForms_browser.config, XsltForms_browser.selectSingleNode('//properties', req.responseXML));
			var inst = document.getElementById(XsltForms_browser.idPf + "instance-config").xfElement;
			XsltForms_browser.config = inst.doc.documentElement;
			inst.srcXML = XsltForms_browser.saveXML(XsltForms_browser.config);
			XsltForms_browser.setMeta(XsltForms_browser.config, "instance", XsltForms_browser.idPf + "instance-config");
			XsltForms_browser.setMeta(XsltForms_browser.config, "model", XsltForms_browser.idPf + "model-config");
			//XMLEvents.dispatch(properties.model, "xforms-rebuild");
			//xforms.refresh();
		}
	},

		

	constructURI : function(uri) {
		if (uri.match(/^[a-zA-Z0-9+\.\-]+:\/\//)) {
			return uri;
		}
		if (uri.charAt(0) === '/') {
			return document.location.href.substr(0, document.location.href.replace(/:\/\//, ":\\\\").indexOf("/")) + uri;
		}
		var href = document.location.href;
		var idx = href.indexOf("?");
		href =  idx === -1 ? href : href.substr(0, idx);
		idx = href.replace(/:\/\//, ":\\\\").lastIndexOf("/");
		if (href.length > idx + 1) {
			return (idx === -1 ? href : href.substr(0, idx)) + "/" + uri;
		}
		return href + uri;
	},

		

	createElement : function(type, parent, content, className) {
		var el = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", type) : document.createElement(type);
		if (className) {
			el.className = className;
		}
		if (parent) {
			parent.appendChild(el);
		}
		if (content) {
			el.appendChild(document.createTextNode(content));
		}
		return el;
	},

		

	getWindowSize : function() {
		var myWidth = 0, myHeight = 0, myOffsetX = 0, myOffsetY = 0, myScrollX = 0, myScrollY = 0;
		if( typeof( window.innerWidth ) === "number" ) {
			//Non-IE
			myWidth = document.documentElement.clientWidth;
			myHeight = document.documentElement.clientHeight;
			myOffsetX = document.body ? Math.max(document.documentElement.clientWidth, document.body.clientWidth) : document.documentElement.clientWidth; // body margins ?
			myOffsetY = document.body ? Math.max(document.documentElement.clientHeight, document.body.clientHeight) : document.documentElement.clientHeight; // body margins ?
			myScrollX = window.scrollX;
			myScrollY = window.scrollY;
		} else if( document.documentElement && ( document.documentElement.clientWidth || document.documentElement.clientHeight ) ) {
			//IE 6+ in 'standards compliant mode'
			myWidth = document.documentElement.clientWidth;
			myHeight = document.documentElement.clientHeight;
			myOffsetX = Math.max(document.documentElement.clientWidth, document.body.clientWidth); // body margins ?
			myOffsetY = Math.max(document.documentElement.clientHeight, document.body.clientHeight); // body margins ?
			myScrollX = document.body.parentNode.scrollLeft;
			myScrollY = document.body.parentNode.scrollTop;
		}
		return {
			height : myHeight,
			width : myWidth,
			offsetX : myOffsetX,
			offsetY : myOffsetY,
			scrollX : myScrollX,
			scrollY : myScrollY
		};
	}
};

		

if (XsltForms_browser.isIE) {
	try {
		var xmlDoc = new ActiveXObject("Msxml2.DOMDocument.6.0");
		xmlDoc = null;
		XsltForms_browser.MSXMLver = "6.0";
	} catch(e) {
		XsltForms_browser.MSXMLver = "3.0";
	}
}
if (!XsltForms_browser.isIE) {
	XsltForms_browser.openRequest = function(method, uri, async) {
		// netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
		var req = new XMLHttpRequest();
		try {
			req.open(method, XsltForms_browser.constructURI(uri), async);
		} catch (e) {
			throw new Error("This browser does not support XHRs(Ajax)! \n Cause: " + (e.message || e.description || e) + " \n Enable Javascript or ActiveX controls (on IE) or lower security restrictions.");
		}
		if (XsltForms_browser.isMozilla) {
			req.overrideMimeType("text/xml");
		}
		return req;
	};
} else if (window.ActiveXObject) {
	XsltForms_browser.openRequest = function(method, uri, async) {
		var req;
		try {
			req = new ActiveXObject("Msxml2.XMLHTTP." + XsltForms_browser.MSXMLver); 
		} catch (e0) {
			try {
				req = new ActiveXObject("Msxml2.XMLHTTP");
			} catch (e) {
				throw new Error("This browser does not support XHRs(Ajax)! \n Cause: " + (e.message || e.description || e) + " \n Enable Javascript or ActiveX controls (on IE) or lower security restrictions.");
			}
		}
		req.open(method, XsltForms_browser.constructURI(uri), async);
		return req;
	};
} else {
	throw new Error("This browser does not support XHRs(Ajax)! \n Enable Javascript or ActiveX controls (on IE) or lower security restrictions.");
}

		

if (XsltForms_browser.isIE) {
	XsltForms_browser.transformText = function(xml, xslt, inline) {
		var xmlDoc = new ActiveXObject("MSXML2.DOMDocument." + XsltForms_browser.MSXMLver);
		xmlDoc.setProperty("AllowDocumentFunction", true);
		xmlDoc.validateOnParse = false;
		xmlDoc.loadXML(xml);
		var xslDoc = new ActiveXObject("MSXML2.FreeThreadedDOMDocument." + XsltForms_browser.MSXMLver);
		xslDoc.setProperty("AllowDocumentFunction", true);
		xslDoc.validateOnParse = false;
		if (inline) {
			xslDoc.loadXML(xslt);
		} else {
			xslDoc.async = false;
			xslDoc.load(xslt);
		}
		var xslTemplate = new ActiveXObject("MSXML2.XSLTemplate." + XsltForms_browser.MSXMLver);
		xslTemplate.stylesheet = xslDoc;
		var xslProc = xslTemplate.createProcessor();
		xslProc.input = xmlDoc;
		for (var i = 3, len = arguments.length-1; i < len ; i += 2) {
			xslProc.addParameter(arguments[i], arguments[i+1], "");
		}
		xslProc.transform();
		return xslProc.output;
    };
} else {
    XsltForms_browser.transformText = function(xml, xslt, inline) {
			var parser = new DOMParser();
			var serializer = new XMLSerializer();
			var xmlDoc = parser.parseFromString(xml, "text/xml");
			var xsltDoc;
			if (inline) {
				xsltDoc = parser.parseFromString(xslt, "text/xml");
			} else {
				xsltDoc = document.implementation.createDocument("","",null);
				if (xsltDoc.load) {
					xsltDoc.async = false;
					xsltDoc.load(xslt);
				} else {
					var xhttp = new XMLHttpRequest();
					xhttp.open("GET", xslt, false);
					xhttp.send("");
					xslt = xhttp.responseText;
					xsltDoc = parser.parseFromString(xslt, "text/xml");
				}
			}
			var xsltProcessor = new XSLTProcessor();
			if (!XsltForms_browser.isMozilla && !XsltForms_browser.isOpera) {
				xsltProcessor.setParameter(null, "xsltforms_caller", "true");
			}
			xsltProcessor.setParameter(null, "xsltforms_config", document.getElementById(XsltForms_browser.idPf + "instance-config").xfElement.srcXML);
			xsltProcessor.setParameter(null, "xsltforms_lang", XsltForms_globals.language);
			for (var i = 3, len = arguments.length-1; i < len ; i += 2) {
				xsltProcessor.setParameter(null, arguments[i], arguments[i+1]);
			}
			xsltProcessor.importStylesheet(xsltDoc);
			try {
				var resultDocument = xsltProcessor.transformToDocument(xmlDoc);
				return serializer.serializeToString(resultDocument);
			} catch (e) {
				return "";
			}
	};
}

		

XsltForms_browser.scripts = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "script") : document.getElementsByTagName("script");
for (var __i = 0, __len = XsltForms_browser.scripts.length; __i < __len; __i++) {
	var __src = XsltForms_browser.scripts[__i].src;
	if (__src.indexOf(XsltForms_browser.jsFileName) !== -1) {
		XsltForms_browser.ROOT = __src.replace(XsltForms_browser.jsFileName, "");
		break;
	}
}
XsltForms_browser.loadapplet = function() {
	var appelt = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "applet") : document.createElement("applet");
	appelt.setAttribute("style", "position:absolute;left:-1px");
	appelt.setAttribute("name", "xsltforms");
	appelt.setAttribute("code", "xsltforms.class");
	appelt.setAttribute("archive", XsltForms_browser.ROOT + "xsltforms.jar");
	appelt.setAttribute("width", "1");
	appelt.setAttribute("height", "1");
	var body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
	body.insertBefore(appelt, body.firstChild);
};

		

XsltForms_browser.IEReadFile = function(fname, encoding, xsdtype, title) {
	if (document.applets.xsltforms) {
		return document.applets.xsltforms.readFile(fname, encoding, xsdtype, title) || "";
	} else {
		XsltForms_browser.loadapplet();
		if (document.applets.xsltforms) {
			return document.applets.xsltforms.readFile(fname, encoding, xsdtype, title) || "";
		}
	}
	return "";
};

		

XsltForms_browser.javaReadFile = function(fname, encoding, xsdtype, title) {
	if (document.applets.xsltforms) {
		return document.applets.xsltforms.readFile(fname, encoding, xsdtype, title) || "";
	} else {
		XsltForms_browser.loadapplet();
		if (document.applets.xsltforms) {
			return document.applets.xsltforms.readFile(fname, encoding, xsdtype, title) || "";
		}
	}
	return "";
};

		

XsltForms_browser.javaWriteFile = function(fname, encoding, xsdtype, title, content) {
	if (document.applets.xsltforms) {
		if (fname === "") {
			fname = document.applets.xsltforms.lastChosenFileName;
		}
		return document.applets.xsltforms.writeFile(fname, encoding, xsdtype, title, content) === 1;
	} else {
		XsltForms_browser.loadapplet();
		if (document.applets.xsltforms) {
			if (fname === "") {
				fname = document.applets.xsltforms.lastChosenFileName;
			}
			return document.applets.xsltforms.writeFile(fname, encoding, xsdtype, title, content) === 1;
		}
	}
	return false;
};

		

XsltForms_browser.readFile = function(fname, encoding, xsdtype, title) {
	return XsltForms_browser.javaReadFile(fname, encoding, xsdtype, title);
};

		

XsltForms_browser.writeFile = function(fname, encoding, xsdtype, title, content) {
	return XsltForms_browser.javaWriteFile(fname, encoding, xsdtype, title, content);
};

XsltForms_browser.xsltsrc = '<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">';
XsltForms_browser.xsltsrc += '	<xsl:output method="xml" omit-xml-declaration="yes"/>';
XsltForms_browser.xsltsrc += '	<xsl:template match="@*[starts-with(translate(name(),\'ABCDEFGHIJKLMNOPQRSTUVWXYZ\',\'abcdefghijklmnopqrstuvwxyz\'),\'xsltforms_\')]" priority="1"/>';
XsltForms_browser.xsltsrc += '	<xsl:template match="@*|node()" priority="0">';
XsltForms_browser.xsltsrc += '		<xsl:copy>';
XsltForms_browser.xsltsrc += '			<xsl:apply-templates select="@*|node()"/>';
XsltForms_browser.xsltsrc += '		</xsl:copy>';
XsltForms_browser.xsltsrc += '	</xsl:template>';
XsltForms_browser.xsltsrc += '</xsl:stylesheet>';

XsltForms_browser.xsltsrcrelevant = '<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">';
XsltForms_browser.xsltsrcrelevant += '	<xsl:output method="xml" omit-xml-declaration="yes"/>';
XsltForms_browser.xsltsrcrelevant += '	<xsl:template match="*[@xsltforms_notrelevant = \'true\']" priority="1"/>';
XsltForms_browser.xsltsrcrelevant += '	<xsl:template match="@*[starts-with(translate(name(),\'ABCDEFGHIJKLMNOPQRSTUVWXYZ\',\'abcdefghijklmnopqrstuvwxyz\'),\'xsltforms_\')]" priority="1"/>';
XsltForms_browser.xsltsrcrelevant += '	<xsl:template match="@*" priority="0">';
XsltForms_browser.xsltsrcrelevant += '		<xsl:choose>';
XsltForms_browser.xsltsrcrelevant += '			<xsl:when test="parent::*/attribute::*[local-name() = concat(\'xsltforms_\',local-name(current()),\'_notrelevant\')] = \'true\'"/>';
XsltForms_browser.xsltsrcrelevant += '			<xsl:otherwise>';
XsltForms_browser.xsltsrcrelevant += '				<xsl:copy>';
XsltForms_browser.xsltsrcrelevant += '					<xsl:apply-templates select="node()"/>';
XsltForms_browser.xsltsrcrelevant += '				</xsl:copy>';
XsltForms_browser.xsltsrcrelevant += '			</xsl:otherwise>';
XsltForms_browser.xsltsrcrelevant += '		</xsl:choose>';
XsltForms_browser.xsltsrcrelevant += '	</xsl:template>';
XsltForms_browser.xsltsrcrelevant += '	<xsl:template match="node()" priority="0">';
XsltForms_browser.xsltsrcrelevant += '		<xsl:copy>';
XsltForms_browser.xsltsrcrelevant += '			<xsl:apply-templates select="@*|node()"/>';
XsltForms_browser.xsltsrcrelevant += '		</xsl:copy>';
XsltForms_browser.xsltsrcrelevant += '	</xsl:template>';
XsltForms_browser.xsltsrcrelevant += '</xsl:stylesheet>';

if (XsltForms_browser.isIE) {
	XsltForms_browser.createXMLDocument = function(xml) {
		var d = new ActiveXObject("MSXML2.DOMDocument." + XsltForms_browser.MSXMLver);
		d.setProperty("SelectionLanguage", "XPath");
		d.validateOnParse = false;
		//d.setProperty("SelectionNamespaces", "xmlns:xml='http://www.w3.org/XML/1998/namespace'");
		d.loadXML(xml);
		return d;
	};
	XsltForms_browser.setAttributeNS = function(node, ns, name, value) {
		node.setAttributeNode(node.ownerDocument.createNode(XsltForms_nodeType.ATTRIBUTE, name, ns));
		node.setAttribute(name, value);
	};
	XsltForms_browser.selectSingleNode = function(xpath, node) {
		try {
			return node.selectSingleNode(xpath);
		} catch (e) {
			return null;
		}
	};
	XsltForms_browser.selectSingleNodeText = function(xpath, node) {
		try {
			return node.selectSingleNode(xpath).text;
		} catch (e) {
			return "";
		}
	};
	XsltForms_browser.selectNodesLength = function(xpath, node) {
		try {
			return node.selectNodes(xpath).length;
		} catch (e) {
			return 0;
		}
	};
	XsltForms_browser.xsltDoc = new ActiveXObject("MSXML2.DOMDocument." + XsltForms_browser.MSXMLver);
	XsltForms_browser.xsltDoc.loadXML(XsltForms_browser.xsltsrc);
	XsltForms_browser.xsltDocRelevant = new ActiveXObject("MSXML2.DOMDocument." + XsltForms_browser.MSXMLver);
	XsltForms_browser.xsltDocRelevant.loadXML(XsltForms_browser.xsltsrcrelevant);
	XsltForms_browser.loadNode = function(dest, src) {
		var r = src.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	};
	XsltForms_browser.loadTextNode = function(dest, txt) {
		if (dest.nodeType === XsltForms_nodeType.ATTRIBUTE) {
			dest.value = txt;
		} else {
			while (dest.firstChild) {
				dest.removeChild(dest.firstChild);
			}
			dest.appendChild(dest.ownerDocument.createTextNode(txt));
		}
	};
	XsltForms_browser.loadXML = function(dest, xml) {
		var result = new ActiveXObject("MSXML2.DOMDocument." + XsltForms_browser.MSXMLver);
		result.setProperty("SelectionLanguage", "XPath");
		result.validateOnParse = false;
		result.loadXML(xml);
		var r = result.documentElement.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	};
	XsltForms_browser.saveXML = function(node, relevant) {
		if (node.nodeType === XsltForms_nodeType.ATTRIBUTE) { 
			return node.nodeValue;
		} else {
			if (node.nodeType === XsltForms_nodeType.TEXT) {
				var s = "";
				while (node && node.nodeType === XsltForms_nodeType.TEXT) {
					s += node.nodeValue;
					node = node.nextSibling;
				}
				return s;
			} else {
				var xmlDoc = new ActiveXObject("MSXML2.DOMDocument." + XsltForms_browser.MSXMLver);
				xmlDoc.setProperty("SelectionLanguage", "XPath"); 
				xmlDoc.appendChild(node.documentElement ? node.documentElement.cloneNode(true) : node.cloneNode(true));
				return relevant ? xmlDoc.transformNode(XsltForms_browser.xsltDocRelevant) : xmlDoc.transformNode(XsltForms_browser.xsltDoc);
			}
		}
	};
} else {
	XsltForms_browser.createXMLDocument = function(xml) {
		return XsltForms_browser.parser.parseFromString(xml, "text/xml");
	};
	XsltForms_browser.setAttributeNS = function(node, ns, name, value) {
		node.setAttributeNS(ns, name, value);
	};
	XsltForms_browser.selectSingleNode = function(xpath, node) {
		try {
			var nsResolver = document.createNSResolver(node);
			if (node.evaluate) {
				return node.evaluate(xpath, node, nsResolver, XPathResult.ANY_TYPE, null).iterateNext();
			} else {
				return node.ownerDocument.evaluate(xpath, node, nsResolver, XPathResult.ANY_TYPE, null).iterateNext();
			}
		} catch (e) {
			return null;
		}
	};
	XsltForms_browser.selectSingleNodeText = function(xpath, node) {
		try {
			var nsResolver = document.createNSResolver(node);
			if (node.evaluate) {
				return node.evaluate(xpath, node, nsResolver, XPathResult.ANY_TYPE, null).iterateNext().textContent;
			} else {
				return node.ownerDocument.evaluate(xpath, node, nsResolver, XPathResult.ANY_TYPE, null).iterateNext().textContent;
			}
		} catch (e) {
			return "";
		}
	};
	XsltForms_browser.selectNodesLength = function(xpath, node) {
		try {
			var nsResolver = document.createNSResolver(node);
			if (node.evaluate) {
				return node.evaluate(xpath, node, nsResolver, XPathResult.UNORDERED_NODE_SNAPSHOT_TYPE, null).snapshotLength;
			} else {
				return node.ownerDocument.evaluate(xpath, node, nsResolver, XPathResult.UNORDERED_NODE_SNAPSHOT_TYPE, null).snapshotLength;
			}
		} catch (e) {
			return 0;
		}
	};
	XsltForms_browser.parser = new DOMParser();
	XsltForms_browser.xsltDoc = XsltForms_browser.parser.parseFromString(XsltForms_browser.xsltsrc, "text/xml");
	XsltForms_browser.xsltProcessor = new XSLTProcessor();
	XsltForms_browser.xsltProcessor.importStylesheet(XsltForms_browser.xsltDoc);
	XsltForms_browser.xsltDocRelevant = XsltForms_browser.parser.parseFromString(XsltForms_browser.xsltsrcrelevant, "text/xml");
	XsltForms_browser.xsltProcessorRelevant = new XSLTProcessor();
	XsltForms_browser.xsltProcessorRelevant.importStylesheet(XsltForms_browser.xsltDocRelevant);
	XsltForms_browser.serializer = new XMLSerializer();
	XsltForms_browser.loadNode = function(dest, src) {
		var r = src.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	};
	XsltForms_browser.loadTextNode = function(dest, txt) {
		if (dest.nodeType === XsltForms_nodeType.ATTRIBUTE) {
			dest.value = txt;
		} else {
			while (dest.firstChild) {
				dest.removeChild(dest.firstChild);
			}
			dest.appendChild(dest.ownerDocument.createTextNode(txt));
		}
	};
	XsltForms_browser.loadXML = function(dest, xml) {
		var result = XsltForms_browser.parser.parseFromString(xml, "text/xml");
		var r = result.documentElement.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	};
	XsltForms_browser.saveXML = function(node, relevant) {
		if (node.nodeType === XsltForms_nodeType.ATTRIBUTE) { 
			return node.nodeValue;
		} else {
			if (node.nodeType === XsltForms_nodeType.TEXT) {
				var s = "";
				while (node && node.nodeType === XsltForms_nodeType.TEXT) {
					s += node.nodeValue;
					node = node.nextSibling;
				}
				return s;
			} else {
				var resultDocument = relevant ? XsltForms_browser.xsltProcessorRelevant.transformToDocument(node) : XsltForms_browser.xsltProcessor.transformToDocument(node);
				return XsltForms_browser.serializer.serializeToString(resultDocument);
			}
		}
	};
}
XsltForms_browser.unescape = function(xml) {
	if (!xml) {
		return "";
	}
	var regex_escapepb = /^\s*</;
	if (!xml.match(regex_escapepb)) {
		xml = xml.replace(/&lt;/g, "<");
		xml = xml.replace(/&gt;/g, ">");
		xml = xml.replace(/&amp;/g, "&");
	}
	return xml;
};
XsltForms_browser.escape = function(text) {
	if (!text) {
		return "";
	}
	if (typeof(text) === "string") {
		text = text.replace(/&/g, "&amp;");
		text = text.replace(/</g, "&lt;");
		text = text.replace(/>/g, "&gt;");
	}
	return text;
};

XsltForms_browser.getMeta = function(node, meta) {
	return node.nodeType === XsltForms_nodeType.ELEMENT ? node.getAttribute("xsltforms_"+meta) : node.ownerElement ? node.ownerElement.getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) : node.selectSingleNode("..").getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
};

XsltForms_browser.getBoolMeta = function(node, meta) {
	return Boolean(node.nodeType === XsltForms_nodeType.ELEMENT ? node.getAttribute("xsltforms_"+meta) : node.nodeType === XsltForms_nodeType.ATTRIBUTE ? node.ownerElement ? node.ownerElement.getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) :  node.selectSingleNode("..").getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) : false);
};

XsltForms_browser.getType = function(node) {
	if (node.nodeType === XsltForms_nodeType.ELEMENT) {
		var t = node.getAttribute("xsltforms_type");
		if (t && t !== "") {
			return t;
		}
		if (node.getAttributeNS) {
			return node.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type");
		} else {
			var att = node.selectSingleNode("@*[local-name()='type' and namespace-uri()='http://www.w3.org/2001/XMLSchema-instance']");
			if (att && att.value !== "") {
				return att.value;
			} else {
				return null;
			}
		}
	} else if (node.nodeType === XsltForms_nodeType.DOCUMENT) {
		return null;
	} else {
		if (node.ownerElement) {
			return node.ownerElement.getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type");
		} else {
			return node.selectSingleNode("..").getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type");
		}
	}
};

XsltForms_browser.getNil = function(node) {
	if (node.nodeType === XsltForms_nodeType.ELEMENT) {
		if (node.getAttributeNS) {
			return XsltForms_globals.booleanValue(node.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil"));
		} else {
			var att = node.selectSingleNode("@*[local-name()='nil' and namespace-uri()='http://www.w3.org/2001/XMLSchema-instance']");
			if (att && att.value !== "") {
				return XsltForms_globals.booleanValue(att.value);
			} else {
				return false;
			}
		}
	} else {
		return false;
	}
};

XsltForms_browser.setMeta = function(node, meta, value) {
	if (node) {
		if (node.nodeType === XsltForms_nodeType.ELEMENT) {
			node.setAttribute("xsltforms_"+meta, value);
		} else {
			if (node.ownerElement) {
				node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value);
			} else {
				node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value);
			}
		}
	}
};

XsltForms_browser.setBoolMeta = function(node, meta, value) {
	if (node) {
		if (value) {
			if (node.nodeType === XsltForms_nodeType.ELEMENT) {
				node.setAttribute("xsltforms_"+meta, value);
			} else {
				if (node.ownerElement) {
					node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value);
				} else {
					node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value);
				}
			}
		} else {
			if (node.nodeType === XsltForms_nodeType.ELEMENT) {
				node.removeAttribute("xsltforms_"+meta);
			} else {
				if (node.ownerElement) {
					node.ownerElement.removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
				} else {
					node.selectSingleNode("..").removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
				}
			}
		}
	}
};

XsltForms_browser.setTrueBoolMeta = function(node, meta) {
	if (node) {
		if (node.nodeType === XsltForms_nodeType.ELEMENT) {
			node.setAttribute("xsltforms_"+meta, true);
		} else {
			if (node.ownerElement) {
				node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, true);
			} else {
				node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, true);
			}
		}
	}
};

XsltForms_browser.setFalseBoolMeta = function(node, meta) {
	if (node) {
		if (node.nodeType === XsltForms_nodeType.ELEMENT) {
			node.removeAttribute("xsltforms_"+meta);
		} else {
			if (node.ownerElement) {
				node.ownerElement.removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
			} else {
				node.selectSingleNode("..").removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
			}
		}
	}
};

XsltForms_browser.rmValueMeta = function(node, meta, value) {
	if (node) {
		var prev = XsltForms_browser.getMeta(node, meta);
		if (!prev) {
			prev = "";
		}
		var v = " " + value + " ";
		var pos = prev.indexOf(v);
		if (pos !== -1) {
			XsltForms_browser.setMeta(node, meta, prev.substring(0, pos) + prev.substring(pos + v.length));
		}
	}
};

XsltForms_browser.addValueMeta = function(node, meta, value) {
	if (node) {
		var prev = XsltForms_browser.getMeta(node, meta);
		if (!prev) {
			prev = "";
		}
		var v = " " + value + " ";
		var pos = prev.indexOf(v);
		if (pos === -1) {
			XsltForms_browser.setMeta(node, meta, prev + v);
		}
	}
};

XsltForms_browser.inValueMeta = function(node, meta, value) {
	if (node) {
		var prev = XsltForms_browser.getMeta(node, meta) + "";
		var v = " " + value + " ";
		var pos = prev.indexOf(v);
		return pos !== -1;
	}
};

XsltForms_browser.setType = function(node, value) {
	if (node) {
		if (node.nodeType === XsltForms_nodeType.ELEMENT) {
			node.setAttribute("xsltforms_type", value);
		} else {
			if (node.ownerElement) {
				node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type", value);
			} else {
				node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type", value);
			}
		}
	}
};

if (!XsltForms_browser.isIE) {
	if (typeof XMLDocument === "undefined") {
		var XMLDocument = Document;
	}
	XMLDocument.prototype.selectNodes = function(path, single, node) {
		var r = this.evaluate(path, (node ? node : this), this.createNSResolver(this.documentElement), (single ? XPathResult.FIRST_ORDERED_NODE_TYPE : XPathResult.ORDERED_NODE_SNAPSHOT_TYPE), null);
		if (single) {
			return r.singleNodeValue ? r.singleNodeValue : null;
		}
		for (var i = 0, len = r.snapshotLength, r2 = []; i < len; i++) {
			r2.push(r.snapshotItem(i));
		}
		return r2;
	};
	XMLDocument.prototype.selectSingleNode = function(path) {
		return this.selectNodes(path, true);
	};
	XMLDocument.prototype.createNode = function(t, name, ns) {
		switch(t) {
			case XsltForms_nodeType.ELEMENT:
				return this.createElementNS(ns, name);
			case XsltForms_nodeType.ATTRIBUTE:
				return this.createAttributeNS(ns, name);
			default:
				return null;
		}
	};
	Element.prototype.selectNodes = function(path) {
		return this.ownerDocument.selectNodes(path, false, this);
	};
	Element.prototype.selectSingleNode = function(path) {	
		return this.ownerDocument.selectNodes(path, true, this);
	};
}

		

XsltForms_browser.debugConsole = {
	element_ : null,
	isInit_ : false,
	time_ : 0,
	init_ : function() {
		this.element_ = document.getElementById("xsltforms_console");
		this.isInit_ = true;
		this.time_ = new Date().getTime();
    },

		

    write : function(text) {
		try {
		if (this.isOpen()) {
			var time = new Date().getTime();
			this.element_.appendChild(document.createTextNode(time - this.time_ + " -> " + text));
			XsltForms_browser.createElement("br", this.element_);
			this.time_ = time;
		}
			} catch(e) {
			}
    },

		

	clear : function() {
		if (this.isOpen()) {
			while (this.element_.firstChild) {
				this.element_.removeChild(this.element_.firstChild);
			}
			this.time_ = new Date().getTime();
		}
	},

		

	isOpen : function() {
		if (!this.isInit_) {
			this.init_();
		}
		return this.element_;
	}
};


		

XsltForms_browser.dialog = {
	openPosition: {},
	dialogs : [],
	init : false,
	initzindex : 50,
	zindex: 0,
	selectstack : [],

		

	dialogDiv : function(id) {
		var div = null;
		if (typeof id !== "string") {
			var divid = id.getAttribute("id");
			if (divid && divid !== "") {
				div = XsltForms_idManager.find(divid);
			} else {
				div = id;
			}
		} else {
			div = XsltForms_idManager.find(id);
		}
		if (!div) {
			XsltForms_browser.debugConsole.write("Unknown dialog("+id+")!");
		}
		return div;
		},

		

	show : function(div, parent, modal) {
			if (!(div = this.dialogDiv(div))) {
				return;
			}
			// Don't reopen the top-dialog.
			if (this.dialogs[this.dialogs.length - 1] === div) {
				return;
			}
			// Maintain dialogs-array ordered.
			this.dialogs = XsltForms_browser.removeArrayItem(this.dialogs, div);
			this.dialogs.push(div);
			var size;
			if (modal) {
				var surround = document.getElementById("xforms-dialog-surround");
				surround.style.display = "block";
				surround.style.zIndex = (this.zindex + this.initzindex)*2;
				this.zindex++;
				size = XsltForms_browser.getWindowSize();
				surround.style.height = size.height+"px";
				surround.style.width = size.width+"px";
				surround.style.top = size.scrollY+"px";
				surround.style.left = size.scrollX+"px";
				var surroundresize = function () {
					var surround = document.getElementById("xforms-dialog-surround");
					var size = XsltForms_browser.getWindowSize();
					surround.style.height = size.height+"px";
					surround.style.width = size.width+"px";
					surround.style.top = size.scrollY+"px";
					surround.style.left = size.scrollX+"px";
				};
				window.onscroll = surroundresize;
				window.onresize = surroundresize;
			}
			div.style.display = "block";
			div.style.zIndex = (this.zindex + this.initzindex)*2-1;
			this.showSelects(div, false, modal);
			if (parent) {
				var absPos = XsltForms_browser.getAbsolutePos(parent);
				XsltForms_browser.setPos(div, absPos.x, (absPos.y + parent.offsetHeight));
			} else {
				size = XsltForms_browser.getWindowSize();
				var h = size.scrollY + (size.height - div.offsetHeight) / 2;
				XsltForms_browser.setPos(div, (size.width - div.offsetWidth) / 2, h > 0 ? h : 100);
			}
		},

		

	hide : function(div, modal) {
		if (!(div = this.dialogDiv(div))) {
			return;
		}
		var oldlen = this.dialogs.length;
		this.dialogs = XsltForms_browser.removeArrayItem(this.dialogs, div);
		if (this.dialogs.length === oldlen) {
			return;
		}
		this.showSelects(div, true, modal);
		div.style.display = "none";
		if (modal) {
			if (!this.dialogs.length) {
				this.zindex = 0;
				document.getElementById('xforms-dialog-surround').style.display = "none";
				window.onscroll = null;
				window.onresize = null;
			} else {
				this.zindex--;
				document.getElementById('xforms-dialog-surround').style.zIndex = (this.zindex + this.initzindex)*2-2;
				// Ensure new top-dialog over modal-surround.
				if (this.dialogs.length) {
					this.dialogs[this.dialogs.length - 1].style.zIndex = (this.zindex + this.initzindex)*2-1;
				}
			}
		}
	},

		

	knownSelect : function(s) {
		if (XsltForms_browser.isIE6) {
			for (var i = 0, len = this.zindex; i < len; i++) {
				for (var j = 0, len1 = this.selectstack[i].length; j < len1; j++) {
					if (this.selectstack[i][j].select === s) {
						return true;
					}
				}
			}
		}
		return false;
	},

		

	showSelects : function(div, value, modal) {
		if (XsltForms_browser.isIE6) {
			var selects = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "select") : document.getElementsByTagName("select");
			var pos = XsltForms_browser.getAbsolutePos(div);
			var w = div.offsetWidth;
			var h = div.offsetHeight;
			var dis = [];
			for (var i = 0, len = selects.length; i < len; i++) {
				var s = selects[i];
				var p = s.parentNode;
				while (p && p !== div) {
					p = p.parentNode;
				}
				if (p !== div) {
					var ps = XsltForms_browser.getAbsolutePos(s);
					var ws = s.offsetWidth;
					var hs = s.offsetHeight;
					var under = ps.x + ws > pos.x && ps.x < pos.x + w && ps.y + hs > pos.y && ps.y < pos.y + h;
					if (modal) {
						if (value) {
							dis = this.selectstack[this.zindex];
							for (var j = 0, len1 = dis.length; j < len1; j++) {
								if (dis[j].select === s) {
									s.disabled = dis[j].disabled;
									s.style.visibility = dis[j].visibility;
									break;
								}
							}
						} else {
							var d = {"select": s, "disabled": s.disabled, "visibility": s.style.visibility};
							dis[dis.length] = d;
							if (under) {
								s.style.visibility = "hidden";
							} else {
								s.disabled = true;
							}
						}
					} else {
							if (under) {
								s.style.visibility = value? "" : "hidden";
							}
					}
				}
			}
			if (modal && !value) {
				this.selectstack[this.zindex - 1] = dis;
			}
		}
	}
};


		

XsltForms_browser.events = {
	cache :null,
	add_ : function() {
		if (!XsltForms_browser.events.cache) {
			XsltForms_browser.events.cache = [];
			XsltForms_browser.events.attach(window, "unload", XsltForms_browser.events.flush_);
		}
		XsltForms_browser.events.cache.push(arguments);
	},
	flush_ : function() {
		if (!XsltForms_browser.events.cache) {
			return;
		}
		for (var i = XsltForms_browser.events.cache.length - 1; i >= 0; i--) {
			var item = XsltForms_browser.events.cache[i];
			XsltForms_browser.events.detach(item[0], item[1], item[2], item[3]);
		}
		if (XsltForms_browser.events.onunload) {
			XsltForms_browser.events.onunload();
		}
		XsltForms_browser.events.onunload = null;
	},
	onunload : null
};

if (XsltForms_browser.isIE && !XsltForms_browser.isIE9) {
	XsltForms_browser.events.attach = function(target, name, handler, phase) {
		var func = function(evt) { 
			handler.call(window.event.srcElement, evt);
		};
		target.attachEvent("on" + name, func);
		this.add_(target, name, func, phase);
	};

	XsltForms_browser.events.detach = function(target, name, handler, phase) {
		target.detachEvent("on" + name, handler);
	};

	XsltForms_browser.events.getTarget = function() {
		return window.event.srcElement;
	};
    
	XsltForms_browser.events.dispatch = function(target, name) {
		target.fireEvent("on" + name, document.createEventObject());
	};
} else {
	XsltForms_browser.events.attach = function(target, name, handler, phase) {
		if (target === window && !window.addEventListener) {
			target = document;
		}
		target.addEventListener(name, handler, phase);
		this.add_(target, name, handler, phase);
	};
    
	XsltForms_browser.events.detach = function(target, name, handler, phase) {
		if (target === window && !window.addEventListener) {
			target = document;
		}
		target.removeEventListener(name, handler, phase);
	};

	XsltForms_browser.events.getTarget = function(ev) {
		return ev.target;
	};
    
	XsltForms_browser.events.dispatch = function(target, name) {
		var event = document.createEvent("Event");
		event.initEvent(name, true, true);
		target.dispatchEvent(event);
	};
}


		

XsltForms_browser.i18n = {
	messages : null,
	lang : null,
	langs : ["cz", "de", "el", "en", "en_us", "es", "fr" , "gl", "ko", "it", "ja", "nb_no", "nl", "nn_no", "pt", "ro", "ru", "si", "sk", "zh", "zh_cn", "zh_tw"],

		

	get : function(key) {
		if (!XsltForms_browser.config) {
			return "Initializing";
		}
		if (XsltForms_globals.language === "navigator" || XsltForms_globals.language !== XsltForms_browser.selectSingleNodeText('language', XsltForms_browser.config)) {
			var lan = XsltForms_globals.language === "navigator" ? (navigator.language || navigator.userLanguage) : XsltForms_browser.selectSingleNodeText('language', XsltForms_browser.config);
			lan = lan.replace("-", "_").toLowerCase();
			var found = XsltForms_browser.inArray(lan, XsltForms_browser.i18n.langs);
			if (!found) {
				var ind = lan.indexOf("_");
				if (ind !== -1) {
					lan = lan.substring(0, ind);
				}
				found = XsltForms_browser.inArray(lan, XsltForms_browser.i18n.langs);
			}
			if (found) {
				XsltForms_browser.loadProperties("config_" + lan + ".xsl");
				XsltForms_globals.language = XsltForms_browser.selectSingleNodeText('language', XsltForms_browser.config);
			} else {
				XsltForms_globals.language = "default";
			}
		}
		return XsltForms_browser.selectSingleNodeText(key, XsltForms_browser.config);
    },

		

	parse : function(str, pattern) {
		if (!str || str.match("^\\s*$")) {
			return null;
		}
		if (!pattern) {
			pattern = XsltForms_browser.i18n.get("format.datetime");
		}
		var d = new Date(2000, 0, 1);
		XsltForms_browser.i18n._parse(d, "Year", str, pattern, "yyyy");
		XsltForms_browser.i18n._parse(d, "Month", str, pattern, "MM");
		XsltForms_browser.i18n._parse(d, "Date", str, pattern, "dd");
		XsltForms_browser.i18n._parse(d, "Hours", str, pattern, "hh");
		XsltForms_browser.i18n._parse(d, "Minutes", str, pattern, "mm");
		XsltForms_browser.i18n._parse(d, "Seconds", str, pattern, "ss");
		return d;
	},

		

	format : function(date, pattern, loc) {
		if (!date) {
			return "";
		}
		if (!pattern) {
			pattern = XsltForms_browser.i18n.get("format.datetime");
		}
		var str = XsltForms_browser.i18n._format(pattern, (loc ? date.getDate() : date.getUTCDate()), "dd");
		str = XsltForms_browser.i18n._format(str, (loc ? date.getMonth() : date.getUTCMonth()) + 1, "MM");
		var y = (loc ? date.getFullYear() : date.getUTCFullYear());
		str = XsltForms_browser.i18n._format(str, y < 1000? 1900 + y : y, "yyyy");
		str = XsltForms_browser.i18n._format(str, (loc ? date.getSeconds() : date.getUTCSeconds()), "ss");
		str = XsltForms_browser.i18n._format(str, (loc ? date.getMinutes() : date.getUTCMinutes()), "mm");
		str = XsltForms_browser.i18n._format(str, (loc ? date.getHours() : date.getUTCHours()), "hh");
		var o = date.getTimezoneOffset();
		str = XsltForms_browser.i18n._format(str, (loc ? (o < 0 ? "+" : "-") + XsltForms_browser.zeros(Math.floor(Math.abs(o)/60),2) + ":" + XsltForms_browser.zeros(Math.abs(o) % 60,2) : "Z"), "z");
		return str;
	},

		

	parseDate : function(str) {
		return XsltForms_browser.i18n.parse(str, XsltForms_browser.i18n.get("format.date"));
	},

		

	formatDate : function(str) {
		return XsltForms_browser.i18n.format(str, XsltForms_browser.i18n.get("format.date"), true);
	},
 
		

	formatNumber : function(number, decimals) {
		if (isNaN(number)) {
			return number;
		}
		var value = "" + number;
		var index = value.indexOf(".");
		var integer = parseInt(index !== -1? value.substring(0, index) : value, 10);
		var decimal = index !== -1? value.substring(index + 1) : "";
		var decsep = XsltForms_browser.i18n.get("format.decimal");
		return integer + (decimals > 0? decsep + XsltForms_browser.zeros(decimal, decimals, true) : (decimal? decsep + decimal : ""));
	},

		

	parseNumber : function(value) {
		var decsep = XsltForms_browser.i18n.get("format.decimal");
		if(!value.match("^[\\-+]?([0-9]+(\\" + decsep + "[0-9]*)?|\\" + decsep + "[0-9]+)$")) {
			throw "Invalid number " + value;
		}
		var index = value.indexOf(decsep);
		var integer = parseInt(index !== -1? value.substring(0, index) : value, 10);
		var decimal = index !== -1? value.substring(index + 1) : null;
		return integer + (decimal? "." + decimal : "");
	},
	_format : function(returnValue, value, el) {
		return returnValue.replace(el, XsltForms_browser.zeros(value, el.length));
	},
	_parse : function(date, prop, str, format, el) {
		var index = format.indexOf(el);
		if (index !== -1) {
			format = format.replace(new RegExp("\\.", "g"), "\\.");
			format = format.replace(new RegExp("\\(", "g"), "\\(");
			format = format.replace(new RegExp("\\)", "g"), "\\)");
			format = format.replace(new RegExp(el), "(.*)");
			format = format.replace(new RegExp("yyyy"), ".*");
			format = format.replace(new RegExp("MM"), ".*");
			format = format.replace(new RegExp("dd"), ".*");
			format = format.replace(new RegExp("hh"), ".*");
			format = format.replace(new RegExp("mm"), ".*");
			format = format.replace(new RegExp("ss"), ".*");
			var val = str.replace(new RegExp(format), "$1");
			if (val.charAt(0) === '0') {
				val = val.substring(1);
			}
			val = parseInt(val, 10);
			if (isNaN(val)) {
				throw "Error parsing date " + str + " with pattern " + format;
			}
			var n = new Date();
			n = n.getFullYear() - 2000;
			date["set" + prop](prop === "Month"? val - 1 : (prop === "Year" && val <= n+10 ? val+2000 : val));
		}
	}
};

XsltForms_numberList = function(parent, className, input, min, max, minlengh) {
	this.element = XsltForms_browser.createElement("ul", parent, null, className);
	this.move = 0;
	this.input = input;
	this.min = min;
	this.max = max;
	this.minlength = minlengh || 1;
	var list = this;
	this.createChild("+", function() { list.start(1); }, function() { list.stop(); } );
	for (var i = 0; i < 7; i++) {
		this.createChild(" ", function(event) {
			list.input.value = XsltForms_browser.events.getTarget(event).childNodes[0].nodeValue;
			list.close();
			XsltForms_browser.events.dispatch(list.input, "change");
		} );
	}
	this.createChild("-", function() { list.start(-1); }, function() { list.stop(); } );
};

XsltForms_numberList.prototype.show = function() {
	var input = this.input;
	this.current = parseInt(input.value, 10);
	this.refresh();
	XsltForms_browser.dialog.show(this.element, input, false);
};

XsltForms_numberList.prototype.close = function() {
	XsltForms_browser.dialog.hide(this.element, false);
}; 

XsltForms_numberList.prototype.createChild = function(content, handler, handler2) {
	var child = XsltForms_browser.createElement("li", this.element, content);
	XsltForms_browser.initHover(child);
	if (handler2) {
		XsltForms_browser.events.attach(child, "mousedown", handler);
		XsltForms_browser.events.attach(child, "mouseup", handler2);
	} else {
		XsltForms_browser.events.attach(child, "click", handler);
	}
};

XsltForms_numberList.prototype.refresh = function()  {
	var childs = this.element.childNodes;
	var cur = this.current;
	if (cur >= this.max - 3) {
		cur = this.max - 3;
	} else if (cur <= this.min + 3) {
		cur = this.min + 3;
	}
	var top = cur + 4;
	for (var i = 1; i < 8; i++) {
		XsltForms_browser.setClass(childs[i], "hover", false);
		var str = (top - i) + "";
		while (str.length < this.minlength) {
			str = '0' + str;
		}
		childs[i].firstChild.nodeValue = str;
	}
};

XsltForms_numberList.prototype.start = function(value) {
	this.move = value;
	XsltForms_numberList.current = this;
	this.run();
};
    
XsltForms_numberList.prototype.stop = function() {
	this.move = 0;
};

XsltForms_numberList.prototype.run = function() {
	if ((this.move > 0 && this.current + 3 < this.max) || (this.move < 0 && this.current - 3> this.min)) {
		this.current += this.move;
		this.refresh();
		var list = this;
		setTimeout(XsltForms_numberList.current.run, 60);
	}
};

XsltForms_numberList.current = null;


		

XsltForms_browser.forEach = function(object, block) {
	var args = [];
	for (var i = 0, len = arguments.length - 2; i < len; i++) {
		args[i] = arguments[i + 2];
	}
	if (object) {
		if (typeof object.length === "number") {
			for (var j = 0, len1 = object.length; j < len1; j++) {
				var obj = object[j];
				var func = typeof block === "string" ? obj[block] : block;
				func.apply(obj, args);
			}
		} else {
			for (var key in object) {
				if (object.hasOwnProperty(key)) {
					var obj2 = object[key];
					var func2 = typeof block === "string" ? obj2[block] : block;
					func2.apply(obj2, args);
				}
			}   
		}
	}
};


		

XsltForms_browser.assert = function(condition, message) {
	if (!condition && XsltForms_browser.debugConsole.isOpen()) {
		if (!XsltForms_globals.debugMode) {
			XsltForms_globals.debugMode = true;
			XsltForms_globals.debugging();
		}
		XsltForms_browser.debugConsole.write("Assertion failed: " + message);
		var callstack = null;
		if (arguments.caller) { // Internet Explorer
			this.callstack = [];
			for (var caller = arguments.caller; caller; caller = caller.caller) {
				this.callstack.push(caller.name ? caller.name : "<anonymous>");
			}
		} else {
			try {
				var x; x.y;
			} catch (exception) {
				this.callstack = exception.stack.split("\n");
			}
		}
		if (this.callstack) {
			for (var i = 0, len = this.callstack.length; i < len; i++) {
				XsltForms_browser.debugConsole.write("> " + this.callstack[i]);
			}
		}
		throw message;
	}
};


		

XsltForms_browser.inArray = function(value, array) {
	for (var i = 0, len = array.length; i < len; i++) {
		if (value === array[i]) {
			return true;
		}
	}
	return false;
};


		

XsltForms_browser.zeros = function(value, length, right) {
	var res = "" + value;
	if (right) {
		while (res.length < length) {
			res = res + "0";
		}
	} else {
		while (res.length < length) {
			res = "0" + res;
		}
	}
	return res;
};

		
		
XsltForms_browser.getValue = function(node, format, serialize) {
	XsltForms_browser.assert(node);
	if (serialize) {
		return node.nodeType === XsltForms_nodeType.ATTRIBUTE ? node.nodeValue : XsltForms_browser.saveXML(node);
	}
	var value = node.text !== undefined ? node.text : node.textContent;
	if (value && format) {
		var schtyp = XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string");
		if (schtyp.format) {
			try { value = schtyp.format(value); } catch(e) { }
		}
	}
	return value;
};


		

XsltForms_browser.setValue = function(node, value) {
	XsltForms_browser.assert(node);
	if (node.nodeType === XsltForms_nodeType.ATTRIBUTE) {
		node.nodeValue = value;
	} else if (XsltForms_browser.isIE && node.innerHTML) {
		node.innerHTML = XsltForms_browser.escape(value);
	} else {
		while (node.firstChild) {
			node.removeChild(node.firstChild);
		}
		if (value) {
			for (var i = 0, l = value.length; i < l; i += 4096) {
				node.appendChild(node.ownerDocument.createTextNode(value.substr(i, 4096)));
			}
		}
	}
};


		

XsltForms_browser.run = function(action, element, evt, synch, propagate) {
	if (synch) {
		XsltForms_browser.dialog.show("statusPanel", null, false);
		setTimeout(function() { 
			XsltForms_globals.openAction();
			action.execute(XsltForms_idManager.find(element), null, evt);
			XsltForms_browser.dialog.hide("statusPanel", false);
			if (!propagate) {
				evt.stopPropagation();
			}
			XsltForms_globals.closeAction();
		}, 1 );
	} else {
		XsltForms_globals.openAction();
		action.execute(XsltForms_idManager.find(element), null, evt);
		if (!propagate) {
			evt.stopPropagation();
		}
		XsltForms_globals.closeAction();
	}
};


		

XsltForms_browser.getId = function(element) {
	if(element.id) {
		return element.id;
	} else {
		return element.parentNode.parentNode.parentNode.parentNode.id;
	}
};


		

XsltForms_browser.show = function(el, type, value) {
	el.parentNode.lastChild.style.display = value? 'inline' : 'none';
};


		

XsltForms_browser.copyArray = function(source, dest) {
	if( dest ) {
		for (var i = 0, len = source.length; i < len; i++) {
			dest[i] = source[i];
		}
	}
};


		

XsltForms_browser.removeArrayItem = function(array, item) {
	var narr = [];
	for (var i = 0, len = array.length; i < len; i++) {
		if (array[i] !== item ) {
			narr.push(array[i]);
		}
	}
	return narr;
};


		

String.prototype.trim = function() {
	return this.replace(/^\s+|\s+$/, '');
};


		

String.prototype.addslashes = function() {
	return this.replace(/\\/g,"\\\\").replace(/\'/g,"\\'").replace(/\"/g,"\\\"");
};


	
		
		
		
		
function XsltForms_binding(type, xpath, model, bind) {
	this.type = type;
	this.bind = bind? bind : null;
	this.xpath = xpath? XsltForms_xpath.get(xpath) : null;
	var modelelt = model;
	if( typeof model === "string" ) {
		modelelt = document.getElementById(model);
	}
	this.model = model? (modelelt ? modelelt.xfElement : model) : null;
	this.result = null;
}


		

XsltForms_binding.prototype.evaluate = function(ctx, depsNodes, depsId, depsElements) {
	var result = null;
	if (this.bind) {
		if (typeof this.bind === "string") {
			var idel = document.getElementById(this.bind);
			if (!idel) {
				XsltForms_browser.debugConsole.write("Binding evaluation returned null for bind: " + this.bind); 
				return null;	// A 'null' signifies bind-ID not found.
			}
			this.bind = idel.xfElement;
		}
		result = this.bind.nodes;
		XsltForms_browser.copyArray(this.bind.depsNodes, depsNodes);
		XsltForms_browser.copyArray(this.bind.depsElements, depsElements);
	} else {
		var exprCtx = new XsltForms_exprContext(!ctx || (this.model && this.model !== document.getElementById(XsltForms_browser.getMeta(ctx.ownerDocument.documentElement, "model")).xfElement) ? this.model ? this.model.getInstanceDocument().documentElement : XsltForms_globals.defaultModel.getInstanceDocument().documentElement : ctx,
			null, null, null, null, ctx, depsNodes, depsId, depsElements);
		result = this.xpath.evaluate(exprCtx);
	}
	XsltForms_browser.assert(this.type || !result || typeof result === "object", "Binding evaluation didn't returned a nodeset but '"+(typeof result === "object" ? "" : result)+"' for " + (this.bind ? "bind: " + this.bind : "XPath expression: " + this.xpath.expression));
	switch (this.type) {
		case "xsd:string": 
			result = XsltForms_globals.stringValue(result);
			break;
		case "xsd:boolean":
			result = XsltForms_globals.booleanValue(result);
			break;
	}
	this.result = result;
	return result;
};

	
		
		
		
function XsltForms_mipbinding(type, xpath, model) {
	this.binding = new XsltForms_binding(type, xpath, model, null);
	this.nodes = [];
	this.depsElements = [];
	this.depsNodes = [];
}


		

XsltForms_mipbinding.prototype.evaluate = function(ctx, node) {
	var deps = null;
	var depsN = null;
	var curn = this.nodes.length;
	for (var i0 = 0, len0 = this.nodes.length; i0 < len0; i0++ ) {
		if (node === this.nodes[i0].node) {
			deps = this.nodes[i0].deps;
			depsN = this.nodes[i0].depsN;
			curn = i0;
			break;
		}
	}
	if (!deps && !depsN) {
		this.nodes.push({node: node, deps: [], depsN: []});
		deps = depsN = [];
	}
	var build = !XsltForms_globals.ready || (deps.length === 0);
	var changes = XsltForms_globals.changes;
	for (var i1 = 0, len1 = depsN.length; !build && i1 < len1; i1++) {
		build = depsN[i1].nodeName === "";
	}
	for (var i = 0, len = deps.length; !build && i < len; i++) {
		var el = deps[i];
		for (var j = 0, len2 = changes.length; !build && j < len2; j++) {
			if (el === changes[j]) {
				if (el.instances) { //model
					if (el.rebuilded || el.newRebuilded) {
						build = true;
					} else {
						for (var k = 0, len3 = depsN.length; !build && k < len3; k++) {
							build = XsltForms_browser.inArray(depsN[k], el.nodesChanged);
						}
					}
				} else {
					build = true;
				}
			}
		}
	}
	if (build) {
		// alert("Evaluate \"" + this.binding.xpath.expression + "\"");
		depsN.length = 0;
		deps.length = 0;
		this.nodes[curn].result = this.binding.evaluate(ctx.node, this.nodes[curn].depsN, null, this.nodes[curn].deps);
		return this.nodes[curn].result;
	} else {
		return this.nodes[curn].result;
	}
};

		

XsltForms_mipbinding.prototype.nodedispose_ = function(node) {
	for (var i = 0, len = this.nodes.length; i < len; i++ ) {
		if (node === this.nodes[i].node) {
			this.nodes[i] = this.nodes[len-1];
			this.nodes.pop();
			break;
		}
	}
};

XsltForms_mipbinding.nodedispose = function(node) {
	var bindids = XsltForms_browser.getMeta(node, "bind");
	if (bindids) {
		var binds = bindids.split(" ");
		for (var j = 0, len2 = binds.length; j < len2; j++) {
			var bind = document.getElementById(binds[j]).xfElement;
			if (bind.required) {
				bind.required.nodedispose_(node);
			}
			if (bind.relevant) {
				bind.relevant.nodedispose_(node);
			}
			if (bind.readonly) {
				bind.readonly.nodedispose_(node);
			}
			if (bind.constraint) {
				bind.constraint.nodedispose_(node);
			}
			if (bind.calculate) {
				bind.calculate.nodedispose_(node);
			}
		}
	}
	for (var n = node.firstChild; n; n = n.nextSibling) {
		if (n.nodeType === XsltForms_nodeType.ELEMENT) {
			XsltForms_mipbinding.nodedispose(n);
		}
	}
};

	
		
		
		
var XsltForms_idManager = {
	cloneId : function(element) {
		XsltForms_browser.assert(element && element.id);
		var id = element.getAttribute("oldid") || element.id;
		var list = this.data[id];
		if (!list) {
			list = [];
			this.data[id] = list;
		}
		var newId = "clonedId" + this.index++;
		list.push(newId);
		element.setAttribute("oldid", id);
		element.id = newId;
	},
    find : function(id) {
		var ids = this.data[id];
		if (ids) {
			for (var i = 0, len = ids.length; i < len; i++) {
				var element = document.getElementById(ids[i]);
				if (element) {
					var parent = element.parentNode;
					while (parent.nodeType === XsltForms_nodeType.ELEMENT) {
						if (XsltForms_browser.hasClass(parent, "xforms-repeat-item")) {
							if (XsltForms_browser.hasClass(parent, "xforms-repeat-item-selected")) {
								return element;
							} else {
								break;
							}
						}
						parent = parent.parentNode;
					}
				}
			}
		}
		var res = document.getElementById(id);
		//if (!res) {
			//alert("element " + id + " not found");
		//}
		return res;
	},
	clear : function() {
		for (var i = 0, len = this.data.length; i < len; i++) {
			this.data[i] = null;
		}
		this.data = [];
		this.index = 0;
	},
	data : [],
	index : 0
};

	
	
		
		
		
		
		
function XsltForms_coreElement() {
}


		

XsltForms_coreElement.prototype.init = function(subform, id, parent, className) {
	this.subforms = [];
	this.subforms[subform] = true;
	this.nbsubforms = 1;
	parent = parent? parent.element : XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "head")[0]: document.getElementsByTagName("head")[0];
	this.element = XsltForms_browser.createElement("span", parent, null, className);
	this.element.id = id;
	this.element.xfElement = this;
};


		

XsltForms_coreElement.prototype.dispose = function() {
	this.element.xfElement = null;
	this.element.parentNode.removeChild(this.element);
	this.element = null;
	this.model = null;
};

	
		
		
		
function XsltForms_model(subform, id, schemas) {
	if (subform !== "xsltforms-mainform") {
		XsltForms_globals.addChange(this);
	}
	this.init(subform, id, null, "xforms-model");
	this.instances = {};
	this.binds = [];
	this.nodesChanged = [];
	this.newNodesChanged = [];
	this.schemas = [];
	this.defaultInstance = null;
	this.defaultSubmission = null;
	XsltForms_globals.models.push(this);
	subform.models.push(this);
	elt = document.getElementById(id);
	XsltForms_globals.defaultModel = XsltForms_globals.defaultModel || this;
	if (elt) {
		elt.getInstanceDocument = function(modid) {
			return this.xfElement.getInstanceDocument(modid);
		};
		elt.rebuild = function() {
			return this.xfElement.rebuild();
		};
		elt.recalculate = function() {
			return this.xfElement.recalculate();
		};
		elt.revalidate = function() {
			return this.xfElement.revalidate();
		};
		elt.refresh = function() {
			return this.xfElement.refresh();
		};
		elt.reset = function() {
			return this.xfElement.reset();
		};
	}
	if (schemas) {
		schemas = schemas.split(" ");
		for (var i = 0, len = schemas.length; i < len; i++) {
			var found = false;
			for (var sid in XsltForms_schema.all) {
				if (XsltForms_schema.all.hasOwnProperty(sid)) {
					var schema = XsltForms_schema.all[sid];
					if (schema.name === schemas[i]) {
						this.schemas.push(schema);
						found = true;
						break;
					}
				}
			}
			if (!found) {
				XsltForms_globals.error(this, "xforms-link-exception", "Schema " + schemas[i] + " not found");
			}
		}
	}
}

XsltForms_model.prototype = new XsltForms_coreElement();


		

XsltForms_model.create = function(subform, id, schemas) {
	var elt = document.getElementById(id);
	if (elt) {
		elt.xfElement.subforms[subform] = true;
		elt.xfElement.nbsubforms++;
		subform.models.push(elt.xfElement);
		XsltForms_globals.addChange(elt.xfElement);
		return elt.xfElement;
	} else {
		return new XsltForms_model(subform, id, schemas);
	}
};


		

XsltForms_model.prototype.addInstance = function(instance) {
	this.instances[instance.element.id] = instance;
	this.defaultInstance = this.defaultInstance || instance;
};

		

XsltForms_model.prototype.addBind = function(bind) {
	this.binds.push(bind);
};

		

XsltForms_model.prototype.dispose = function(subform) {
	if (subform && this.nbsubforms !== 1) {
		this.subforms[subform] = null;
		this.nbsubforms--;
		return;
	}
	this.instances = null;
	this.binds = null;
	this.defaultInstance = null;
	XsltForms_coreElement.prototype.dispose.call(this);
};

		

XsltForms_model.prototype.getInstance = function(id) {
	return id ? this.instances[id] : this.defaultInstance;
};

		

XsltForms_model.prototype.getInstanceDocument = function(id) {
	var instance = this.getInstance(id);
	return instance? instance.doc : null;
};

		

XsltForms_model.prototype.findInstance = function(node) {
	var doc = node.nodeType === XsltForms_nodeType.DOCUMENT ? node : node.ownerDocument;
	for (var id in this.instances) {
		if (this.instances.hasOwnProperty(id)) {
			var inst = this.instances[id];
			if (doc === inst.doc) {
				return inst;
			}
		}
	}
	return null;
};


		

XsltForms_model.prototype.construct = function() {
	if (!XsltForms_globals.ready) {
		XsltForms_browser.forEach(this.instances, "construct");
	}
	XsltForms_xmlevents.dispatch(this, "xforms-rebuild");
	XsltForms_xmlevents.dispatch(this, "xforms-model-construct-done");
	if (this === XsltForms_globals.models[XsltForms_globals.models.length - 1]) {
		window.setTimeout("XsltForms_xmlevents.dispatchList(XsltForms_globals.models, \"xforms-ready\")", 1);
	}
};


		

XsltForms_model.prototype.reset = function() {
	XsltForms_browser.forEach(this.instances, "reset");
	this.setRebuilded(true);
	XsltForms_globals.addChange(this);
};


		

XsltForms_model.prototype.rebuild = function() {
	if (XsltForms_globals.ready) {
		this.setRebuilded(true);
	}
	XsltForms_browser.forEach(this.binds, "refresh");
	XsltForms_xmlevents.dispatch(this, "xforms-recalculate");
};


		

XsltForms_model.prototype.recalculate = function() { 
	XsltForms_browser.forEach(this.binds, "recalculate");
	XsltForms_xmlevents.dispatch(this, "xforms-revalidate");
};


		

XsltForms_model.prototype.revalidate = function() {
	XsltForms_browser.forEach(this.instances, "revalidate");
	if (XsltForms_globals.ready) {
		XsltForms_xmlevents.dispatch(this, "xforms-refresh");
	}
};


		

XsltForms_model.prototype.refresh = function() {
	// Nada?
};


		

XsltForms_model.prototype.addChange = function(node) {
	var list = XsltForms_globals.building? this.newNodesChanged : this.nodesChanged;
	if (!XsltForms_browser.inArray(node, list)) {
		XsltForms_globals.addChange(this);
	}
	if (node.nodeType === XsltForms_nodeType.ATTRIBUTE && !XsltForms_browser.inArray(node, list)) {
		list.push(node);
		node = node.ownerElement ? node.ownerElement : node.selectSingleNode("..");
	}
	while (node.nodeType === XsltForms_nodeType.ELEMENT && !XsltForms_browser.inArray(node, list)) {
		list.push(node);
		node = node.parentNode;
	}
};


		

XsltForms_model.prototype.setRebuilded = function(value) {
	if (XsltForms_globals.building) {
		this.newRebuilded = value;
	} else {
		this.rebuilded = value;
	}
};

	
		
		
		
function XsltForms_instance(subform, id, model, readonly, mediatype, src, srcXML) {
	this.init(subform, id, model, "xforms-instance");
	this.readonly = readonly;
	this.mediatype = mediatype;
	this.src = XsltForms_browser.unescape(src);
	switch(mediatype) {
		case "application/xml":
			this.srcXML = XsltForms_browser.unescape(srcXML);
			if (this.srcXML.substring(0, 1) === "&") {
				this.srcXML = XsltForms_browser.unescape(this.srcXML);
			}
			break;
		case "application/json":
			var json;
			eval("json = " + XsltForms_browser.unescape(srcXML));
			this.srcXML = XsltForms_browser.json2xml("", json, true, false);
			break;
		default:
			alert("Unsupported mediatype '" + mediatype + "' for instance #" + id);
			return;
	}
	this.model = model;
	this.doc = XsltForms_browser.createXMLDocument("<dummy/>");
	XsltForms_browser.setMeta(this.doc.documentElement, "instance", id);
	XsltForms_browser.setMeta(this.doc.documentElement, "model", model.element.id);
	model.addInstance(this);
	subform.instances.push(this);
}

XsltForms_instance.prototype = new XsltForms_coreElement();
 

		

XsltForms_instance.create = function(subform, id, model, readonly, mediatype, src, srcXML) {
	var instelt = document.getElementById(id);
	if (instelt) {
		instelt.xfElement.subforms[subform] = true;
		instelt.xfElement.nbsubforms++;
		subform.instances.push(instelt.xfElement);
		return instelt.xfElement;
	} else {
		return new XsltForms_instance(subform, id, model, readonly, mediatype, src, srcXML);
	}
};

		

XsltForms_instance.prototype.dispose = function(subform) {
	if (subform && this.nbsubforms !== 1) {
		this.subforms[subform] = null;
		this.nbsubforms--;
		return;
	}
	XsltForms_coreElement.prototype.dispose.call(this);
};


		

XsltForms_instance.prototype.construct = function(subform) {
	if (!XsltForms_globals.ready || (subform && !subform.ready && this.nbsubforms === 1)) {
		if (this.src) {
			if (this.src.substring(0, 8) === "local://") {
				try {
					if (typeof(localStorage) === 'undefined') {
						throw { message: "local:// not supported" };
					}
					this.setDoc(window.localStorage.getItem(this.src.substr(8)));
				} catch(e) {
					XsltForms_globals.error(this.element, "xforms-link-exception", "Fatal error loading " + this.src, e.toString());
				}
			} else {
				if (this.src.substr(0, 11) === "javascript:") {
					try {
						var ser;
						eval("ser = (" + this.src.substr(11) + ");");
						this.setDoc(ser);
					} catch (e) {
						XsltForms_globals.error(this.element, "xforms-link-exception", "Error evaluating the following Javascript expression: "+this.src.substr(11));
					}
				} else {
					var cross = false;
					if (this.src.match(/^[a-zA-Z0-9+\.\-]+:\/\//)) {
						var domain = /^([a-zA-Z0-9+\.\-]+:\/\/[^\/]*)/;
						var sdom = domain.exec(this.src);
						var ldom = domain.exec(document.location.href);
						cross = sdom[0] !== ldom[0];
					}
					if (cross) {
						this.setDoc('<dummy xmlns=""/>');
						XsltForms_browser.jsoninstobj = this;
						var scriptelt = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
						scriptelt.setAttribute("src", this.src+((this.src.indexOf("?") === -1) ? "?" : "&")+"callback=XsltForms_browser.jsoninst");
						scriptelt.setAttribute("id", "jsoninst");
						scriptelt.setAttribute("type", "text/javascript");
						var body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
						body.insertBefore(scriptelt, body.firstChild);
					} else {
						try {
							var req = XsltForms_browser.openRequest("GET", this.src, false);
							XsltForms_browser.debugConsole.write("Loading " + this.src);
							req.send(null);
							if (req.status !== 0 && (req.status < 200 || req.status >= 300)) {
								throw { message: "Request error: " + req.status };
							}
							this.setDoc(req.responseText);
						} catch(e) {
							XsltForms_globals.error(this.element, "xforms-link-exception", "Fatal error loading " + this.src, e.toString());
						}
					}
				}
			}
		} else {
			this.setDoc(this.srcXML);
		}
	}
};


		

XsltForms_instance.prototype.reset = function() {
	this.setDoc(this.oldXML, true);
};
 

		

XsltForms_instance.prototype.store = function(isReset) {
	if (this.oldXML && !isReset) {
		this.oldXML = null;
	}
	this.oldXML = XsltForms_browser.saveXML(this.doc.documentElement);
};


		

XsltForms_instance.prototype.setDoc = function(xml, isReset, preserveOld) {
	var instid = XsltForms_browser.getMeta(this.doc.documentElement, "instance");
	var modid = XsltForms_browser.getMeta(this.doc.documentElement, "model");
	XsltForms_browser.loadXML(this.doc.documentElement, xml);
	XsltForms_browser.setMeta(this.doc.documentElement, "instance", instid);
	XsltForms_browser.setMeta(this.doc.documentElement, "model", modid);
	if (!preserveOld) {
		this.store(isReset);
	}
	if (instid === XsltForms_browser.idPf + "instance-config") {
		XsltForms_browser.config = this.doc.documentElement;
	}
};
        

		

XsltForms_instance.prototype.revalidate = function() {
	if (!this.readonly) {
		this.validation_(this.doc.documentElement);
	}
};

XsltForms_instance.prototype.validation_ = function(node, readonly, notrelevant) {
	if (!readonly) {
		readonly = false;
	}
	if (!notrelevant) {
		notrelevant = false;
	}
	this.validate_(node, readonly, notrelevant);
	readonly = XsltForms_browser.getBoolMeta(node, "readonly");
	notrelevant = XsltForms_browser.getBoolMeta(node, "notrelevant");
	var atts = node.attributes || [];
	if (atts) {
		var atts2 = [];
		for (var i = 0, len = atts.length; i < len; i++) {
			if (atts[i].nodeName.substr(0,10) !== "xsltforms_") {
				atts2[atts2.length] = atts[i];
			}
		}
		for (var i2 = 0, len2 = atts2.length; i2 < len2; i2++) {
			this.validation_(atts2[i2], readonly, notrelevant);
		}
	}
	for (var j = 0, len1 = node.childNodes.length; j < len1; j++) {
		var child = node.childNodes[j];
		if (child.nodeType === XsltForms_nodeType.ELEMENT) {
			this.validation_(child, readonly, notrelevant);
		}
	}
};

XsltForms_instance.prototype.validate_ = function(node, readonly, notrelevant) {
	var bindids = XsltForms_browser.getMeta(node, "bind");
	var value = XsltForms_globals.xmlValue(node);
	var schtyp = XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string");
	if (bindids) {
		var binds = bindids.split(" ");
		var relevantfound = false;
		var readonlyfound = false;
		for (var i = 0, len = binds.length; i < len; i++) {
			var bind = document.getElementById(binds[i]).xfElement;
			var nodes = bind.nodes;
			var i2 = 0;
			for (var len2 = nodes.length; i2 < len2; i2++) {
				if (nodes[i2] === node) {
					break;
				}
			}
			for (var j = 0, len3 = bind.depsNodes.length; j < len3; j++) {
				XsltForms_browser.rmValueMeta(bind.depsNodes[j], "depfor", bind.depsId);
			}
			bind.depsNodes.length = 0;
			var ctx = new XsltForms_exprContext(node, i2, nodes, null, null, null, [], bind.depsId);
			if (bind.required) {
				this.setProperty_(node, "required", bind.required.evaluate(ctx, node));
			}
			if (notrelevant || !relevantfound || bind.relevant) {
				this.setProperty_(node, "notrelevant", notrelevant || !(bind.relevant? bind.relevant.evaluate(ctx, node) : true));
				relevantfound = relevantfound || bind.relevant;
			}
			if (readonly || !readonlyfound || bind.readonly) {
				this.setProperty_(node, "readonly", readonly || (bind.readonly? bind.readonly.evaluate(ctx, node) : bind.calculate ? true : false));
				readonlyfound = readonlyfound || bind.readonly;
			}
			this.setProperty_(node, "notvalid",
				!XsltForms_browser.getBoolMeta(node, "notrelevant") && !(!(XsltForms_browser.getBoolMeta(node, "required") && (!value || value === "")) &&
				(XsltForms_browser.getNil(node) ? value === "" : !schtyp || schtyp.validate(value)) &&
				(!bind.constraint || bind.constraint.evaluate(ctx, node))));
			XsltForms_browser.copyArray(ctx.depsNodes, bind.depsNodes);
		}
	} else {
		this.setProperty_(node, "notrelevant", notrelevant);
		this.setProperty_(node, "readonly", readonly);
		this.setProperty_(node, "notvalid", schtyp && !schtyp.validate(value));
	}
};

XsltForms_instance.prototype.setProperty_ = function (node, property, value) {
	if (XsltForms_browser.getBoolMeta(node, property) !== value) {
		XsltForms_browser.setBoolMeta(node, property, value);
		this.model.addChange(node);   
	}
};

		

XsltForms_browser.json2xml = function(name, json, root, inarray) {
	var fullname = "";
	if (name === "________") {
		fullname = " exml:fullname=\"" + XsltForms_browser.escape(name) + "\"";
		name = "________";
	}
	var ret = root ? "<exml:anonymous xmlns:exml=\"http://www.agencexml.com/exml\" xmlns:xsi=\"http://www.w3.org/1999/XMLSchema-instance\" xmlns:exsi=\"http://www.agencexml.com/exi\" xmlns=\"\">" : "";
	if (json instanceof Array) {
		if (inarray) {
			ret += "<exml:anonymous exsi:maxOccurs=\"unbounded\">";
		}
		if (json.length === 0) {
			ret += "<" + (name === "" ? "exml:anonymous" : name) + fullname + " exsi:maxOccurs=\"unbounded\" xsi:nil=\"true\"/>";
		} else {
			for (var i = 0, len = json.length; i < len; i++) {
				ret += XsltForms_browser.json2xml(name === "" ? "exml:anonymous" : name, json[i], false, true);
			}
		}
		if (inarray) {
			ret += "</exml:anonymous>";
		}
	} else {
		var xsdtype = "";
		switch(typeof(json)) {
			case "number":
				xsdtype = " xsi:type=\"xsd:double\"";
				break;
			case "boolean":
				xsdtype = " xsi:type=\"xsd:boolean\"";
				break;
			case "object":
				if (json instanceof Date) {
					xsdtype = " xsi:type=\"xsd:dateTime\"";
				}
				break;
		}
		ret += name === "" ? "" : "<"+name+fullname+(inarray?" exsi:maxOccurs=\"unbounded\"":"")+xsdtype+">";
		if (typeof(json) === "object" && !(json instanceof Date)) {
			for (var m in json) {
				if (json.hasOwnProperty(m)) {
					ret += XsltForms_browser.json2xml(m, json[m], false, false);
				}
			}
		} else {
			if (json instanceof Date) {
				ret += json.getFullYear() + "-";
				ret += (json.getMonth() < 9 ? "0" : "") + (json.getMonth()+1) + "-";
				ret += (json.getDate() < 10 ? "0" : "") + json.getDate() + "T";
				ret += (json.getHours() < 10 ? "0" : "") + json.getHours() + ":";
				ret += (json.getMinutes() < 10 ? "0" : "") + json.getMinutes() + ":";
				ret += (json.getSeconds() < 10 ? "0" : "") + json.getSeconds() + "Z";
			} else {
				ret += XsltForms_browser.escape(json);
			}
		}
		ret += name === "" ? "" : "</"+name+">";
	}
	ret += root ? "</exml:anonymous>" : "";
	return ret;
};

		

XsltForms_browser.jsoninst = function(json) {
	//alert(json2xml("", json, true, false));
	XsltForms_browser.jsoninstobj.setDoc(XsltForms_browser.json2xml("", json, true, false));
	XsltForms_globals.addChange(XsltForms_browser.jsoninstobj.model);
	XsltForms_xmlevents.dispatch(XsltForms_browser.jsoninstobj.model, "xforms-rebuild");
	XsltForms_globals.refresh();
	document.body.removeChild(document.getElementById("jsoninst"));
};
    
	
		
		
		
function XsltForms_bind(subform, id, parent, nodeset, type, readonly, required, relevant, calculate, constraint) {
	if (document.getElementById(id)) {
		return;
	}
	var model = parent.model || parent;
	if (type === "xsd:ID") {
		XsltForms_globals.IDstr = nodeset;
		return;
	}
	this.init(subform, id, parent, "xforms-bind");
	this.model = model;
	this.type = type ? XsltForms_schema.getType(type) : null;
	this.nodeset = nodeset;
	this.readonly = readonly;
	this.required = required;
	this.relevant = relevant;
	this.calculate = calculate;
	this.constraint = constraint;
	this.depsNodes = [];
	this.depsElements = [];
	this.nodes = [];
	this.binds = [];
	this.binding = new XsltForms_binding(null, this.nodeset);
	parent.addBind(this);
	subform.binds.push(this);
	this.depsId = XsltForms_element.depsId++;
}

XsltForms_bind.prototype = new XsltForms_coreElement();

XsltForms_bind.prototype.addBind = function(bind) {
	this.binds.push(bind);
};

XsltForms_bind.prototype.clear = function(bind) {
	this.depsNodes.length = 0;
	this.depsElements.length = 0;
	this.nodes.length = 0;
	XsltForms_browser.forEach(this.binds, "clear");
};

		

XsltForms_bind.prototype.refresh = function(ctx, index) {
	if (!index) {
		for (var i = 0, len = this.depsNodes.length; i < len; i++) {
			XsltForms_browser.rmValueMeta(this.depsNodes[i], "depfor", this.depsId);
		}
		this.clear();
	}
	ctx = ctx || (this.model ? this.model.getInstanceDocument() ? this.model.getInstanceDocument().documentElement : null : null);
	XsltForms_browser.copyArray(this.binding.evaluate(ctx, this.depsNodes, this.depsId, this.depsElements), this.nodes);
	var el = this.element;
	for (var i2 = 0, len2 = this.nodes.length; i2 < len2; i2++) {
		var node = this.nodes[i2];
		var bindids = XsltForms_browser.getMeta(node, "bind");
		if (!bindids) {
			XsltForms_browser.setMeta(node, "bind", this.element.id);
		} else {
			var bindids2 = " "+bindids+" ";
			if (bindids.indexOf(" "+this.element.id+" ") === -1) {
				XsltForms_browser.setMeta(node, "bind", bindids+" "+this.element.id);
			}
		}
		if (this.type) {
			if (XsltForms_browser.getMeta(node, "schemaType")) {
				XsltForms_globals.error(el, "xforms-binding-exception", "Type especified in xsi:type attribute");
			} else {
				var name = this.type.name;
				var ns = this.type.nsuri;
				for (var key in XsltForms_schema.prefixes) {
					if (XsltForms_schema.prefixes.hasOwnProperty(key)) {
						if (XsltForms_schema.prefixes[key] === ns) {
							name = key + ":" + name;
							break;
						}
					}
				}
				XsltForms_browser.setType(node, name);
			}
		}
		for (var j = 0, len1 = el.childNodes.length; j < len1; j++) {
			el.childNodes[j].xfElement.refresh(node, i2);
		}
	}
};


		

XsltForms_bind.prototype.recalculate = function() {
	var el = this.element;
	if (this.calculate) {
		for (var i = 0, len = this.nodes.length; i < len; i++) {
			var node = this.nodes[i];
			var ctx = new XsltForms_exprContext(node, i + 1, this.nodes);
			var value = XsltForms_globals.stringValue(this.calculate.evaluate(ctx, node));
			value = XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string").normalize(value);
			XsltForms_browser.setValue(node, value);
			this.model.addChange(node);
			XsltForms_browser.debugConsole.write("Calculate " + node.nodeName + " " + value);
		}
	}
	for (var j = 0, len1 = el.childNodes.length; j < len1; j++) {
		el.childNodes[j].xfElement.recalculate();
	}
};

	
		
		
		
function XsltForms_submission(subform, id, model, ref, bind, action, method, version, indent,
			mediatype, encoding, omitXmlDeclaration, cdataSectionElements,
			replace, targetref, instance, separator, includenamespaceprefixes, validate, relevant,
			synchr, show, serialization) {
	if (document.getElementById(id)) {
		return;
	}
	this.init(subform, id, model, "xforms-submission");
	this.model = model;
	if (!model.defaultSubmission) {
		model.defaultSubmission = this;
	}
	this.action = action;
	if (action.substr && action.substr(0,7) === "file://" && !document.applets.xsltforms) {
		XsltForms_browser.loadapplet();
	}
	this.method = method;
	this.replace = replace;
	this.targetref = targetref;
	this.version = version;
	this.indent = indent;
	this.validate = validate;
	this.relevant = relevant;
	this.synchr = synchr;
	this.show = show;
	this.serialization = serialization;
	if (mediatype) {
		var lines = mediatype.split(";");
		this.mediatype = lines[0];
		for (var i = 1, len = lines.length; i < len; i++) {
			var vals = lines[i].split("=");
			switch (vals[0].replace(/^\s+/g,'').replace(/\s+$/g,'')) {
				case "action":
					this.soapAction = vals[1].replace(/^\s+/g,'').replace(/\s+$/g,'');
					break;
				case "charset":
					this.charset = vals[1].replace(/^\s+/g,'').replace(/\s+$/g,'');
					break;
			}
		}
	}
	this.encoding = encoding || "UTF-8";
	this.omitXmlDeclaration = omitXmlDeclaration;
	this.cdataSectionElements = cdataSectionElements;
	this.instance = instance;
	this.separator = separator === "&amp;"? "&" : separator;
	this.includenamespaceprefixes = includenamespaceprefixes;
	this.headers = [];
	if (ref || bind) {
		this.binding = new XsltForms_binding(null, ref, model.element, bind);
		this.eval_ = function() {
			return this.binding.evaluate()[0];
		};
	} else {
		this.eval_ = function() {
			return this.model.getInstanceDocument();
		};
	}
}

XsltForms_submission.prototype = new XsltForms_coreElement();


		

XsltForms_submission.prototype.header = function(nodeset, combine, name, values) {
	this.headers.push({nodeset: nodeset, combine: combine, name: name, values: values});
	return this;
};

		

XsltForms_submission.prototype.submit = function() {
	XsltForms_globals.openAction();
	var node = this.eval_();
	var action = "error";
	if(this.action.evaluate) {
		action = XsltForms_globals.stringValue(this.action.evaluate());
	} else {
		action = this.action;
	}
	if (action.subst && action.subst(0, 8) === "local://" && (typeof(localStorage) === 'undefined')) {
		evcontext["error-type"] = "validation-error";
		this.issueSubmitException_(evcontext, null, {message: "local:// submission not supported"});
		XsltForms_globals.closeAction();
		return;
	}
	var method = "post";
	var subm = this;
	if(this.method.evaluate) {
		method = XsltForms_globals.stringValue(this.method.evaluate());
	} else {
		method = this.method;
	}
	var evcontext = {"method": method, "resource-uri": action};
	if (node) {
		if (this.validate && !XsltForms_globals.validate_(node)) {
			evcontext["error-type"] = "validation-error";
			this.issueSubmitException_(evcontext, null, null);
			XsltForms_globals.closeAction();
			return;
		}
		if ((method === "get" || method === "delete") && this.serialization !== "none" && action.substr(0, 11) !== "javascript:" && action.substr(0, 8) !== "local://") {
			var tourl = XsltForms_submission.toUrl_(node, this.separator);
			action += (action.indexOf('?') === -1? '?' : this.separator) +
				tourl.substr(0, tourl.length - this.separator.length);
		}
	}
	XsltForms_xmlevents.dispatch(this, "xforms-submit-serialize");
	var ser = node ? XsltForms_browser.saveXML(node, this.relevant) : "";
	var instance = this.instance;
	if (action.substr(0, 7) === "file://" || action.substr(0, 11) === "javascript:" || action.substr(0, 8) === "local://") {
		if (action.substr(0, 7) === "file://" && method === "put") {
			if (!XsltForms_browser.writeFile(action.substr(7), subm.encoding, "string", "XSLTForms Java Saver", ser)) {
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-error");
			}
			XsltForms_xmlevents.dispatch(subm, "xforms-submit-done");
		} else if (action.substr(0, 8) === "local://" && method === "put") {
			try {
				window.localStorage.setItem(action.substr(8), ser);
			} catch (e) {
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-error");
				XsltForms_globals.closeAction();
				return;
			}
			XsltForms_xmlevents.dispatch(subm, "xforms-submit-done");
		} else if (action.substr(0, 8) === "local://" && method === "delete") {
			try {
				window.localStorage.removeItem(action.substr(8));
			} catch (e) {
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-error");
				XsltForms_globals.closeAction();
				return;
			}
			XsltForms_xmlevents.dispatch(subm, "xforms-submit-done");
		} else if (method === "get") {
			if (action.substr(0, 7) === "file://") {
				ser =  XsltForms_browser.readFile(action.substr(7), subm.encoding, "string", "XSLTForms Java Loader");
			} else if (action.substr(0, 8) === "local://") {
				try {
					ser = window.localStorage.getItem(action.substr(8));
				} catch (e) {
					XsltForms_xmlevents.dispatch(subm, "xforms-submit-error");
					XsltForms_globals.closeAction();
					return;
				} 
			} else {
				eval("ser = (" + action.substr(11) + ");");
			}
			if (ser !== "" && (subm.replace === "instance" || (subm.targetref && subm.replace === "text"))) {
				var ctxnode = !instance ? (node ? (node.documentElement ? node.documentElement : node.ownerDocument.documentElement) : subm.model.getInstance().documentElement) : document.getElementById(instance).xfElement.doc.documentElement;
				if (subm.targetref) {
					var targetnode = subm.targetref.evaluate(ctxnode);
					if (targetnode && targetnode[0]) {
						if (subm.replace === "instance") {
							XsltForms_browser.loadXML(targetnode[0], ser);
						} else {
							XsltForms_browser.loadTextNode(targetnode[0], ser);
						}
					}
				} else {
					var inst = !instance ? (node ? document.getElementById(XsltForms_browser.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "instance")).xfElement : subm.model.getInstance()) : document.getElementById(instance).xfElement;
					inst.setDoc(ser, false, true);
				}
				XsltForms_globals.addChange(subm.model);
				XsltForms_xmlevents.dispatch(subm.model, "xforms-rebuild");
				XsltForms_globals.refresh();
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-done");
			} else {
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-error");
			}
		}
		XsltForms_globals.closeAction();
		return;
	}
	if (action.substr(0,11) === "javascript:") {
		if (method === "get") {
			ser = XsltForms_browser.readFile(action.substr(7), subm.encoding, "string", "XSLTForms Java Loader");
			if (ser !== "" && (subm.replace === "instance" || (subm.targetref && subm.replace === "text"))) {
				var ctxnode = !instance ? (node ? (node.documentElement ? node.documentElement : node.ownerDocument.documentElement) : subm.model.getInstance().documentElement) : document.getElementById(instance).xfElement.doc.documentElement;
				if (subm.targetref) {
					var targetnode = subm.targetref.evaluate(ctxnode);
					if (targetnode && targetnode[0]) {
						if (subm.replace === "instance") {
							XsltForms_browser.loadXML(targetnode[0], ser);
						} else {
							XsltForms_browser.loadTextNode(targetnode[0], ser);
						}
					}
				} else {
					var inst = !instance ? (node ? document.getElementById(XsltForms_browser.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "instance")).xfElement : subm.model.getInstance()) : document.getElementById(instance).xfElement;
					inst.setDoc(ser, false, true);
				}
				XsltForms_globals.addChange(subm.model);
				XsltForms_xmlevents.dispatch(subm.model, "xforms-rebuild");
				XsltForms_globals.refresh();
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-done");
			} else {
				XsltForms_xmlevents.dispatch(subm, "xforms-submit-error");
			}
		}
		XsltForms_globals.closeAction();
		return;
	}
	var synchr = this.synchr;
	var body;
	if(method === "xml-urlencoded-post") {
		var outForm = document.getElementById("xsltforms_form");
		if(outForm) {
			outForm.firstChild.value = ser;
		} else {
			outForm = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "form") : document.createElement("form");
			outForm.setAttribute("method", "post");
			outForm.setAttribute("action", action);
			outForm.setAttribute("id", "xsltforms_form");
			var txt = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "input") : document.createElement("input");
			txt.setAttribute("type", "hidden");
			txt.setAttribute("name", "postdata");
			txt.setAttribute("value", ser);
			outForm.appendChild(txt);
			body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
			body.insertBefore(outForm, body.firstChild);
		}
		outForm.submit();
		XsltForms_globals.closeAction();
	} else {
		var cross = false;
		if (action.match(/^[a-zA-Z0-9+\.\-]+:\/\//)) {
			var domain = /^([a-zA-Z0-9+\.\-]+:\/\/[^\/]*)/;
			var sdom = domain.exec(action);
			var ldom = domain.exec(document.location.href);
			cross = sdom[0] !== ldom[0];
		}
		if (cross) {
			XsltForms_browser.jsoninstobj = !instance ? (node ? document.getElementById(XsltForms_browser.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "instance")).xfElement : this.model.getInstance()) : document.getElementById(instance).xfElement;
			var scriptelt = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
			scriptelt.setAttribute("src", action.replace(/&amp;/g, "&")+((action.indexOf("?") === -1) ? "?" : "&")+"callback=jsoninst");
			scriptelt.setAttribute("id", "jsoninst");
			scriptelt.setAttribute("type", "text/javascript");
			body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
			body.insertBefore(scriptelt, body.firstChild);
			XsltForms_xmlevents.dispatch(this, "xforms-submit-done");
			XsltForms_globals.closeAction();
		} else {
			// TODO: Validate binding target is not empty
			if (!node && (method !== "get" || method !== "delete")) {
				evcontext["error-type"] = "no-data";
				this.issueSubmitException_(evcontext, null, null);
				return;
			}
			var req = null;
			try {
				req = XsltForms_browser.openRequest(method, action, !synchr);
				var func = function() {
					if (!synchr && req.readyState !== 4) {
						return;
					}
					try {
						if (req.status !== 0 && (req.status < 200 || req.status >= 300)) {
							evcontext["error-type"] = "resource-error";
							subm.issueSubmitException_(evcontext, req, null);
							XsltForms_globals.closeAction();
							return;
						}
						if (subm.replace === "instance" || (subm.targetref && subm.replace === "text")) {
							if (subm.targetref) {
								var ctxnode = !instance ? (node ? (node.documentElement ? node.documentElement : node.ownerDocument.documentElement) : subm.model.getInstance().documentElement) : document.getElementById(instance).xfElement.doc.documentElement;
								var targetnode = subm.targetref.evaluate(ctxnode);
								if (targetnode && targetnode[0]) {
									if (subm.replace === "instance") {
										XsltForms_browser.loadXML(targetnode[0], req.responseText);
									} else {
										XsltForms_browser.loadTextNode(targetnode[0], req.responseText);
									}
								}
							} else {
								var inst = !instance ? (node ? document.getElementById(XsltForms_browser.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "instance")).xfElement : subm.model.getInstance()) : document.getElementById(instance).xfElement;
								inst.setDoc(req.responseText, false, true);
							}
							XsltForms_globals.addChange(subm.model);
							XsltForms_xmlevents.dispatch(subm.model, "xforms-rebuild");
							XsltForms_globals.refresh();
						}
						XsltForms_submission.requesteventlog(evcontext, req);
						XsltForms_xmlevents.dispatch(subm, "xforms-submit-done", null, null, null, null, evcontext);
						XsltForms_globals.closeAction();
						if (subm.replace === "all") {
							var resp = req.responseText;
							var piindex = resp.indexOf("<?xml-stylesheet", 0);
							while ( piindex !== -1) {
								var xslhref = resp.substr(piindex, resp.substr(piindex).indexOf("?>")).replace(/^.*href=\"([^\"]*)\".*$/, "$1");
								resp = XsltForms_browser.transformText(resp, xslhref, false);
								piindex = resp.indexOf("<?xml-stylesheet", 0);
							}
							if( subm.show === "new" ) {
								var w = window.open("about:blank","_blank");
								w.document.write(resp);
								w.document.close();
							} else {
								XsltForms_browser.dialog.hide("statusPanel", false);
								XsltForms_globals.close();
								if(document.write) {
									document.write(resp);
									document.close();
								} else {
									//document.documentElement.parentNode.replaceChild(req.responseXML.documentElement,document.documentElement);
									if (resp.indexOf("<?", 0) === 0) {
										resp = resp.substr(resp.indexOf("?>")+2);
									}                       
									//alert(resp);
									document.documentElement.innerHTML = resp;
								}
							}
						}
					} catch(e) {
						XsltForms_browser.debugConsole.write(e || e.message);
						evcontext["error-type"] = "parse-error";
						subm.issueSubmitException_(evcontext, req, e);
						XsltForms_globals.closeAction();
					}
				};
				if (!synchr) {
					req.onreadystatechange = func;
				}
				var media = this.mediatype;
				var mt = (media || "application/xml") + (this.charset? ";charset=" + this.charset : "");
				XsltForms_browser.debugConsole.write("Submit " + this.method + " - " + mt + " - " + action + " - " + synchr);
				var len = this.headers.length;
				if (len !== 0) {
					var headers = [];
					for (var i = 0, len0 = this.headers.length; i < len0; i++) {
						var nodes = [];
						if (this.headers[i].nodeset) {
							nodes = this.headers[i].nodeset.evaluate();
						} else {
							nodes = [subm.model.getInstanceDocument().documentElement];
						}
						var hname;
						for (var n = 0, lenn = nodes.length; n < lenn; n++) {
							if (this.headers[i].name.evaluate) {
								hname = XsltForms_globals.stringValue(this.headers[i].name.evaluate(nodes[n]));
							} else {
								hname = this.headers[i].name;
							}
							if (hname !== "") {
								var hvalue = "";
								var j;
								var len2;
								for (j = 0, len2 = this.headers[i].values.length; j < len2; j++) {
									var hv = this.headers[i].values[j];
									var hv2;
									if (hv.evaluate) {
										hv2 = XsltForms_globals.stringValue(hv.evaluate(nodes[n]));
									} else {
										hv2 = hv;
									}
									hvalue += hv2;
									if (j < len2 - 1) {
										hvalue += ",";
									}
								}
								var len3;
								for (j = 0, len3 = headers.length; j < len3; j++) {
									if (headers[j].name === hname) {
										switch (this.headers[i].combine) {
											case "prepend":
												headers[j].value = hvalue + "," + headers[j].value;
												break;
											case "replace":
												headers[j].value = hvalue;
												break;
											default:
												headers[j].value += "," + hvalue;
												break;
										}
										break;
									}
								}
								if (j === len3) {
									headers.push({name: hname, value: hvalue});
								}
							}
						}
					}
					for (var k = 0, len4 = headers.length; k < len4; k++) {
						req.setRequestHeader(headers[k].name, headers[k].value);
					}
				}
				if (method === "get" || method === "delete") {
					if (media === XsltForms_submission.SOAP_) {
						req.setRequestHeader("Accept", mt);
					} else {
						if (subm.replace === "instance") {
							req.setRequestHeader("Accept", "application/xml,text/xml");
						}
					}
					req.send(null);
				} else {
					req.setRequestHeader("Content-Type", mt);
					if (media === XsltForms_submission.SOAP_) {
						req.setRequestHeader("SOAPAction", this.soapAction);
					} else {
						if (subm.replace === "instance") {
							req.setRequestHeader("Accept", "application/xml,text/xml");
						}
					}
					req.send(ser);
				}
				if (synchr) {
					func();
				}
			} catch(e) {
				XsltForms_browser.debugConsole.write(e.message || e);
				evcontext["error-type"] = "resource-error";
				subm.issueSubmitException_(evcontext, req, e);
				XsltForms_globals.closeAction();
			}
		}
	}
};

XsltForms_submission.SOAP_ = "application/soap+xml";


		

XsltForms_submission.requesteventlog = function(evcontext, req) {
	evcontext["response-status-code"] = req.status;
	evcontext["response-reason-phrase"] = req.statusText;
	var rheads = req.getAllResponseHeaders();
	var rheaderselts = "";
	if (rheads) {
		rheads = rheads.replace(/\r/, "").split("\n");
		for (var i = 0, len = rheads.length; i < len; i++) {
			var colon = rheads[i].indexOf(":");
			if (colon !== -1) {
				var name = rheads[i].substring(0, colon).replace(/^\s+|\s+$/, "");
				var value = rheads[i].substring(colon+1).replace(/^\s+|\s+$/, "");
				rheaderselts += "<header><name>"+XsltForms_browser.escape(name)+"</name><value>"+XsltForms_browser.escape(value)+"</value></header>";
			}
		}
	}
	evcontext.rheadsdoc = XsltForms_browser.createXMLDocument("<data>"+rheaderselts+"</data>");
	if (evcontext.rheadsdoc.documentElement.firstChild) {
		var rh = evcontext.rheadsdoc.documentElement.firstChild;
		evcontext["response-headers"] = [rh];
		while (rh.nextSibling) {
			rh = rh.nextSibling;
			evcontext["response-headers"].push(rh);
		}
	}
	if (req.responseXML) {
		evcontext["response-body"] = [XsltForms_browser.createXMLDocument(req.responseText)];
	} else {
		evcontext["response-body"] = req.responseText || "";
	}
};

		

XsltForms_submission.prototype.issueSubmitException_ = function(evcontext, req, ex) {
	if (ex) {
		evcontext.message = ex.message || ex;
		evcontext["stack-trace"] = ex.stack;
	}
	if (req) {
		XsltForms_submission.requesteventlog(evcontext, req);
	}
	XsltForms_xmlevents.dispatch(this, "xforms-submit-error", null, null, null, null, evcontext);
};

		

XsltForms_submission.toUrl_ = function(node, separator) {
	var url = "";
	var val = "";
	var hasChilds = false;
	for(var i = 0, len = node.childNodes.length; i < len; i++) {
		var child = node.childNodes[i];
		switch (child.nodeType) {
			case XsltForms_nodeType.ELEMENT:
				hasChilds = true;
				url += this.toUrl_(child, separator);
				break;
			case XsltForms_nodeType.TEXT:
				val += child.nodeValue;
				break;
		}
	}
	if (!hasChilds && val.length > 0) {
		url += node.nodeName + '=' + encodeURIComponent(val) + separator;
	}
	return url;
};

	
		
		
		
var XsltForms_processor = {

		

	error : function(element, type, value) {
			alert(type+": "+value);
		}
};

	
		
		
function XsltForms_timer(subform, id, time) {
	if (document.getElementById(id)) {
		return;
	}
	this.init(subform, id, null, "xforms-timer");
	this.running = false;
	this.time = time;
}

XsltForms_timer.prototype = new XsltForms_coreElement();

XsltForms_timer.prototype.start = function() {
	this.running = true;
	var timer = this;
	setTimeout(function() { timer.run(); }, this.time);
};

XsltForms_timer.prototype.stop = function() {
	this.running = false;
};

XsltForms_timer.prototype.run = function() {
	if (this.running) {
		var timer = this;
		XsltForms_globals.openAction();
		XsltForms_xmlevents.dispatch(timer.element, "ajx-time");
		XsltForms_globals.closeAction();
		setTimeout(function() { timer.run(); }, this.time);
	}
};
    
	
	
		
		
		
		
function XsltForms_confirm(subform, id, binding, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.id = id;
	this.binding = binding;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_confirm.prototype = new XsltForms_abstractAction();

XsltForms_confirm.prototype.run = function(element, ctx, evt) {
	var text;
	if (this.binding) {
		var node = this.binding.evaluate(ctx)[0];
		if (node) {
			text = XsltForms_browser.getValue(node);
		}
	} else {
		var e = XsltForms_idManager.find(this.id);
		XsltForms_globals.build(e, ctx);
		text = e.textContent || e.innerText;
	}
	if (text) {
		var res = XsltForms_browser.confirm(text.trim());
		if (!res) {
			evt.stopPropagation();
			evt.stopped = true;
		}
	}
};

	
		
		
function XsltForms_setproperty(subform, name, value, literal, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.name = name;
	this.value = value;
	this.literal = literal;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_setproperty.prototype = new XsltForms_abstractAction();

XsltForms_setproperty.prototype.run = function(element, ctx) {
	var value = this.literal;
	if (this.value) {
		//value = this.value.evaluate(node); // ??? What is node?
		if (typeof(value) !== "string" && typeof(value.length) !== "undefined") {
			value = value.length > 0? XsltForms_browser.getValue(value[0]) : "";
		}
	}
	if (value) {
		XsltForms_browser.i18n.lang = value;
		XsltForms_browser.debugConsole.write("setproperty " + name + " = " + value);
	}
};
    
	
		
		
		
function XsltForms_abstractAction() {
}


		

XsltForms_abstractAction.prototype.init = function(ifexpr, whileexpr, iterateexpr) {
	this.ifexpr = XsltForms_xpath.get(ifexpr);
	this.whileexpr = XsltForms_xpath.get(whileexpr);
	this.iterateexpr = XsltForms_xpath.get(iterateexpr);
};


		

XsltForms_abstractAction.prototype.execute = function(element, ctx, evt) {
	if (evt.stopped) { return; }
	if (!ctx) {
		ctx = element.node || (XsltForms_globals.defaultModel.getInstanceDocument() ? XsltForms_globals.defaultModel.getInstanceDocument().documentElement : null);
	}
	// for now, iterate overrides while.
	if (this.iterateexpr) {
		if (this.whileexpr) {
			XsltForms_globals.error(this.element, "xforms-compute-exception", "@iterate cannot be used with @while");
		}
		var nodes = this.iterateexpr.evaluate(ctx);
		for (var i = 0, len = nodes.length; i < len; i++) {
			this.exec_(element, nodes[i], evt);
		}
	} else if (this.whileexpr) {
		while (XsltForms_globals.booleanValue(this.whileexpr.evaluate(ctx))) {
			if (!this.exec_(element, ctx, evt)) {
				break;
			}
		}
	} else {
		this.exec_(element, ctx, evt);
	}
};


		

XsltForms_abstractAction.prototype.exec_ = function(element, ctx, evt) {
	if (this.ifexpr) {
		if (XsltForms_globals.booleanValue(this.ifexpr.evaluate(ctx))) {
			this.run(element, ctx, evt);
		} else {
			return false;
		}
	} else {
		this.run(element, ctx, evt);
	}
	return true;
};


		

XsltForms_abstractAction.prototype.run = function(element, ctx, evt) { };

	
		
		
		
function XsltForms_action(subform, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.init(ifexpr, whileexpr, iterateexpr);
	this.childs = [];
}

XsltForms_action.prototype = new XsltForms_abstractAction();


		

XsltForms_action.prototype.add = function(action) {
	this.childs.push(action);
	return this;
};


		

XsltForms_action.prototype.run = function(element, ctx, evt) {
	XsltForms_browser.forEach(this.childs, "execute", element, ctx, evt);
};
    
	
		
		
		
function XsltForms_delete(subform, nodeset, model, bind, at, context, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.binding = new XsltForms_binding(null, nodeset, model, bind);
	//this.at = at?XsltForms_xpath.get(at):null;
	this.at = XsltForms_xpath.get(at);
	this.context = XsltForms_xpath.get(context);
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_delete.prototype = new XsltForms_abstractAction();


		

XsltForms_delete.prototype.run = function(element, ctx) {
	if (this.context) {
		ctx = this.context.evaluate(ctx)[0];
	}
	if (!ctx) {
		return;
	}
	var nodes = this.binding.evaluate(ctx);
	if(this.at) {
		var index = XsltForms_globals.numberValue(this.at.evaluate(new XsltForms_exprContext(ctx, 1, nodes)));
		if(!nodes[index - 1]) {
			return;
		}
		nodes = [nodes[index - 1]];
	}
	var model;
	var instance;
	if (nodes.length > 0) {
		model = document.getElementById(XsltForms_browser.getMeta(nodes[0].documentElement ? nodes[0].documentElement : nodes[0].ownerDocument.documentElement, "model")).xfElement;
		instance = model.findInstance(nodes[0]);
	}
	for (var i = 0, len = nodes.length; i < len; i++) {
		var node = nodes[i];
		XsltForms_mipbinding.nodedispose(node);
		var repeat = XsltForms_browser.getMeta(node, "repeat");
		if (node.nodeType === XsltForms_nodeType.ATTRIBUTE) {
			if (node.ownerElement) {
				node.ownerElement.removeAttributeNS(node.namespaceURI, node.nodeName);
			} else {
				node.selectSingleNode("..").removeAttributeNS(node.namespaceURI, node.nodeName);
			}
		} else {
			node.parentNode.removeChild(node);
		}
		if (repeat) {
			document.getElementById(repeat).xfElement.deleteNode(node);
		}
	}
	if (nodes.length > 0) {
		XsltForms_globals.addChange(model);
		model.setRebuilded(true);
		XsltForms_xmlevents.dispatch(instance, "xforms-delete");
	}
};
    
	
		
		
		
function XsltForms_dispatch(subform, name, target, ifexpr, whileexpr, iterateexpr, delay) {
	this.subform = subform;
	this.name = name;
	this.target = target;
	this.init(ifexpr, whileexpr, iterateexpr);
	this.delay = delay;
}

XsltForms_dispatch.prototype = new XsltForms_abstractAction();


		

XsltForms_dispatch.prototype.run = function(element, ctx, evt) {
	var name = this.name;
	if (name.evaluate) {
		name = XsltForms_globals.stringValue(name.evaluate());
	}
	var target = this.target;
	if (target && target.evaluate) {
		target = XsltForms_globals.stringValue(target.evaluate());
	}
	if (!target) {
		switch (name) {
			case "xforms-submit":
				target = document.getElementById(XsltForms_browser.getMeta(ctx.ownerDocument.documentElement, "model")).xfElement.defaultSubmission;
				break;
			case "xforms-reset":
				target = document.getElementById(XsltForms_browser.getMeta(ctx.ownerDocument.documentElement, "model")).xfElement;
				break;
		}
	} else {
		target = typeof target === "string"? document.getElementById(target) : target;
	}
	var delay = 0;
	if (this.delay) {
		if (this.delay.evaluate) {
			delay = XsltForms_globals.numberValue(this.delay.evaluate());
		} else {
			delay = XsltForms_globals.numberValue(this.delay);
		}
	}
	if (delay > 0 ) {
		window.setTimeout("XsltForms_xmlevents.dispatch(document.getElementById('"+target.id+"'),'"+name+"')", delay);
	} else {
		XsltForms_xmlevents.dispatch(target, name);
	}
};
    
	
		
		
		
function XsltForms_insert(subform, nodeset, model, bind, at, position, origin, context, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.binding = new XsltForms_binding(null, nodeset, model, bind);
	this.origin = XsltForms_xpath.get(origin);
	this.context = XsltForms_xpath.get(context);
	this.at = XsltForms_xpath.get(at);
	this.position = position;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_insert.prototype = new XsltForms_abstractAction();


		

XsltForms_insert.prototype.run = function(element, ctx) {
	if (this.context) {
		ctx = this.context.evaluate(ctx)[0];
	}
	if (!ctx) {
		return;
	}
	var nodes = [];
	if( this.binding.bind || this.binding.xpath ) {
		nodes = this.binding.evaluate(ctx);
	}
	var index = 0;
	var node = null;
	var originNodes = [];
	var parent = null;
	var pos = this.position === "after"? 1 : 0;
	var res = 0;
	if (this.origin) {
		originNodes = this.origin.evaluate(ctx);
	}
	if (originNodes.length === 0) {
		if (nodes.length === 0) {
			return;
		}
		originNodes.push(nodes[nodes.length - 1]);
	}
	for(var i = 0, len = originNodes.length; i < len; i += 1) {
		node = originNodes[i];
		if (nodes.length === 0) {
			parent = ctx;
		} else {
			parent = nodes[0].nodeType === XsltForms_nodeType.DOCUMENT? nodes[0] : nodes[0].nodeType === XsltForms_nodeType.ATTRIBUTE? nodes[0].ownerDocument ? nodes[0].ownerDocument : nodes[0].selectSingleNode("..") : nodes[0].parentNode;
			if (parent.nodeType !== XsltForms_nodeType.DOCUMENT && node.nodeType !== XsltForms_nodeType.ATTRIBUTE) {
				res = this.at ? Math.round(XsltForms_globals.numberValue(this.at.evaluate(new XsltForms_exprContext(ctx, 1, nodes)))) + i - 1: nodes.length - 1;
				index = isNaN(res)? nodes.length : res + pos;
			}
		}
		XsltForms_browser.debugConsole.write("insert " + node.nodeName + " in " + parent.nodeName + " at " + index + " - " + ctx.nodeName);
		if (node.nodeType === XsltForms_nodeType.ATTRIBUTE) {
			XsltForms_browser.setAttributeNS(parent, node.namespaceURI, (node.prefix ? node.prefix+":" : "")+node.nodeName, node.nodeValue);
		} else {
			var clone = node.cloneNode(true);
			if (parent.nodeType === XsltForms_nodeType.DOCUMENT) {
				var first = parent.documentElement;
				var prevmodel = XsltForms_browser.getMeta(first, "model");
				var previnst = XsltForms_browser.getMeta(first, "instance");
				parent.removeChild(first);
				first = null;
				XsltForms_browser.setMeta(clone, "instance", previnst);
				XsltForms_browser.setMeta(clone, "model", prevmodel);
				parent.appendChild(clone);
			} else {
				var nodeAfter;
				if (index >= nodes.length && nodes.length !== 0) {
					nodeAfter = nodes[nodes.length - 1].nextSibling;
				} else {
					nodeAfter = nodes[index];
				}
				if (nodeAfter) {
					nodeAfter.parentNode.insertBefore(clone, nodeAfter);
				} else {
					parent.appendChild(clone);
				}
				var repeat = nodes.length > 0? XsltForms_browser.getMeta(nodes[0], "repeat") : null;
				nodes.push(clone);
				if (repeat) {
					document.getElementById(repeat).xfElement.insertNode(clone, nodeAfter);
				}
			}
		}
	}
	var model = document.getElementById(XsltForms_browser.getMeta(parent.documentElement ? parent.documentElement : parent.ownerDocument.documentElement, "model")).xfElement;
	XsltForms_globals.addChange(model);
	model.setRebuilded(true);
	XsltForms_xmlevents.dispatch(model.findInstance(parent), "xforms-insert");
};
    
	
		
		
		
function XsltForms_load(subform, binding, resource, show, targetid, instance, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.binding = binding;
	this.resource = resource;
	this.show = show;
	this.targetid = targetid;
	this.instance = instance;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_load.prototype = new XsltForms_abstractAction();


		

XsltForms_load.prototype.run = function(element, ctx) {
	var href = this.resource;
	if (this.binding) {
		var node = this.binding.evaluate(ctx)[0];
		if (node) {
			href = XsltForms_browser.getValue(node);
		}
	} else {
		if (href && typeof href === 'object') {
			href = XsltForms_globals.stringValue(this.resource.xpath.evaluate(ctx));
		} else {
			if (typeof href === 'string') {
				href = XsltForms_browser.unescape(href); 
			}
		}
	}
	if (href) {
		if(href.substr(0, 11) === "javascript:") {
			try {
				eval("{XsltForms_context={elementId:\""+element.getAttribute("id")+"\"};"+href.substr(11)+"}");
			} catch (e) {
				alert("XSLTForms Exception\n--------------------------\n\nError evaluating the following Javascript expression :\n\n"+href.substr(11)+"\n\n"+e);
			}
		} else if (this.show === "new") {
			window.open(href);
		} else if (this.show === "embed") {
			XsltForms_globals.openAction();
			var req = null;
			var method = "get";
			var evcontext = {"method": method, "resource-uri": href};
			try {
				req = XsltForms_browser.openRequest(method, href, false);
				XsltForms_browser.debugConsole.write("Load " + href);
				req.send(null);
				if (req.status !== 0 && (req.status < 200 || req.status >= 300)) {
					evcontext["error-type"] = "resource-error";
					this.issueLoadException_(evcontext, req, null);
					XsltForms_globals.closeAction();
					return;
				}
				XsltForms_submission.requesteventlog(evcontext, req);
				//XsltForms_xmlevents.dispatch(this, "xforms-load-done", null, null, null, null, evcontext);
				XsltForms_globals.closeAction();
				var resp = req.responseText;
				var piindex = resp.indexOf("<?xml-stylesheet", 0);
				while ( piindex !== -1) {
					var xslhref = resp.substr(piindex, resp.substr(piindex).indexOf("?>")).replace(/^.*href=\"([^\"]*)\".*$/, "$1");
					resp = XsltForms_browser.transformText(resp, xslhref, false);
					piindex = resp.indexOf("<?xml-stylesheet", 0);
				}
				XsltForms_browser.dialog.hide("statusPanel", false);
				var sp = XsltForms_globals.stringSplit(resp, "XsltForms_MagicSeparator");
				var subjs = "/* xsltforms-subform-" + XsltForms_globals.nbsubforms + " " + sp[1] + " xsltforms-subform-" + XsltForms_globals.nbsubforms + " */"
				var imain = subjs.indexOf('"xsltforms-mainform"');
				var targetelt = XsltForms_idManager.find(this.targetid);
				var targetsubform = targetelt.xfSubform;
				if (targetsubform) {
					targetsubform.dispose();
				}
				subjs = '(function(){var xsltforms_subform_eltid = "' + targetelt.id + '";var xsltforms_parentform = XsltForms_subform.subforms["' + this.subform.id + '"];' + subjs.substring(0, imain) + '"xsltforms-subform-' + XsltForms_globals.nbsubforms + '"' + subjs.substring(imain + 20) + "})();";
				var subbody = "<!-- xsltforms-subform-" + XsltForms_globals.nbsubforms + " " + sp[3] + " xsltforms-subform-" + XsltForms_globals.nbsubforms + " -->"
				imain = subbody.indexOf(' id="xsltforms-mainform');
				while (imain !== -1) {
					subbody = subbody.substring(0, imain) + ' id="xsltforms-subform-' + XsltForms_globals.nbsubforms + subbody.substring(imain + 23);
					imain = subbody.indexOf(' id="xsltforms-mainform');
				}
				if (targetelt.xfElement) {
					targetelt = targetelt.children[targetelt.children.length - 1];
				}
				targetelt.innerHTML = subbody;
				targetelt.hasXFElement = null;
				var scriptelt = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
				scriptelt.setAttribute("id", "xsltforms-subform-" + XsltForms_globals.nbsubforms + "-script");
				scriptelt.setAttribute("type", "text/javascript");
				var scripttxt = document.createTextNode(subjs);
				scriptelt.appendChild(scripttxt);
				var body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
				body.insertBefore(scriptelt, body.firstChild);
				XsltForms_browser.setClass(targetelt, "xforms-subform-loaded", true);
				XsltForms_globals.nbsubforms++;
			} catch(e2) {
				XsltForms_browser.debugConsole.write(e2.message || e2);
				evcontext["error-type"] = "resource-error";
				this.issueLoadException_(evcontext, req, e2);
				XsltForms_globals.closeAction();
			}
		} else {
			location.href = href;
		}
	} else {
		if (this.instance) {
			var instance = document.getElementById(this.instance);
			if (!instance) { throw {name: "instance " + this.instance + " not found"}; }
			var ser = XsltForms_browser.saveXML(instance.xfElement.doc.documentElement);
			var lw;
			if (this.show === "new") {
				lw = window.open('data:text/xml,' + ser, this.instance);
				lw.document.close();
			} else {
				if (this.show === "replace") {
					lw = window.open("about:blank", _self);
					lw.document.write(ser);
					lw.document.close();
				}
			}
		}
	}
};

		

XsltForms_load.prototype.issueLoadException_ = function(evcontext, req, ex) {
	if (ex) {
		evcontext.message = ex.message || ex;
		evcontext["stack-trace"] = ex.stack;
	}
	if (req) {
		XsltForms_submission.requesteventlog(evcontext, req);
	}
	XsltForms_xmlevents.dispatch(document.getElementById(this.targetid), "xforms-link-exception", null, null, null, null, evcontext);
};

	
		
		
		
function XsltForms_message(subform, id, binding, level, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.binding = binding;
	this.id = id;
	this.level = level;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_message.prototype = new XsltForms_abstractAction();


		

XsltForms_message.prototype.run = function(element, ctx) {
	var text;
	if (this.binding) {
		var node = this.binding.evaluate(ctx)[0];
		if (node) {
			text = XsltForms_browser.getValue(node);
		}
	} else {
		var e = XsltForms_idManager.find(this.id);
		var building = XsltForms_globals.building;
		XsltForms_globals.building = true;
		XsltForms_globals.build(e, ctx);
		XsltForms_globals.building = building;
		text = e.textContent || e.innerText;
	}

	if (text) {
		alert(text.trim());
	}
};
    
	
		
		
		
function XsltForms_script(subform, binding, stype, script, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.binding = binding;
	this.stype = stype;
	this.script = script;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_script.prototype = new XsltForms_abstractAction();


		

XsltForms_script.prototype.run = function(element, ctx) {
	var script = this.script;
	switch (this.stype) {
		case "text/javascript":
			if (this.binding) {
				var node = this.binding.evaluate(ctx)[0];
				if (node) {
					script = XsltForms_browser.getValue(node);
				}
			} else {
				if (typeof script === 'object') {
					script = XsltForms_globals.stringValue(this.script.xpath.evaluate(ctx));
				} else {
					if (typeof script === 'string') {
						script = XsltForms_browser.unescape(script); 
					}
				}
			}
			if (script) {
				try {
					eval("{XsltForms_context={elementId:\""+element.getAttribute("id")+"\"};"+script+"}");
				} catch (e) {
					alert("XSLTForms Exception\n--------------------------\n\nError evaluating the following Javascript expression :\n\n"+script+"\n\n"+e);
				}
			}
			break;
		case "application/xquery":
			this.script.xpath.evaluate(ctx);
			break;
	}
};

	
		
		
		
function XsltForms_setindex(subform, repeat, index, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.repeat = repeat;
	this.index = XsltForms_xpath.get(index);
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_setindex.prototype = new XsltForms_abstractAction();


		

XsltForms_setindex.prototype.run = function(element, ctx) {
	var repeat = XsltForms_idManager.find(this.repeat);
	var index = XsltForms_globals.numberValue(this.index.evaluate(ctx));
	XsltForms_browser.debugConsole.write("setIndex " + index);
	if (!isNaN(index)) {
		repeat.xfElement.setIndex(index);
	}
};

	
		
		
		
function XsltForms_setvalue(subform, binding, value, literal, context, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.binding = binding;
	this.value = value? XsltForms_xpath.get(value) : null;
	this.literal = literal;
	this.context = XsltForms_xpath.get(context);
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_setvalue.prototype = new XsltForms_abstractAction();


		

XsltForms_setvalue.prototype.run = function(element, ctx) {
	var node = this.binding.evaluate(ctx)[0];
	if (node) {
		if (this.context) {
			ctx = this.context.evaluate(ctx)[0];
		}
		var value = this.value? XsltForms_globals.stringValue(this.context ? this.value.evaluate(ctx, ctx) : this.value.evaluate(node, ctx)) : this.literal;
		XsltForms_globals.openAction();
		XsltForms_browser.setValue(node, value || "");
		document.getElementById(XsltForms_browser.getMeta(node.ownerDocument.documentElement, "model")).xfElement.addChange(node);
		XsltForms_browser.debugConsole.write("Setvalue " + node.nodeName + " = " + value); 
		XsltForms_globals.closeAction();
	}
};

	
		
		
		
function XsltForms_toggle(subform, caseId, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.caseId = caseId;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_toggle.prototype = new XsltForms_abstractAction();


		

XsltForms_toggle.prototype.run = function(element, ctx) {
	XsltForms_toggle.toggle(this.caseId, ctx);
};


		

XsltForms_toggle.toggle = function(caseId, ctx) {
	XsltForms_globals.openAction();
	if (typeof caseId === 'object') {
		if (!ctx) {
			ctx = XsltForms_globals.defaultModel.getInstanceDocument() ? XsltForms_globals.defaultModel.getInstanceDocument().documentElement : null;
		}
		caseId = XsltForms_globals.stringValue(caseId.xpath.evaluate(ctx));
	}
	var element = XsltForms_idManager.find(caseId);
	var childs = element.parentNode.childNodes;
	var ul;
	var index = -1;
	if (childs.length > 0 && childs[0].nodeName.toLowerCase() === "ul") {
		ul = childs[0];
	}
	for (var i = ul ? 1 : 0, len = childs.length; i < len; i++) {
		var child = childs[i];
		if (child === element) {
			index = i - 1;
		} else {
			if (child.style && child.style.display !== "none") {
				child.style.display = "none";
				if (ul) {
					XsltForms_browser.setClass(ul.childNodes[i - 1], "ajx-tab-selected", false);
				}
			}
			XsltForms_xmlevents.dispatch(child, "xforms-deselect");
		}
	}
	if (element.style.display === "none") {
		element.style.display = "block";
		if (ul) {
			XsltForms_browser.setClass(ul.childNodes[index], "ajx-tab-selected", true);
		}
	}
	XsltForms_xmlevents.dispatch(element, "xforms-select");
	XsltForms_globals.closeAction();
};

	
		
		
		
function XsltForms_unload(subform, targetid, ifexpr, whileexpr, iterateexpr) {
	this.subform = subform;
	this.targetid = targetid;
	this.init(ifexpr, whileexpr, iterateexpr);
}

XsltForms_unload.prototype = new XsltForms_abstractAction();


		

XsltForms_unload.prototype.run = function(element, ctx) {
	var targetid = this.targetid || this.subform.eltid;
	var targetelt = XsltForms_idManager.find(targetid);
	targetelt.xfSubform.dispose();
	targetelt.xfSubform = null;
	if (targetelt.xfElement) {
		targetelt = targetelt.children[targetelt.children.length - 1];
	}
	targetelt.innerHTML = "";
	targetelt.hasXFElement = null;
	XsltForms_browser.setClass(targetelt, "xforms-subform-loaded", false);
};

	
	
		
		
		
		
function XsltForms_tree(subform, id, binding) {
	this.init(subform, id);
	this.binding = binding;
	this.hasBinding = true;
	this.root = XsltForms_browser.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "ul")[0] : this.element.getElementsByTagName("ul")[0];
	this.label = this.root.firstChild.cloneNode(true);
}

XsltForms_tree.prototype = new XsltForms_element();

XsltForms_tree.prototype.dispose = function() {
	this.root = null;
	this.selected = null;
	XsltForms_element.prototype.dispose.call(this);
};

XsltForms_tree.prototype.build_ = function(ctx) {
	var node = this.evaluateBinding(this.binding, ctx)[0];
	if (node) {
		var nodes = [];
		this.buildTree(this.root, 0, node, nodes);
		if (!this.selected || !XsltForms_browser.inArray(this.selected, nodes)) {
			this.select(this.root.firstChild);
		}
	}
};

XsltForms_tree.prototype.select = function(item) {
	var changed = true;
	var init = !!this.selected;
	if (init) {
		if (this.selected === item) {
			changed = false;
		} else {
			XsltForms_browser.setClass(this.selected, "xforms-tree-item-selected", false);
			XsltForms_browser.setClass(this.selected.childNodes[1], "xforms-tree-item-label-selected", false);
		}
	}
	if (changed) {
		this.element.node = item.node;
		this.selected = item;
		XsltForms_browser.setClass(item, "xforms-tree-item-selected", true);
		XsltForms_browser.setClass(item.childNodes[1], "xforms-tree-item-label-selected", true);
		XsltForms_globals.openAction();
		XsltForms_globals.addChange(this);
		XsltForms_globals.addChange(document.getElementById(XsltForms_browser.getMeta(item.node.ownerDocument.documentElement, "model")).xfElement);
		XsltForms_globals.closeAction();
	}
};

XsltForms_tree.prototype.click = function(target) {
	if (target.className === "xforms-tree-item-button") {
		var ul = target.nextSibling.nextSibling;
		if (ul) {
			ul.style.display = ul.style.display !== "none"? "none" : "block";
		}
	} else if (XsltForms_browser.hasClass(target, "xforms-tree-item-label")) {
		this.select(target.parentNode);
	}
};

XsltForms_tree.prototype.buildTree = function(parent, index, node, nodes) {
	var li = null;
	var ul = null;
	var label = null;
	var childs = node.childNodes;
	var nochild = childs.length === 0;
	nodes.push(node);
	if (parent.childNodes.length < index + 1) {
		li = this.label.cloneNode(true);
		parent.appendChild(li);
		XsltForms_repeat.initClone(li);
	} else {
		li = parent.childNodes[index];
		var last = li.lastChild;
		if (last.nodeName.toLowerCase() === "ul") {
			ul = last;
			if (nochild) {
				XsltForms_globals.dispose(ul);
				li.removeChild(ul);
			}
		}
	}
	li.node = node;
	XsltForms_browser.setClass(li, "xforms-tree-item-fork", !nochild);
	XsltForms_browser.setClass(li, "xforms-tree-item-leaf", nochild);
	for (var i = 0, len = childs.length; i < len; i++) {
		var child = childs[i];
		if (child.nodeType === XsltForms_nodeType.ELEMENT) {
			if (!ul) {
				ul = XsltForms_browser.createElement("ul", li);
			}
			this.buildTree(ul, i, child, nodes);
		}
	}
	if (ul) {
		for (var j = ul.childNodes.length, len1 = childs.length; j > len1; j--) {
			XsltForms_globals.dispose(ul.lastChild);
			ul.removeChild(ul.lastChild);
		}
	}
};

XsltForms_tree.prototype.refresh = function() {
};
    
	
		
		
		
function XsltForms_avt(subform, id, attrs) {
	this.init(subform, id);
	this.attrs = attrs;
	this.bindings = [];
	//for (var i = 0, len = attrs.length; i < len; i++) {
	//}
	this.hasBinding = true;
	this.isOutput = true;
}

XsltForms_avt.prototype = new XsltForms_control();


		

XsltForms_avt.prototype.clone = function(id) { 
	return new XsltForms_avt(this.subform, id, this.attrs);
};


		

XsltForms_avt.prototype.dispose = function() {
	this.attrs = null;
	XsltForms_control.prototype.dispose.call(this);
};


		

XsltForms_avt.prototype.setValue = function(value) {
	var node = this.element.node;
	var element = this.valueElement;
	if (element.nodeName.toLowerCase() === "span") {
		if (this.mediatype === "application/xhtml+xml") {
			while (element.firstChild) {
				element.removeChild(element.firstChild);
			}
			XDocument.parse(value, element);
		} else {
			XsltForms_browser.setValue(element, value);
		}
	} else {
		element.src = value;
	}
};

	
		
		
		
function XsltForms_element() {
}


		

XsltForms_element.depsId = 0;

XsltForms_element.prototype.init = function(subform, id) {
	this.subform = subform;
	this.element = document.getElementById(id);
	this.element.xfElement = this;
	this.depsElements = [];
	this.depsNodesBuild = [];
	this.depsNodesRefresh = [];
	this.depsIdB = XsltForms_element.depsId++;
	this.depsIdR = XsltForms_element.depsId++;
};


		

XsltForms_element.prototype.dispose = function() {
	if(this.element) {
		this.element.xfElement = null;
		this.element.hasXFElement = null;
		this.element = null;
	}
	this.depsElements = null;
	if (this.depsNodesBuild) {
		for (var i = 0, len = this.depsNodesBuild.length; i < len; i++) {
			XsltForms_browser.rmValueMeta(this.depsNodesBuild[i], "depfor", this.depsIdB);
		}
	}
	this.depsNodesBuild = null;
	if (this.depsNodesRefresh) {
		for (var i2 = 0, len2 = this.depsNodesRefresh.length; i2 < len2; i2++) {
			XsltForms_browser.rmValueMeta(this.depsNodesRefresh[i2], "depfor", this.depsIdR);
		}
	}
	this.depsNodesRefresh = null;
};


		

XsltForms_element.prototype.build = function(ctx) {
	if (this.hasBinding) {
		var deps = this.depsElements;
		var depsN = this.depsNodesBuild;
		var depsR = this.depsNodesRefresh;
		var build = !XsltForms_globals.ready || (deps.length === 0) || ctx !== this.ctx;
		var refresh = false;
		var changes = XsltForms_globals.changes;
		for (var i0 = 0, len0 = depsN.length; !build && i0 < len0; i0++) {
			build = depsN[i0].nodeName === "";
		}
		for (var i = 0, len = deps.length; !build && i < len; i++) {
			var el = deps[i];
			for (var j = 0, len1 = changes.length; !build && j < len1; j++) {
				if (el === changes[j]) {
					if (el.instances) { //model
						if (el.rebuilded || el.newRebuilded) {
							build = true;
						} else {
							for (var k = 0, len2 = depsN.length; !build && k < len2; k++) {
								build = XsltForms_browser.inArray(depsN[k], el.nodesChanged);
							}
							if (!build) {
								for (var n = 0, len3 = depsR.length; n < len3; n++) {
									refresh = XsltForms_browser.inArray(depsR[n], el.nodesChanged);
								}
							}
						}
					} else {
						build = true;
					}
				}
			}
		}
		this.changed = build || refresh;
		if (build) {
			for (var i4 = 0, len4 = depsN.length; i4 < len4; i4++) {
				XsltForms_browser.rmValueMeta(depsN[i4], "depfor", this.depsIdB);
			}
			depsN.length = 0;
			for (var i5 = 0, len5 = depsR.length; i5 < len5; i5++) {
				XsltForms_browser.rmValueMeta(depsR[i5], "depfor", this.depsIdR);
			}
			depsR.length = 0;
			deps.length = 0;
			this.ctx = ctx;
			this.build_(ctx);
		}
	} else {
		this.element.node = ctx;
	}
};

		

XsltForms_element.prototype.evaluateBinding = function(binding, ctx) {
	var nodes = null;
	var errmsg = null;
	if (binding) {
		nodes = binding.evaluate(ctx, this.depsNodesBuild, this.depsIdB, this.depsElements);
		if (nodes || nodes === "") {
			return nodes;
		}
		// A 'null' binding means bind-ID was not found.
		errmsg = "non-existent bind-ID("+ binding.bind + ") on element(" + this.element.id + ")!";
	} else {
		errmsg = "no binding defined for element("+ this.element.id + ")!";
	}
	XsltForms_browser.assert(errmsg);
	if (XsltForms_globals.building && XsltForms_globals.debugMode) {
		//
		// Do not fail here, to keep on searching for more errors.
		XsltForms_globals.bindErrMsgs.push(errmsg);
		XsltForms_xmlevents.dispatch(this.element, "xforms-binding-exception");
		nodes = [];
	} else {
		XsltForms_globals.error(this.element, "xforms-binding-exception", errmsg);
	}
	return nodes;
};

	
		
		
		
function XsltForms_control() {
	this.isControl = true;
}

XsltForms_control.prototype = new XsltForms_element();


		

XsltForms_control.prototype.initFocus = function(element, principal) {
	if (principal) {
		this.focusControl = element;
	}
	XsltForms_browser.events.attach(element, "focus", XsltForms_control.focusHandler);
	XsltForms_browser.events.attach(element, "blur", XsltForms_control.blurHandler);
};


		

XsltForms_control.prototype.dispose = function() {
	this.focusControl = null;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_control.prototype.focus = function(focusEvent) {
	if (this.isOutput) {
		return;
	}
	if (XsltForms_globals.focus !== this) {
		XsltForms_globals.openAction();
		XsltForms_globals.blur(true);
		XsltForms_globals.focus = this;
		XsltForms_browser.setClass(this.element, "xforms-focus", true);
		XsltForms_browser.setClass(this.element, "xforms-disabled", false);
		var parent = this.element.parentNode;
		while (parent.nodeType === XsltForms_nodeType.ELEMENT) {
			if (typeof parent.node !== "undefined" && XsltForms_browser.hasClass(parent, "xforms-repeat-item")) {
				XsltForms_repeat.selectItem(parent);
			}
			parent = parent.parentNode;
		}
		XsltForms_xmlevents.dispatch(XsltForms_globals.focus, "DOMFocusIn");
		XsltForms_globals.closeAction();
		if (this.full && !focusEvent) { // select full
			this.focusFirst();
		}
	}
	var fcontrol = this.focusControl;
	XsltForms_globals.posibleBlur = false;
	if (fcontrol && !focusEvent) {
		var control = this.focusControl;
		var name = control.nodeName.toLowerCase();
		control.focus();
		control.focus();
		if (name === "input" || name === "textarea") {
			try {
				control.select();
			} catch (e) {
			}
		}
	}
};


		

XsltForms_control.prototype.build_ = function(ctx) {
	var result = this.evaluateBinding(this.binding, ctx);
	if (typeof result === "object") {
		var node = result[0];
		var element = this.element;
		var old = element.node;
		if (old !== node || !XsltForms_globals.ready) {
			element.node = node;
			this.nodeChanged = true;
		}
		if (node) {
			this.depsNodesRefresh.push(node);
		}
	} else {
		this.outputValue = result;
	}
};


		

XsltForms_control.prototype.refresh = function() {
	var element = this.element;
	var node = element.node;
	if (node) {
		var value = XsltForms_browser.getValue(node, true, this.complex);
		XsltForms_globals.openAction();
		var changed = value !== this.currentValue || this.nodeChanged;
		if (this.relevant) {
			XsltForms_browser.setClass(element, "xforms-disabled", false);
		}
		this.changeProp(node, "required", "xforms-required", "xforms-optional", changed, value);
		this.changeProp(node, "notrelevant", "xforms-disabled", "xforms-enabled", changed, value);
		this.changeProp(node, "readonly", "xforms-readonly", "xforms-readwrite", changed, value);
		this.changeProp(node, "notvalid", "xforms-invalid", "xforms-valid", changed, value);
		this.currentValue = value;
		if (changed) {
			this.setValue(value);
			if (!this.nodeChanged && !this.isTrigger) {
				XsltForms_xmlevents.dispatch(element, "xforms-value-changed");
			}
		}
		XsltForms_globals.closeAction();
	} else if (this.outputValue) {
		this.setValue(this.outputValue);
		XsltForms_browser.setClass(element, "xforms-disabled", false);
	} else {
		XsltForms_browser.setClass(element, "xforms-disabled", !this.hasValue);
	}
	this.nodeChanged = false;
};


		

XsltForms_control.prototype.changeProp = function(node, prop, onTrue, onFalse, changed, nvalue) {
	var value = XsltForms_browser.getBoolMeta(node, prop);
	if (changed || value !== this[prop]) {
		if (!this.nodeChanged && !this.isTrigger) {
			XsltForms_xmlevents.dispatch(this.element, (value? onTrue : onFalse));
		}
		this[prop] = value;
		if (prop === "notvalid" && nvalue === "") {
			value = false;
		}
		XsltForms_browser.setClass(this.element, onTrue, value);
		XsltForms_browser.setClass(this.element, onFalse, !value);
		if(prop === "readonly" && this.changeReadonly) {
			this.changeReadonly();
		}
	}
};


		

XsltForms_control.prototype.valueChanged = function(value) {
	var node = this.element.node;
	var model = document.getElementById(XsltForms_browser.getMeta(node.ownerDocument.documentElement, "model")).xfElement;
	var schtyp = XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string");
	if (value && value.length > 0 && schtyp.parse) {
		try { value = schtyp.parse(value); } catch(e) { }
	}
	if (value !== XsltForms_browser.getValue(node)) {
		XsltForms_globals.openAction();
		XsltForms_browser.setValue(node, value);
		model.addChange(node);
		//XsltForms_xmlevents.dispatch(model, "xforms-recalculate");
		//XsltForms_globals.refresh();
		XsltForms_globals.closeAction();
	}
};


		

XsltForms_control.getXFElement = function(element) {
	var xf = null;
	while (!xf && element) {
		xf = element.xfElement;
		if (xf && !xf.isControl) {
			xf = null;
		}
		element = element.parentNode;
	}
	return xf;
};


		

XsltForms_control.focusHandler = function() {
	var xf = XsltForms_control.getXFElement(this);
	if (XsltForms_globals.focus !== xf) {
		xf.focus(true);
	} else {
		XsltForms_globals.posibleBlur = false;
	}
};


		

XsltForms_control.blurHandler = function() {
	if (XsltForms_control.getXFElement(this) === XsltForms_globals.focus) {
		XsltForms_globals.posibleBlur = true;
		setTimeout(function(){XsltForms_globals.blur();}, 200);
	}
};
    
	
		
		
		
function XsltForms_group(subform, id, binding) {
	XsltForms_globals.counters.group++;
	this.init(subform, id);
	if (binding) {
		this.hasBinding = true;
		this.binding = binding;
	} else {
		XsltForms_browser.setClass(this.element, "xforms-disabled", false);
	}
}

XsltForms_group.prototype = new XsltForms_element();


		

XsltForms_group.prototype.dispose = function() {
	XsltForms_globals.counters.group--;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_group.prototype.clone = function(id) { 
	return new XsltForms_group(this.subform, id, this.binding);
};


		

XsltForms_group.prototype.build_ = function(ctx) {
	var nodes = this.evaluateBinding(this.binding, ctx);
	this.element.node = nodes[0];
	this.depsNodesRefresh.push(nodes[0]);
};


		

XsltForms_group.prototype.refresh = function() {
	var element = this.element;
	var disabled = !element.node || XsltForms_browser.getBoolMeta(element.node, "notrelevant");
	XsltForms_browser.setClass(element, "xforms-disabled", disabled);
	var ul = element.parentNode.children[0];
	if (ul.nodeName.toLowerCase() === "ul") {
		var childs = element.parentNode.children;
		var tab;
		for (var i = 1, len = childs.length; i < len; i++) {
			if (childs[i] === element) {
				tab = ul.childNodes[i - 1];
			}
		}
		XsltForms_browser.setClass(tab, "xforms-disabled", disabled);
	}
};

	
		
		
		
function XsltForms_input(subform, id, valoff, itype, binding, inputmode, incremental, delay, mediatype, aidButton, clone) {
	XsltForms_globals.counters.input++;
	this.init(subform, id);
	this.binding = binding;
	this.inputmode = typeof inputmode === "string"? XsltForms_input.InputMode[inputmode] : inputmode;
	this.incremental = incremental;
	this.delay = delay;
	this.timer = null;
	var cells = this.element.children;
	this.valoff = valoff;
	this.cell = cells[valoff];
	this.isClone = clone;
	this.hasBinding = true;
	this.itype = itype;
	this.bolAidButton = aidButton;
	this.mediatype = mediatype;
	this.initFocus(this.cell.children[0], true);
	if (aidButton) {
		this.aidButton = cells[valoff + 1].children[0];
		this.initFocus(this.aidButton);
	}
}

XsltForms_input.prototype = new XsltForms_control();


		

XsltForms_input.prototype.clone = function(id) { 
	return new XsltForms_input(this.subform, id, this.valoff, this.itype, this.binding, this.inputmode, this.incremental, this.delay, this.mediatype, this.bolAidButton, true);
};


		

XsltForms_input.prototype.dispose = function() {
	this.cell = null;
	this.calendarButton = null;
	XsltForms_globals.counters.input--;
	XsltForms_control.prototype.dispose.call(this);
};


		

XsltForms_input.prototype.initInput = function(type) {
	var cell = this.cell;
	var input = cell.firstChild;
	var tclass = type["class"];
	if (input.type === "password") {
		this.type = XsltForms_schema.getType("xsd_:string");
		this.initEvents(input, true);
	} else if (input.nodeName.toLowerCase() === "textarea") {
		this.type = type;
		if (this.mediatype === "application/xhtml+xml" && type.rte && type.rte.toLowerCase() === "tinymce") {
			input.id = this.element.id + "_textarea";
			var initinfo;
			eval("initinfo = " + (type.appinfo ? type.appinfo : "{}"));
			initinfo.mode = "none";
			initinfo.setup = function(ed) {
				ed.onKeyUp.add(function(ed) {
					XsltForms_control.getXFElement(document.getElementById(ed.id)).valueChanged(ed.getContent() || "");
				});
				ed.onChange.add(function(ed) {
					XsltForms_control.getXFElement(document.getElementById(ed.id)).valueChanged(ed.getContent() || "");
				});
				ed.onUndo.add(function(ed) {
					XsltForms_control.getXFElement(document.getElementById(ed.id)).valueChanged(ed.getContent() || "");
				});
				ed.onRedo.add(function(ed) {
					XsltForms_control.getXFElement(document.getElementById(ed.id)).valueChanged(ed.getContent() || "");
				});
			};
			tinyMCE.init(initinfo);
			tinyMCE.execCommand("mceAddControl", true, input.id);
		}
		this.initEvents(input, false);
	} else if (type !== this.type) {
		this.type = type;
		if (tclass === "boolean" || this.itype !== input.type) {
			while (cell.firstChild) {
				cell.removeChild(cell.firstChild);
			}
		} else {
			for (var i = cell.childNodes.length - 1; i >= 1; i--) {
				cell.removeChild(cell.childNodes[i]);
			}
		}
		if (tclass === "boolean") {
			input = XsltForms_browser.createElement("input");
			input.type = "checkbox";
			cell.appendChild(input);
		} else {
			if(this.itype !== input.type) {
				input = XsltForms_browser.createElement("input", cell, null, "xforms-value");
			}
			this.initEvents(input, (this.itype === "text"));
			if (tclass === "date" || tclass === "datetime") {
				this.calendarButton = XsltForms_browser.createElement("button", cell, "...", "aid-button");
				this.calendarButton.setAttribute("type", "button");
				this.initFocus(this.calendarButton);
			} else if (tclass === "number") {
				input.style.textAlign = "right";
			}
			var max = type.getMaxLength();
			if (max) {
				input.maxLength = max;
			} else {
				input.removeAttribute("maxLength");
			}
			var length = type.getDisplayLength();
			if (length) { 
				input.size = length;
			} else { 
				input.removeAttribute("size");
			}
		}
	}
	this.initFocus(input, true);
	this.input = input;
};


		

XsltForms_input.prototype.setValue = function(value) {
	var node = this.element.node;
	var type = node ? XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string") : XsltForms_schema.getType("xsd_:string");
	if (!this.input || type !== this.type) {
		this.initInput(type);
		this.changeReadonly();
	}
	if (type["class"] === "boolean") {
		this.input.checked = value === "true";
	} else if (this.type.rte && this.type.rte.toLowerCase() === "tinymce" && tinyMCE.get(this.input.id) && tinyMCE.get(this.input.id).getContent() !== value) {
		tinyMCE.get(this.input.id).setContent(value);
	} else if (this.input.value !== value) { // && this !== XsltForms_globals.focus) {
		this.input.value = value || "";
	}
};


		

XsltForms_input.prototype.changeReadonly = function() {
	if (this.input) {
		this.input.readOnly = this.readonly;
		if (this.calendarButton) {
			this.calendarButton.style.display = this.readonly ? "none" : "";
		}
	}
};


		

XsltForms_input.prototype.initEvents = function(input, canActivate) {
	if (this.inputmode) {
		XsltForms_browser.events.attach(input, "keyup", XsltForms_input.keyUpInputMode);
	}
	if (canActivate) {
		XsltForms_browser.events.attach(input, "keydown", XsltForms_input.keyDownActivate);
		XsltForms_browser.events.attach(input, "keypress", XsltForms_input.keyPressActivate);
		if (this.incremental) {
			XsltForms_browser.events.attach(input, "keyup", XsltForms_input.keyUpIncrementalActivate);
		} else {
			XsltForms_browser.events.attach(input, "keyup", XsltForms_input.keyUpActivate);
		}
	} else {
		if (this.incremental) {
			XsltForms_browser.events.attach(input, "keyup", XsltForms_input.keyUpIncremental);
		}
	}
};


		

XsltForms_input.prototype.blur = function(target) {
	XsltForms_globals.focus = null;
	var input = this.input;
	var value;
	if (!this.incremental) {
		XsltForms_browser.assert(input, this.element.id);
		value = input.type === "checkbox"? (input.checked? "true" : "false") : input.nodeName.toLowerCase() !== "textarea" ? input.value : (this.type.rte && this.type.rte.toLowerCase() === "tinymce") ? tinyMCE.get(input.id).getContent() : input.value;
		this.valueChanged(value);
	} else {
		var node = this.element.node;
		value = input.value;
		if (value && value.length > 0 && XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string").format) {
			try { input.value = XsltForms_browser.getValue(node, true); } catch(e) { }
		}
		if (this.timer) {
			window.clearTimeout(this.timer);
			this.timer = null;
		}
	}
};


		

XsltForms_input.prototype.click = function(target) {
	if (target === this.aidButton) {
		XsltForms_globals.openAction();
		XsltForms_xmlevents.dispatch(this, "ajx-aid");
		XsltForms_globals.closeAction();
	} else if (target === this.input && this.type["class"] === "boolean") {
		XsltForms_globals.openAction();
		this.valueChanged(target.checked? "true" : "false");
		XsltForms_xmlevents.dispatch(this, "DOMActivate");
		XsltForms_globals.closeAction();
	} else if (target === this.calendarButton) {
		XsltForms_calendar.show(target.previousSibling, this.type["class"] === "datetime"? XsltForms_calendar.SECONDS : XsltForms_calendar.ONLY_DATE);
	}
};


		

XsltForms_input.keyUpInputMode = function() {
	var xf = XsltForms_control.getXFElement(this);
	this.value = xf.inputmode(this.value);
};


		

XsltForms_input.keyDownActivate = function(a) {
	this.keyDownCode = a.keyCode;
};


		

XsltForms_input.keyPressActivate = function(a) {
	this.keyPressCode = a.keyCode;
};


		

XsltForms_input.keyUpActivate = function(a) {
	var xf = XsltForms_control.getXFElement(this);
	if (a.keyCode === 13 && (this.keyDownCode === 13 || this.keyPressCode === 13)) {
		XsltForms_globals.openAction();
		xf.valueChanged(this.value || "");
		XsltForms_xmlevents.dispatch(xf, "DOMActivate");
		XsltForms_globals.closeAction();
	}
	this.keyDownCode = this.keyPressCode = null;
};


		

XsltForms_input.keyUpIncrementalActivate = function(a) {
	var xf = XsltForms_control.getXFElement(this);
	if (a.keyCode === 13 && (this.keyDownCode === 13 || this.keyPressCode === 13)) {
		XsltForms_globals.openAction();
		xf.valueChanged(this.value || "");
		XsltForms_xmlevents.dispatch(xf, "DOMActivate");
		XsltForms_globals.closeAction();
	} else {
		if (xf.delay && xf.delay > 0) {
			if (xf.timer) {
				window.clearTimeout(xf.timer);
			}
			xf.timer = window.setTimeout("XsltForms_globals.openAction();document.getElementById('" + xf.element.id + "').xfElement.valueChanged('" + (this.value.addslashes() || "") + "');XsltForms_globals.closeAction();", xf.delay);
		} else {
			XsltForms_globals.openAction();
			xf.valueChanged(this.value || "");
			XsltForms_globals.closeAction();
		}
	}
	this.keyDownCode = this.keyPressCode = null;
};


		

XsltForms_input.keyUpIncremental = function() {
	var xf = XsltForms_control.getXFElement(this);
	if (xf.delay && xf.delay > 0) {
		if (xf.timer) {
			window.clearTimeout(xf.timer);
		}
		xf.timer = window.setTimeout("XsltForms_globals.openAction();document.getElementById('" + xf.element.id + "').xfElement.valueChanged('" + (this.value.addslashes() || "") + "');XsltForms_globals.closeAction();", xf.delay);
	} else {
		XsltForms_globals.openAction();
		xf.valueChanged(this.value || "");
		XsltForms_globals.closeAction();
	}
};


		

XsltForms_input.InputMode = {
	lowerCase : function(value) { return value.toLowerCase(); },
	upperCase : function(value) { return value.toUpperCase(); },
	titleCase : function(value) { return value.charAt(0).toUpperCase() + value.substring(1).toLowerCase(); },
	digits : function(value) {
		if (/^[0-9]*$/.exec(value)) {
			return value;
		} else {
			alert("Character not valid");
			var digits = "1234567890";
			var newValue = "";
			for (var i = 0, len = value.length; i < len; i++) {
				if (digits.indexOf(value.charAt(i)) !== -1) {
					newValue += value.charAt(i);
				}
			}
			return newValue;
		}
	}
};

	
		
		
		
function XsltForms_item(subform, id, bindingL, bindingV) {
	XsltForms_globals.counters.item++;
	this.init(subform, id);
	if (bindingL || bindingV) {
		this.hasBinding = true;
		this.bindingL = bindingL;
		this.bindingV = bindingV;
	} else {
		XsltForms_browser.setClass(this.element, "xforms-disabled", false);
	}
	var element = this.element;
	if (element.nodeName.toLowerCase() !== "option") {
		this.input = XsltForms_browser.isXhtml ? element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input")[0] : element.getElementsByTagName("input")[0];
		this.input.name = XsltForms_control.getXFElement(this.element).element.id;
		XsltForms_browser.events.attach(this.input, "focus", XsltForms_control.focusHandler);
		XsltForms_browser.events.attach(this.input, "blur", XsltForms_control.blurHandler);
		this.label = XsltForms_browser.isXhtml ? element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "span")[0] : element.getElementsByTagName("span")[0];
	}
}

XsltForms_item.prototype = new XsltForms_element();


		

XsltForms_item.prototype.clone = function(id) { 
	return new XsltForms_item(this.subform, id, this.bindingL, this.bindingV);
};


		

XsltForms_item.prototype.dispose = function() {
	this.input = null;
	this.label = null;
	XsltForms_globals.counters.item--;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_item.prototype.build_ = function(ctx) {
	var element = this.element;
	var xf = element.parentNode.xfElement;
	if (xf && xf.isRepeat) {
		ctx = element.node;
	} else {
		element.node = ctx;
	}
	if (this.bindingL) {
		element.nodeL = this.evaluateBinding(this.bindingL, ctx)[0];
		this.depsNodesRefresh.push(element.nodeL);
	}
	if (this.bindingV) {
		element.nodeV = this.evaluateBinding(this.bindingV, ctx)[0];
		this.depsNodesRefresh.push(element.nodeV);
	}
};


		

XsltForms_item.prototype.refresh = function() {
	var element = this.element;
	if (element.nodeName.toLowerCase() === "option") {
		if (element.nodeL) {
			try { 
				element.text = XsltForms_browser.getValue(element.nodeL, true);
			} catch(e) {
			}
		}
		if (element.nodeV) {
			try {
				element.value = XsltForms_browser.getValue(element.nodeV);
			} catch(e2) {
			}
		}
	} else {
		if (element.nodeL) {
			XsltForms_browser.setValue(this.label, XsltForms_browser.getValue(element.nodeL, true));
		}
		if (element.nodeV) {
			this.input.value = XsltForms_browser.getValue(element.nodeV);
		}
	}
};


		

XsltForms_item.prototype.click = function (target) {
	var input = this.input;
	if (input) {
		var xf = XsltForms_control.getXFElement(this.element);
		if (!xf.element.node.readonly && target === input) {
			xf.itemClick(input.value);
		}
	}
};
    
	
		
		
		
function XsltForms_itemset(subform, id, nodesetBinding, labelBinding, valueBinding) {
	XsltForms_globals.counters.itemset++;
	this.init(subform, id);
	this.nodesetBinding = nodesetBinding;
	this.labelBinding = labelBinding;
	this.valueBinding = valueBinding;
	this.hasBinding = true;
}

XsltForms_itemset.prototype = new XsltForms_element();


		

XsltForms_itemset.prototype.build_ = function(ctx) {
	if (this.element.getAttribute("cloned")) {
		return;
	}
	this.nodes = this.evaluateBinding(this.nodesetBinding, ctx);
	var next = this.element;
	var parentNode = next.parentNode;
	var length = this.nodes.length;
	var oldNode = next;
	var listeners = next.listeners;
	for (var cont = 1; true;) {
		next = next.nextSibling;
		if (next && next.getAttribute("cloned")) {
			if (cont >= length) {
				next.listeners = null;
				parentNode.removeChild(next);
				next = oldNode;
			} else {
				next.node = this.nodes[cont];
				this.refresh_(next, cont++);
				oldNode = next;
			}
		} else {
			for (var i = cont; i < length; i++) {
				var node = this.element.cloneNode(true);
				node.setAttribute("cloned", "true");
				XsltForms_idManager.cloneId(node);
				node.node = this.nodes[i];
				parentNode.appendChild(node);
				this.refresh_(node, i);
				if (listeners && !XsltForms_browser.isIE) {
					for (var j = 0, len = listeners.length; j < len; j++) {
						listeners[j].clone(node);
					}
				}
			}
			break;
		}
	}
	if (length > 0) {
		this.element.node = this.nodes[0];
		this.refresh_(this.element, 0);
	} else {
		this.element.value = "\xA0";
		this.element.text = "\xA0";
	}
};


		

XsltForms_itemset.prototype.refresh = function() {
	var parent = this.element.parentNode;
	var i = 0;
	while (parent.childNodes[i] !== this.element) {
		i++;
	}
	for (var j = 0, len = this.nodes.length; j < len || j === 0; j++) {
		XsltForms_browser.setClass(parent.childNodes[i+j], "xforms-disabled", this.nodes.length === 0);
	}
};


		

XsltForms_itemset.prototype.clone = function(id) {
	return new XsltForms_itemset(this.subform, id, this.nodesetBinding, this.labelBinding, this.valueBinding);
};


		

XsltForms_itemset.prototype.dispose = function() {
	XsltForms_globals.counters.itemset--;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_itemset.prototype.refresh_ = function(element, cont) {
	var ctx = this.nodes[cont];
	var nodeLabel = this.evaluateBinding(this.labelBinding, ctx)[0];
	var nodeValue = this.evaluateBinding(this.valueBinding, ctx)[0];
	if (nodeLabel) {
		this.depsNodesRefresh.push(nodeLabel);
		try {
			element.text = XsltForms_browser.getValue(nodeLabel, true);
		} catch(e) {
		}
	}
	if (nodeValue) {
		this.depsNodesRefresh.push(nodeValue);
		try {
			element.value = XsltForms_browser.getValue(nodeValue);
		} catch(e2) {
		}
	}
};

	
		
		
		
function XsltForms_label(subform, id, binding) {
	XsltForms_globals.counters.label++;
	this.init(subform, id);
	if (binding) {
		this.hasBinding = true;
		this.binding = binding;
	}
}

XsltForms_label.prototype = new XsltForms_element();


		

XsltForms_label.prototype.clone = function(id) { 
	return new XsltForms_label(this.subform, id, this.binding);
};


		

XsltForms_label.prototype.dispose = function() {
	XsltForms_globals.counters.label--;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_label.prototype.build_ = function(ctx) {
	var nodes = this.evaluateBinding(this.binding, ctx);
	this.element.node = nodes[0];
	this.depsNodesRefresh.push(nodes[0]);
};


		

XsltForms_label.prototype.refresh = function() {
	var node = this.element.node;
	var value = node? XsltForms_browser.getValue(node, true) : "";
	XsltForms_browser.setValue(this.element, value);
};
    
	
		
		
		
function XsltForms_output(subform, id, valoff, binding, mediatype) {
	XsltForms_globals.counters.output++;
	this.init(subform, id);
	this.valoff = valoff;
	if (this.element.children.length !== 0) {
		var cells = this.element.children;
		this.valueElement = cells[valoff];
	} else {
		this.valueElement = this.element;
	}
	if (this.valueElement.children.length !== 0) {
		this.valueElement = this.valueElement.children[0];
	}
	this.hasBinding = true;
	this.binding = binding;
	this.mediatype = mediatype;
	this.complex = mediatype === "application/xhtml+xml";
	this.isOutput = true;
	if (this.binding && this.binding.isvalue) {
		XsltForms_browser.setClass(this.element, "xforms-disabled", false);
	}
}

XsltForms_output.prototype = new XsltForms_control();


		

XsltForms_output.prototype.clone = function(id) { 
	return new XsltForms_output(this.subform, id, this.valoff, this.binding, this.mediatype);
};


		

XsltForms_output.prototype.dispose = function() {
	this.valueElement = null;
	XsltForms_globals.counters.output--;
	XsltForms_control.prototype.dispose.call(this);
};


		

XsltForms_output.prototype.setValue = function(value) {
	var node = this.element.node;
	var element = this.valueElement;
	if (element.nodeName.toLowerCase() === "span") {
		if (this.mediatype === "application/xhtml+xml") {
			while (element.firstChild) {
				element.removeChild(element.firstChild);
			}
			if (value) {
				element.innerHTML = value;
			}
		} else if (this.mediatype === "image/svg+xml") {
			while (element.firstChild) {
				element.removeChild(element.firstChild);
			}
			if (XsltForms_browser.isIE && !XsltForms_browser.isIE9) {
				var xamlScript = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
				xamlScript.setAttribute("type", "text/xaml");
				xamlScript.setAttribute("id", this.element.id+"-xaml");
				xamlScript.text = XsltForms_browser.transformText(value, XsltForms_browser.ROOT + "svg2xaml.xsl", false, "width", element.currentStyle.width, "height", element.currentStyle.height);
				element.appendChild(xamlScript);
				var xamlObject = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "object") : document.createElement("object");
				xamlObject.setAttribute("width", element.currentStyle.width+"px");
				xamlObject.setAttribute("height", element.currentStyle.height+"px");
				xamlObject.setAttribute("type", "application/x-silverlight");
				xamlObject.setAttribute("style", "min-width: " + element.currentStyle.width+"px");
				//xamlObject.setAttribute("style", "min-width: " + xamlScript.text.substring(xamlScript.text.indexOf('<Canvas Width="')+15,xamlScript.text.indexOf('" Height="')) + "px");
				var xamlParamSource = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "param") : document.createElement("param");
				xamlParamSource.setAttribute("name", "source");
				xamlParamSource.setAttribute("value", "#"+this.element.id+"-xaml");
				xamlObject.appendChild(xamlParamSource);
				var xamlParamOnload = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "param") : document.createElement("param");
				xamlParamOnload.setAttribute("name", "onload");
				xamlParamOnload.setAttribute("value", "onLoaded");
				xamlObject.appendChild(xamlParamOnload);
				var xamlParamIswindowless = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "param") : document.createElement("param");
				xamlParamIswindowless.setAttribute("name", "iswindowless");
				xamlParamIswindowless.setAttribute("value", "true");
				xamlObject.appendChild(xamlParamIswindowless);
				element.appendChild(xamlObject);
			} else if (XsltForms_browser.isXhtml) {
				var cs = window.getComputedStyle(element, null);
				XDocument.parse(value, element);
				element.firstChild.setAttribute("width", cs.getPropertyValue("min-width"));
				element.firstChild.setAttribute("height", cs.getPropertyValue("min-height"));
			} else {
				var svgObject = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "object") : document.createElement("object");
				svgObject.setAttribute("type", "image/svg+xml");
				svgObject.setAttribute("data", "data:image/svg+xml,"+ value);
				//svgObject.setAttribute("height", "400px");
				element.appendChild(svgObject);
			}
		} else {
			XsltForms_browser.setValue(element, value);
		}
	} else {
		element.src = value;
	}
};

		

XsltForms_output.prototype.getValue = function(format) {
	var node = this.element.node;
	var element = this.valueElement;
	if (element.nodeName.toLowerCase() === "span") {
		return XsltForms_browser.getValue(element, format);
	} else {
		var value = element.src;
		if (value && format && element.type.format) {
			try { 
				value = element.type.format(value);
			} catch(e) { 
			}
		}
		return value;
	}
};

	
		
		
		
function XsltForms_repeat(subform, id, binding, clone) {
	XsltForms_globals.counters.repeat++;
	this.init(subform, id);
	this.binding = binding;
	this.index = 1;
	var el = this.element;
	this.isRepeat = true;
	this.hasBinding = true;
	this.root = XsltForms_browser.hasClass(el, "xforms-control")? el.lastChild : el;
	this.isItemset = XsltForms_browser.hasClass(el, "xforms-itemset");
}

XsltForms_repeat.prototype = new XsltForms_element();


		

XsltForms_repeat.prototype.dispose = function() {
	this.root = null;
	XsltForms_globals.counters.repeat--;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_repeat.prototype.setIndex = function(index) {
	if (this.index !== index) {
		var node = this.nodes[index - 1];
		if (node) {    
			XsltForms_globals.openAction();
			this.index = index;
			this.element.node = node;
			XsltForms_globals.addChange(this);
			XsltForms_globals.addChange(document.getElementById(XsltForms_browser.getMeta(node.ownerDocument.documentElement, "model")).xfElement);
			XsltForms_globals.closeAction();
		}
	}
};


		

XsltForms_repeat.prototype.deleteNode = function(node) {
	var newNodes = [];
	var nodes = this.nodes;
	for (var i = 0, len = nodes.length; i < len; i++) {
		if (node !== nodes[i]) {
			newNodes.push(nodes[i]);
		}
	}
	this.nodes = newNodes;
	this.setIndex(this.index === nodes.length? this.index - 1 : this.index);
};


		

XsltForms_repeat.prototype.insertNode = function(node, nodeAfter) {
	var nodes = this.nodes;
	if (nodeAfter) {
		var newNodes = [];
		var index = 1;
		for (var i = 0, len = nodes.length; i < len; i++) {
			if (nodeAfter === nodes[i]) {
				newNodes.push(node);
				index = i + 1;
			}
			newNodes.push(nodes[i]);
		}
		this.nodes = newNodes;
		this.setIndex(index);
	} else {
		nodes.push(node);
		this.setIndex(nodes.length);
	}
};


		

XsltForms_repeat.prototype.build_ = function(ctx) {
	var nodes = this.evaluateBinding(this.binding, ctx);
	var r = this.root;
	var l = r.children.length;
	this.nodes = nodes;
	var n = nodes.length;
	XsltForms_repeat.forceOldId(r.children[0]);
	while (r.children[0] !== r.firstChild) {
		r.removeChild(r.firstChild);
	}
	for (var i = l; i < n; i++) {
		var child = r.children[0].cloneNode(true);
		r.appendChild(child);
		XsltForms_repeat.initClone(child);
	}
	for (var j = n; j < l && r.childNodes.length > 1; j++) {
		XsltForms_globals.dispose(r.lastChild);
		r.removeChild(r.lastChild);
	}
	for (var k = 0; k < n; k++) {
		XsltForms_browser.setMeta(nodes[k], "repeat", this.element.id);
		r.children[k].node = nodes[k];
		//XsltForms_browser.debugConsole.write("XFRepeat: " + r.children[k].id + " -> " + nodes[k].localName + " " + nodes[k].getAttribute("id"));
	}
	if (this.index > n) {
		this.index = 1;
	}
	this.element.node = nodes[this.index - 1];
	//XsltForms_browser.debugConsole.write("XFRepeat: " + this.element.id + " -> " + this.element.node.localName + " " + this.element.node.getAttribute("id"));
};


		

XsltForms_repeat.prototype.refresh = function(selected) {
	var empty = this.nodes.length === 0;
	XsltForms_browser.setClass(this.element, "xforms-disabled", empty);
	if (!empty && !this.isItemset) {
		var childs = this.root.children;
		for (var i = 0, len = childs.length; i < len; i++) {
			var sel = selected && (this.index === i + 1);
			childs[i].selected = sel;
			XsltForms_browser.setClass(childs[i], "xforms-repeat-item-selected", sel);
		}
	}
};


		

XsltForms_repeat.prototype.clone = function(id) { 
	return new XsltForms_repeat(this.subform, id, this.binding, true);
};


		

XsltForms_repeat.initClone = function(element) {
	var id = element.id;
	if (id) {
		XsltForms_idManager.cloneId(element);
		var oldid = element.getAttribute("oldid");
		var original = document.getElementById(oldid);
		var xf = original.xfElement;
		if (xf) {
			xf.clone(element.id);
		}
		var listeners = original.listeners;
		if (listeners && (!XsltForms_browser.isIE || XsltForms_browser.isIE9)) {
			for (var i = 0, len = listeners.length; i < len; i++) {
				listeners[i].clone(element);
			}
		}
	}
	var next = element.firstChild;
	while (next) {
		var child = next;
		next = next.nextSibling;
		if (child.id && child.getAttribute("cloned")) {
			element.removeChild(child);
		} else {
			XsltForms_repeat.initClone(child);
		}
	}
};


		

XsltForms_repeat.forceOldId = function(element) {
	var id = element.id;
	if (id) {
		if (id.substr(0, 8) === "clonedId") {
			return;
		}
		element.setAttribute("oldid", id);
	}
	var next = element.firstChild;
	while (next) {
		var child = next;
		next = next.nextSibling;
		XsltForms_repeat.forceOldId(child);
	}
};


		

XsltForms_repeat.selectItem = function(element) {
	var par = element.parentNode;
	if (par) {
		var repeat = par.xfElement? par : par.parentNode;
		var childs = par.children;
		XsltForms_browser.assert(repeat.xfElement, element.nodeName +  " - " + repeat.nodeName);
		for (var i = 0, len = childs.length; i < len; i++) {
			if (childs[i] === element) {
				repeat.xfElement.setIndex(i + 1);
				break;
			}
		}
	}
};

	
		
		
		
function XsltForms_select(subform, id, multiple, full, binding, incremental, clone) {
	XsltForms_globals.counters.select++;
	this.init(subform, id);
	this.binding = binding;
	this.multiple = multiple;
	this.full = full;
	this.incremental = incremental;
	this.isClone = clone;
	this.hasBinding = true;
	this.outRange = false;
	if (!this.full) {
		this.select = XsltForms_browser.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "select")[0] : this.element.getElementsByTagName("select")[0];
		this.initFocus(this.select);
		XsltForms_browser.events.attach(this.select, "change", incremental? XsltForms_select.incrementalChange : XsltForms_select.normalChange);
	}
}

XsltForms_select.prototype = new XsltForms_control();


		

XsltForms_select.prototype.clone = function(id) { 
	return new XsltForms_select(this.subform, id, this.multiple, this.full, this.binding, this.incremental, true);
};


		

XsltForms_select.prototype.dispose = function() {
	this.select = null;
	this.selectedOptions = null;
	XsltForms_globals.counters.select--;
	XsltForms_control.prototype.dispose.call(this);
};


		

XsltForms_select.prototype.focusFirst = function() {
	var input = XsltForms_browser.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input")[0] : this.element.getElementsByTagName("input")[0];
	input.focus();
	if (XsltForms_browser.isOpera) {
		input.focus();
	}
};


		

XsltForms_select.prototype.setValue = function(value) {
	if (!this.full && (!value || value === "")) {
		this.selectedOptions = [];
		if (this.select.options[0] && this.select.options[0].value !== "\xA0") {
			var empty = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "option") : document.createElement("option");
			empty.value = "\xA0";
			empty.text = "\xA0";
			empty.id = "";
			if (this.select.children[0]) {
				this.select.insertBefore(empty, this.select.children[0]);
			} else {
				this.select.appendChild(empty);
			}
			this.select.selectedIndex = 0;
		}
	} else {
		if (!this.full && this.select.firstChild.value === "\xA0") {
			this.select.removeChild(this.select.firstChild);
		}
		var vals = value? (this.multiple? value.split(XsltForms_globals.valuesSeparator) : [value]) : [""];
		var list = this.full? (XsltForms_browser.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input") : this.element.getElementsByTagName("input")) : this.select.options;
		var well = true;
		for (var i = 0, len = vals.length; well && i < len; i++) {
			var val = vals[i];
			var found = false;
			for (var j = 0, len1 = list.length; !found && j < len1; j++) {
				if (list[j].value === val) {
					found = true;
				}
			}
			well = found;
		}
		if (well || (this.multiple && !value)) {
			if (this.outRange) {
				this.outRange = false;
				XsltForms_xmlevents.dispatch(this, "xforms-in-range");
			}
		} else if ((!this.multiple || value) && !this.outRange) {
			this.outRange = true;
			XsltForms_xmlevents.dispatch(this, "xforms-out-of-range");
		}
		vals = this.multiple? vals : [vals[0]];
		var readonly = this.element.node.readonly;
		var item;
		if (this.full) {
			for (var n = 0, len2 = list.length; n < len2; n++) {
				item = list[n];
				item.checked = XsltForms_browser.inArray(item.value, vals);
			}
		} else {
			this.selectedOptions = [];
			for (var k = 0, len3 = list.length; k < len3; k++) {
				item = list[k];
				var b = XsltForms_browser.inArray(item.value, vals);
				if (b) {
					this.selectedOptions.push(item);
				}
				try {
					item.selected = b;
				} catch(e) {
				}
			}
		}
	}
};


		

XsltForms_select.prototype.changeReadonly = function() {
	if (this.full) {
		var list = XsltForms_browser.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input") : this.element.getElementsByTagName("input");
		for (var i = 0, len = list.length; i < len; i++) {
			list[i].disabled = this.readonly;
		}
	} else {
		if (!XsltForms_browser.dialog.knownSelect(this.select)) {
			this.select.disabled = this.readonly;
		}
	}
};


		

XsltForms_select.prototype.itemClick = function(value) {
	var inputs = XsltForms_browser.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input") : this.element.getElementsByTagName("input");
	var input;
	XsltForms_globals.openAction();
	if (this.multiple) {
		var newValue = null;
		for (var i = 0, len = inputs.length; i < len; i++) {
			input = inputs[i];
			if (input.value === value) {
				XsltForms_xmlevents.dispatch(input.parentNode, input.checked? "xforms-select" : "xforms-deselect");
			}
			if (input.checked) {
				newValue = (newValue ? newValue + XsltForms_globals.valuesSeparator : "") + input.value;
			}
		}
		value = newValue;
	} else {
		var old = this.value || XsltForms_browser.getValue(this.element.node);
		var inputSelected = null;
		if (old === value) {
			XsltForms_globals.closeAction();
			return;
		}
		for (var j = 0, len1 = inputs.length; j < len1; j++) {
			input = inputs[j];
			input.checked = input.value === value;
			if (input.value === old) {
				XsltForms_xmlevents.dispatch(input.parentNode, "xforms-deselect");
			} else if (input.value === value) {
				inputSelected = input;
			}
		}
		XsltForms_xmlevents.dispatch(inputSelected.parentNode, "xforms-select");
	}
	if (this.incremental) {
		this.valueChanged(value || "");
	} else {
		this.value = value || "";
	}
	XsltForms_globals.closeAction();
};


		

XsltForms_select.prototype.blur = function(evt) {
	if (this.value) {
		XsltForms_globals.openAction();
		this.valueChanged(this.value);
		XsltForms_globals.closeAction();
		this.value = null;
	}
};


		

XsltForms_select.normalChange = function(evt) {
	var xf = XsltForms_control.getXFElement(this);
	var news = [];
	var value = "";
	var old = xf.getSelected();
	var opts = this.options;
	XsltForms_globals.openAction();
	for (var i = 0, len = old.length; i < len; i++) {
		if (old[i].selected) {
			news.push(old[i]);
		} else {
			XsltForms_xmlevents.dispatch(old[i], "xforms-deselect");
		}
	}
	var opt;
	for (var j = 0, len1 = opts.length; j < len1; j++) {
		opt = opts[j];
		if (opt.selected) {
			value = value? value + XsltForms_globals.valuesSeparator + opt.value : opt.value;
		}
	}
	for (var k = 0, len2 = opts.length; k < len2; k++) {
		opt = opts[k];
		if (opt.selected) {
			if (!XsltForms_browser.inArray(opt, news)) {
				news.push(opt);
				XsltForms_xmlevents.dispatch(opt, "xforms-select");
			}
		}
	}
	xf.value = value;
	xf.selectedOptions = news;
	XsltForms_globals.closeAction();
};


		

XsltForms_select.incrementalChange = function(evt) {
	var xf = XsltForms_control.getXFElement(this);
	XsltForms_globals.openAction();
	XsltForms_select.normalChange.call(this, evt);
	xf.valueChanged(xf.value);
	XsltForms_globals.closeAction();
};


		

XsltForms_select.prototype.getSelected = function() {
	var s = this.selectedOptions;
	if (!s) {
		s = [];
		var opts = this.select.options;
		for (var i = 0, len = opts.length; i < len; i++) {
			if (opts[i].selected) {
				s.push(opts[i]);
			}
		}
	}
	return s;
};

	
		
		
		
function XsltForms_trigger(subform, id, binding, clone) {
	XsltForms_globals.counters.trigger++;
	this.init(subform, id);
	this.binding = binding;
	this.hasBinding = !!binding;
	if(!this.hasBinding) {
		XsltForms_browser.setClass(this.element, "xforms-disabled", false);
	}
	this.isTrigger = true;
	var button = XsltForms_browser.isXhtml ? (this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "a")[0] || this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "button")[0]) : (this.element.getElementsByTagName("a")[0] || this.element.getElementsByTagName("button")[0]);
	this.input = button;
	this.initFocus(button);
}

XsltForms_trigger.prototype = new XsltForms_control();


		

XsltForms_trigger.prototype.setValue = function () { };


		

XsltForms_trigger.prototype.changeReadonly = function() {
	this.input.disabled = this.readonly;
};


		

XsltForms_trigger.prototype.clone = function (id) {
	return new XsltForms_trigger(this.subform, id, this.binding, true);
};


		

XsltForms_trigger.prototype.dispose = function() {
	XsltForms_globals.counters.trigger--;
	XsltForms_element.prototype.dispose.call(this);
};


		

XsltForms_trigger.prototype.click = function () {
	XsltForms_globals.openAction();
	XsltForms_xmlevents.dispatch(this, "DOMActivate");
	XsltForms_globals.closeAction();
};


		

XsltForms_trigger.prototype.blur = function () { };

	
		
		
		
function XsltForms_calendar() {
	var calendar = this;
	var body = XsltForms_browser.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
	this.element = XsltForms_browser.createElement("table", body, null, "calendar");
	var tHead = XsltForms_browser.createElement("thead", this.element);
	var trTitle = XsltForms_browser.createElement("tr", tHead);
	var title = XsltForms_browser.createElement("td", trTitle, null, "title");
	title.colSpan = 7;
	this.selectMonth = XsltForms_browser.createElement("select", title);
	XsltForms_browser.events.attach(this.selectMonth, "change", function() {
		XsltForms_calendar.INSTANCE.refresh();
	} );
	for (var i = 0; i < 12; i++) {
		var o = XsltForms_browser.createElement("option", this.selectMonth, XsltForms_browser.i18n.get("calendar.month" + i));
		o.setAttribute("value", i);
	}
	this.inputYear = XsltForms_browser.createElement("input", title);
	this.inputYear.readOnly = true;
	XsltForms_browser.events.attach(this.inputYear, "mouseup", function() {
		var cal = XsltForms_calendar.INSTANCE;
		cal.yearList.show();
	} );
	XsltForms_browser.events.attach(this.inputYear, "change", function() {
		XsltForms_calendar.INSTANCE.refresh();
	} );
	var close = XsltForms_browser.createElement("button", title, "X");
	close.setAttribute("type", "button");
	close.setAttribute("title", "Close");
	XsltForms_browser.events.attach(close, "click", function() {
		XsltForms_calendar.close();
	} );
	var trDays = XsltForms_browser.createElement("tr", tHead, null, "names");
	var ini = parseInt(XsltForms_browser.i18n.get("calendar.initDay"), 10);
	for (var j = 0; j < 7; j++) {
		var ind = (j + ini) % 7;
		this.createElement(trDays, "name", XsltForms_browser.i18n.get("calendar.day" + ind));
	}
	this.tBody = XsltForms_browser.createElement("tbody", this.element);
	var handler = function(event) {
		var value = XsltForms_browser.events.getTarget(event).childNodes[0].nodeValue;
		var cal = XsltForms_calendar.INSTANCE;
		if (value !== "") {
			cal.day = value;
			var date = new Date(cal.inputYear.value,cal.selectMonth.value,cal.day);
			if (cal.isTimestamp) {
				date.setSeconds(cal.inputSec.value);
				date.setMinutes(cal.inputMin.value);
				date.setHours(cal.inputHour.value);
				cal.input.value = XsltForms_browser.i18n.format(date, null, true);
			} else {
				cal.input.value = XsltForms_browser.i18n.formatDate(date);
			}
			XsltForms_calendar.close();
			XsltForms_browser.events.dispatch(cal.input, "keyup");
			cal.input.focus();
		}
	};
	for (var dtr = 0; dtr < 6; dtr++) {
		var trLine = XsltForms_browser.createElement("tr", this.tBody);
		for (var day = 0; day < 7; day++) {
			this.createElement(trLine, "day", " ", 1, handler);
		}
	}
	var tFoot = XsltForms_browser.createElement("tfoot", this.element);
	var trFoot = XsltForms_browser.createElement("tr", tFoot);
	var tdFoot = XsltForms_browser.createElement("td", trFoot);
	tdFoot.colSpan = 7;
	this.inputHour = XsltForms_browser.createElement("input", tdFoot);
	this.inputHour.readOnly = true;
	XsltForms_browser.events.attach(this.inputHour, "mouseup", function() {
		XsltForms_calendar.INSTANCE.hourList.show();
	} );
	tdFoot.appendChild(document.createTextNode(":"));
	this.inputMin = XsltForms_browser.createElement("input", tdFoot);
	this.inputMin.readOnly = true;
	XsltForms_browser.events.attach(this.inputMin, "mouseup", function() {
		XsltForms_calendar.INSTANCE.minList.show();
	} );
	tdFoot.appendChild(document.createTextNode(":"));
	this.inputSec = XsltForms_browser.createElement("input", tdFoot);
	this.inputSec.readOnly = true;
	XsltForms_browser.events.attach(this.inputSec, "mouseup", function() {
		if (XsltForms_calendar.INSTANCE.type >= XsltForms_calendar.SECONDS) {
			XsltForms_calendar.INSTANCE.secList.show();
		}
	} );
	this.yearList = new XsltForms_numberList(title, "calendarList", this.inputYear, 1900, 2050);
	this.hourList = new XsltForms_numberList(tdFoot, "calendarList", this.inputHour, 0, 23, 2);
	this.minList = new XsltForms_numberList(tdFoot, "calendarList", this.inputMin, 0, 59, 2);
	this.secList = new XsltForms_numberList(tdFoot, "calendarList", this.inputSec, 0, 59, 2);
}


		

XsltForms_calendar.prototype.today = function() {
	this.refreshControls(new Date());
};


		

XsltForms_calendar.prototype.refreshControls = function(date) {
	this.day = date.getDate();
	this.selectMonth.value = date.getMonth();
	this.inputYear.value = date.getYear() < 1000 ? 1900 + date.getYear() : date.getYear();
	if (this.isTimestamp) {
		this.inputHour.value = XsltForms_browser.zeros(date.getHours(), 2);
		this.inputMin.value = this.type >= XsltForms_calendar.MINUTES ? XsltForms_browser.zeros(date.getMinutes(), 2) : "00";
		this.inputSec.value = this.type >= XsltForms_calendar.SECONDS ? XsltForms_browser.zeros(date.getSeconds(), 2) : "00";
	}
	this.refresh();
};


		

XsltForms_calendar.prototype.refresh = function() {
	var firstDay = this.getFirstDay();
	var daysOfMonth = this.getDaysOfMonth();
	var ini = parseInt(XsltForms_browser.i18n.get("calendar.initDay"), 10);
	var cont = 0;
	var day = 1;
	var currentMonthYear = this.selectMonth.value === this.currentMonth && this.inputYear.value === this.currentYear;
	for (var i = 0; i < 6; i++) {
		var trLine = this.tBody.childNodes[i];
		for (var j = 0; j < 7; j++, cont++) {
			var cell = trLine.childNodes[j];
			var dayInMonth = (cont >= firstDay && cont < firstDay + daysOfMonth);
			XsltForms_browser.setClass(cell, "hover", false);
			XsltForms_browser.setClass(cell, "today", currentMonthYear && day === this.currentDay);
			XsltForms_browser.setClass(cell, "selected", dayInMonth && day === this.day);
			XsltForms_browser.setClass(cell, "weekend", (j+ini)%7 > 4);
			cell.firstChild.nodeValue = dayInMonth ? day++ : "";
		}
	}
};


		

XsltForms_calendar.prototype.getFirstDay = function() {
	var date = new Date();
	date.setDate(1);
	date.setMonth(this.selectMonth.value);
	date.setYear(this.inputYear.value);
	var ini = parseInt(XsltForms_browser.i18n.get("calendar.initDay"), 10);
	var d = date.getDay();
	return (d + (6 - ini)) % 7;
};


		

XsltForms_calendar.prototype.getDaysOfMonth = function() {
	var year = this.inputYear.value;
	var month = this.selectMonth.value;
	if (month === 1 && ((0 === (year % 4)) && ((0 !== (year % 100)) || (0 === (year % 400))))) {
		return 29;
	}
	return XsltForms_calendar.daysOfMonth[this.selectMonth.value];
};


		

XsltForms_calendar.prototype.createElement = function(parent, className, text, colspan, handler) {
	var element = XsltForms_browser.createElement("td", parent, text, className);
	if (colspan > 1) {
		element.colSpan = colspan;
	}
	if (handler) {
		XsltForms_browser.events.attach(element, "click", handler);
		XsltForms_browser.initHover(element);
	}
	return element;
};

XsltForms_calendar.daysOfMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

XsltForms_calendar.ONLY_DATE = 0;
XsltForms_calendar.HOURS = 1;
XsltForms_calendar.MINUTES = 2;
XsltForms_calendar.SECONDS = 3;


		

XsltForms_calendar.show = function(input, type) {
	var cal = XsltForms_calendar.INSTANCE;
	if (!cal) {
		cal = new XsltForms_calendar();
		XsltForms_calendar.INSTANCE = cal;
	}
	if (!type) {
		type = XsltForms_calendar.ONLY_DATE;
	}
	cal.input = input;
	cal.type = type;
	cal.isTimestamp = type !== XsltForms_calendar.ONLY_DATE;
	XsltForms_browser.setClass(cal.element, "date", !cal.isTimestamp);
	var date;
	try {
		date = cal.isTimestamp? XsltForms_browser.i18n.parse(input.value) : XsltForms_browser.i18n.parseDate(input.value);
	} catch (e) {
		date = new Date();
	}
	if (date) {
		cal.refreshControls(date);
	} else {
		cal.today();
	}
	XsltForms_browser.dialog.show(cal.element, input, false);
};


		

XsltForms_calendar.close = function() {
	var cal = XsltForms_calendar.INSTANCE;
	cal.yearList.close();
	XsltForms_browser.dialog.hide(cal.element, false);
};
    
	
	
		
		
		
		
		
function XsltForms_type() {
}


		

XsltForms_type.prototype.setSchema = function(schema) {
	this.schema = schema;
	return this;
};


		

XsltForms_type.prototype.setName = function(name) {
	this.name = name;
	this.nsuri = this.schema.ns;
	this.schema.types[name] = this;
	return this;
};


		

XsltForms_type.prototype.canonicalValue = function(value) {
	value = value.toString();
	switch (this.whiteSpace) {
		case "replace":
			value = value.replace(/[\t\r\n]/g, " ");
			break;
		case "collapse":
			value = value.replace(/[\t\r\n ]+/g, " ").replace(/^\s+|\s+$/g, "");
			break;
	}

	return value;
};


		

XsltForms_type.prototype.getMaxLength = function() {
	return this.maxLength ? this.maxLength : (this.length ? this.length : (this.totalDigits ? this.totalDigits + 1 : null));
};


		

XsltForms_type.prototype.getDisplayLength = function() {
	return this.displayLength;
};
    
	
		
		
		
function XsltForms_schema(subform, ns, name, prefixes) {
	XsltForms_browser.assert(ns && !XsltForms_schema.all[ns], "Needed schema name or exists one schema with that namespace");
	if(XsltForms_schema.all[ns]) {
		XsltForms_globals.error(this, "xforms-link-exception", "More than one schema with the same namespace declaration");
		return;
	}
	this.subform = subform;
	this.name = name;
	this.ns = ns;
	this.types = {};
	this.prefixes = prefixes || {};
	XsltForms_schema.all[ns] = this;
}


		

XsltForms_schema.all = {};


		

XsltForms_schema.prototype.getType = function(name) {
	if (name.indexOf(":") !== -1) {
		var res = name.split(":");
		var prefix = res[0];
		var ns = this.prefixes[prefix];
		if (ns) {
			return XsltForms_schema.getTypeNS(ns, res[1]);
		}
		return XsltForms_schema.getType(name);
	}
	var type = this.types[name];
	if (!type) {
		alert("Type " + name + " not defined");
		throw "Error";
	}
	return type;
};


		

XsltForms_schema.getType = function(name) {
	var res = name.split(":");
	if (typeof(res[1]) === "undefined") {
		return XsltForms_schema.getTypeNS(XsltForms_schema.prefixes.xforms, res[0]);
	} else {
		return XsltForms_schema.getTypeNS(XsltForms_schema.prefixes[res[0]], res[1]);
	}
};


		

XsltForms_schema.getTypeNS = function(ns, name) {
	var schema = XsltForms_schema.all[ns];
	if (!schema) {
		alert("Schema for namespace " + ns + " not defined for type " + name);
		throw "Error";
	}
	var type = schema.types[name];	
	if (!type) {
		alert("Type " + name + " not defined in namespace " + ns);
		throw "Error";
	}
	return type;
};


		

XsltForms_schema.get = function(subform, ns) {
	var schema = XsltForms_schema.all[ns];
	if (!schema) {
		schema = new XsltForms_schema(subform, ns);
	}
	return schema;
};


		

XsltForms_schema.prefixes = {
	"xsd_" : "http://www.w3.org/2001/XMLSchema",
	"xsd" : "http://www.w3.org/2001/XMLSchema",
	"xs" : "http://www.w3.org/2001/XMLSchema",
	"xf" : "http://www.w3.org/2002/xforms",
	"xform" : "http://www.w3.org/2002/xforms",
	"xforms" : "http://www.w3.org/2002/xforms",
	"xsltforms" : "http://www.agencexml.com/xsltforms"
};


		

XsltForms_schema.registerPrefix = function(prefix, namespace) {
	this.prefixes[prefix] = namespace;
};
    
	
		
		
		
function XsltForms_atomicType() {
	this.patterns = [];
}

XsltForms_atomicType.prototype = new XsltForms_type();


		

XsltForms_atomicType.prototype.setBase = function(base) {
	var baseType = typeof base === "string"? this.schema.getType(base) : base;
	for (var id in baseType)  {
		if (baseType.hasOwnProperty(id)) {
			var value = baseType[id];
			if (id === "patterns") {
				XsltForms_browser.copyArray(value, this.patterns);
			} else if (id !== "name" && id !== "nsuri") {
				this[id] = value;
			}
		}
	}
	return this;
};


		

XsltForms_atomicType.prototype.put = function(name, value) {
	if (name === "base") {
		this.setBase(value);
	} else if (name === "pattern") {
		XsltForms_browser.copyArray([value], this.patterns);
	} else {
		this[name] = value;
	}
	
	return this;
};


		

/** If valid return canonicalValue else null*/
XsltForms_atomicType.prototype.validate = function (value) {
	value = this.canonicalValue(value);
	for (var i = 0, len = this.patterns.length; i < len; i++) {
		if (!value.match(this.patterns[i])) {
			return false;
		}
	}
	if (this.enumeration) {
		var matched = false;
		for (var j = 0, len1 = this.enumeration.length; j < len1; j++) {
			if (value === this.canonicalValue(this.enumeration[j])) {
				matched = true;
				break;
			}
		}
		if (!matched) {
			return false;
		}
	}
	var l = value.length;
	var value_i = parseInt(value, 10);
	if ( (this.length && this.length !== l) ||
		(this.minLength && l < this.minLength) ||
		(this.maxLength && l > this.maxLength) ||
		(this.maxInclusive && value_i > this.maxInclusive) ||
		(this.maxExclusive && value_i >= this.maxExclusive) ||
		(this.minInclusive && value_i < this.minInclusive) ||
		(this.minExclusive && value_i <= this.minExclusive) ) {
		return false;
	}
	if (this.totalDigits || this.fractionDigits) {
		var index = value.indexOf(".");
		var integer = parseInt(index !== -1? value.substring(0, index) : value, 10);
		var decimal = index !== -1? value.substring(index + 1) : "";
		if (index !== -1) {
			if (this.fractionDigits === 0) {
				return false;
			}
			var dl = decimal.length - 1;
			while (dl >= 0 && decimal.charAt(dl) === 0) {
				dl--;
			}
			decimal = decimal.substring(0, dl + 1);
		}
		if ( (this.totalDigits && integer.length + decimal.length > this.totalDigits) ||
			(this.fractionDigits && decimal.length > this.fractionDigits)) {
			return false;
		}
	}
	return true;
};


		

XsltForms_atomicType.prototype.normalize = function (value) {
	if (this.fractionDigits) {
		var number = parseFloat(value);
		var num;
		if (isNaN(number)) {
			return "NaN";
		}
		if (number === 0) {
			num = XsltForms_browser.zeros(0, this.fractionDigits + 1, true);
		} else {
			var mult = XsltForms_browser.zeros(1, this.fractionDigits + 1, true);
			num = "" + Math.round(number * mult);
		}
		if (this.fractionDigits !== 0) {
			var index = num.length - this.fractionDigits;
			return (index === 0 ? "0" : num.substring(0, index)) + "." + num.substring(index);
		}
		return num;
	}
	return value;
};
    
	
		
		
		
function XsltForms_listType() {
	this.whiteSpace = "collapse";
}

XsltForms_listType.prototype = new XsltForms_type();


		

XsltForms_listType.prototype.setItemType = function(itemType) {
	this.itemType = typeof itemType === "string"? this.schema.getType(itemType) : itemType;
	return this;
};


		

XsltForms_listType.prototype.validate = function (value) {
	var items = this.baseType.canonicalValue.call(this, value).split(" ");
	value = "";
	if (items.length === 1 && items[0] === "") {
		items = [];
	}
	for (var i = 0, len = items.length; i < len; i++) {
		var item = XsltForms_itemType.validate(items[i]);
		if (!item) {
			return null;
		}
		value += value.length === 0? item : " " + item;
	}
	if ( (this.length && this.length !== 1) ||
		(this.minLength && 1 < this.minLength) ||
		(this.maxLength && 1 > this.maxLength)) {
		return null;
	}
	return null;
};


		

XsltForms_listType.prototype.canonicalValue = function(value) {
	var items = this.baseType.canonicalValue(value).split(" ");
	var cvalue = "";
	for (var i = 0, len = items.length; i < len; i++) {
		var item = this.itemType.canonicalValue(items[i]);
		cvalue += (cvalue.length === 0 ? "" : " ") + item;
	}
	return cvalue;
};

	
		
		
		
function XsltForms_unionType(memberTypes) {
	this.baseTypes = [];
	this.memberTypes = memberTypes ? memberTypes.split(" ") : [];
}

XsltForms_unionType.prototype = new XsltForms_type();


		

XsltForms_unionType.prototype.addType = function(type) {
	this.baseTypes.push(typeof type === "string"? this.schema.getType(type) : type);
	return this;
};


		

XsltForms_unionType.prototype.addTypes = function() {
	for (var i = 0, len = this.memberTypes.length; i < len; i++ ) {
		this.baseTypes.push(this.schema.getType(this.memberTypes[i]));
	}
	return this;
};


		

XsltForms_unionType.prototype.validate = function (value) {
	for (var i = 0, len = this.baseTypes.length; i < len; ++i) {
		if (this.baseTypes[i].validate(value)) {
			return true;
		}
	}
	return false;
};
    
	
		
		
		
var XsltForms_typeDefs = {

		

	initAll : function() {
		this.init("http://www.w3.org/2001/XMLSchema", this.Default);
		this.init("http://www.w3.org/2002/xforms", this.XForms);
		this.init("http://www.agencexml.com/xsltforms", this.XSLTForms);
	},

		

	init : function(ns, list) {
		var schema = XsltForms_schema.get("xsltforms-mainform", ns);
		for (var id in list) {
			if (list.hasOwnProperty(id)) {
				var type = new XsltForms_atomicType();
				type.setSchema(schema);
				type.setName(id);
				var props = list[id];
				var base = props.base;
				if (base) {
					type.setBase(base);
				}
				for (var prop in props) {
					if (prop !== "base") {
						type[prop] = props[prop];
					}
				}
			}
		}
	},
	ctes : {
		i : "A-Za-z_\\xC0-\\xD6\\xD8-\\xF6\\xF8-\\xFF",
		c : "A-Za-z_\\xC0-\\xD6\\xD8-\\xF6\\xF8-\\xFF\\-\\.0-9\\xB7"
	}
};


		

XsltForms_typeDefs.Default = {

		

	"string" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"whiteSpace" : "preserve"
	},

		

	"boolean" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^(true|false|0|1)$" ],
		"class" : "boolean"
	},

		

	"decimal" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[\\-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)$" ],
		"class" : "number",
		"format" : function(value) {
			return XsltForms_browser.i18n.formatNumber(value, this.fractionDigits);
		},
		"parse" : function(value) {
			return XsltForms_browser.i18n.parseNumber(value);
		}
	},

		

	"float" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^(([\\-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][\\-+]?[0-9]+)?|-?INF|NaN)$" ],
		"class" : "number"
	},

		

	"double" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^(([\\-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][-+]?[0-9]+)?|-?INF|NaN)$" ],
		"class" : "number"
	},

		

	"dateTime" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+\\-]([01][0-9]|2[0-3]):[0-5][0-9])?$" ],
		"class" : "datetime",
		"displayLength" : 20,
		"format" : function(value) {
			return XsltForms_browser.i18n.format(XsltForms_browser.i18n.parse(value, "yyyy-MM-ddThh:mm:ss"),null, true);
		},
		"parse" : function(value) {
			return XsltForms_browser.i18n.format(XsltForms_browser.i18n.parse(value), "yyyy-MM-ddThh:mm:ss", true);
		}
	},

		

	"date" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])(Z|[+\\-]([01][0-9]|2[0-3]):[0-5][0-9])?$" ],
		"class" : "date",
		"displayLength" : 10,
		"format" : function(value) {
			return XsltForms_browser.i18n.formatDate(XsltForms_browser.i18n.parse(value, "yyyy-MM-dd"), null, true);
		},
		"parse" : function(value) {
			return XsltForms_browser.i18n.format(XsltForms_browser.i18n.parseDate(value), "yyyy-MM-dd", true);
		}
	},

		

	"time" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+\\-]([01][0-9]|2[0-3]):[0-5][0-9])?$" ],
		"displayLength" : 8
	},

		

	"duration" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^-?P(?!$)([0-9]+Y)?([0-9]+M)?([0-9]+D)?(T(?!$)([0-9]+H)?([0-9]+M)?([0-9]+(\\.[0-9]+)?S)?)?$" ]
	},

		

	"gDay" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^---(0[1-9]|[12][0-9]|3[01])$" ]
	},

		

	"gMonth" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^--(0[1-9]|1[012])$" ]
	},

		

	"gMonthDay" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^--(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$" ]
	},

		

	"gYear" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([\\-+]?([0-9]{4}|[1-9][0-9]{4,}))?$" ]
	},

		

	"gYearMonth" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([12][0-9]{3})-(0[1-9]|1[012])$" ]
	},

		

	"integer" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:decimal",
		"fractionDigits" : "0"
	},

		

	"nonPositiveInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^(-[0-9]+|0)$" ]
	},

		

	"nonNegativeInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^\\+?[0-9]+$" ]
	},

		

	"negativeInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^-0*[1-9][0-9]*$" ]
	},

		

	"positiveInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^\\+?0*[1-9][0-9]*$" ]
	},

		

	"byte" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"minInclusive" : -128,
		"maxInclusive" : 127
	},

		

	"short" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"minInclusive" : -32768,
		"maxInclusive" : 32767
	},

		

	"int" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"minInclusive" : -2147483648,
		"maxInclusive" : 2147483647
},

		

	"long" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"minInclusive" : -9223372036854775808,
		"maxInclusive" : 9223372036854775807
},

		

	"unsignedByte" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:nonNegativeInteger",
		"maxInclusive" : 255
	},

		

	"unsignedShort" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:nonNegativeInteger",
		"maxInclusive" : 65535
	},

		

	"unsignedInt" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:nonNegativeInteger",
		"maxInclusive" : 4294967295
	},

		

	"unsignedLong" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:nonNegativeInteger",
		"maxInclusive" : 18446744073709551615
},

		

	"normalizedString" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"whiteSpace" : "replace"
	},

		

	"token" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"whiteSpace" : "collapse"
	},

		

	"language" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*$" ]
	},

		

	"anyURI" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^(([^:\\/?#]+):)?(\\/\\/([^\\/\\?#]*))?([^\\?#]*)(\\?([^#]*))?(#([^\\:#\\[\\]\\@\\!\\$\\&\\\\'\\(\\)\\*\\+\\,\\;\\=]*))?$" ]
	},

		

	"Name" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^[" + XsltForms_typeDefs.ctes.i + ":][" + XsltForms_typeDefs.ctes.c + ":]*$" ]
	},

		

	"NCName" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^[" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*$" ]
	},

		

	"QName" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^(([a-zA-Z][0-9a-zA-Z+\\-\\.]*:)?/{0,2}[0-9a-zA-Z;/?:@&=+$\\.\\>> -_!~*'()%]+)?(>> #[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?:[" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*$" ]
	},

		

	"ID" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:NCName"
	},

		

	"IDREF" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:NCName"
	},

		

	"IDREFS" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*( +[" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*)*$" ]
	},

		

	"NMTOKEN" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[" + XsltForms_typeDefs.ctes.c + "]+$" ]
	},

		

	"NMTOKENS" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[" + XsltForms_typeDefs.ctes.c + "]+( +[" + XsltForms_typeDefs.ctes.c + "]+)*$" ]
	},

		

	"base64Binary" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[a-zA-Z0-9+/]+$" ]
	},

		

	"hexBinary" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[0-9A-Fa-f]+$" ],
		"format" : function(value) {
			return value.toUpperCase();
		},
		"parse" : function(value) {
			return value.toUpperCase();
		}
	}
};


		

XsltForms_typeDefs.XForms = {

		

	"string" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"whiteSpace" : "preserve"
	},

		

	"boolean" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(true|false|0|1)?$" ],
		"class" : "boolean"
	},

		

	"decimal" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([\\-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+))?$" ],
		"class" : "number",
		"format" : function(value) {
			return XsltForms_browser.i18n.formatNumber(value, this.fractionDigits);
		},
		"parse" : function(value) {
			return XsltForms_browser.i18n.parseNumber(value);
		}
	},

		

	"float" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^((([\\-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][\\-+]?[0-9]+)?|-?INF|NaN))?$" ],
		"class" : "number"
	},

		

	"double" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^((([\\-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][-+]?[0-9]+)?|-?INF|NaN))?$" ],
		"class" : "number"
	},

		

	"dateTime" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+\\-]([01][0-9]|2[0-3]):[0-5][0-9])?)?$" ],
		"class" : "datetime",
		"displayLength" : 20,
		"format" : function(value) {
			return XsltForms_browser.i18n.format(XsltForms_browser.i18n.parse(value, "yyyy-MM-ddThh:mm:ss"), null, true);
		},
		"parse" : function(value) {
			return XsltForms_browser.i18n.format(XsltForms_browser.i18n.parse(value), "yyyy-MM-ddThh:mm:ss", true);
		}
	},

		

	"date" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])(Z|[+\\-]([01][0-9]|2[0-3]):[0-5][0-9])?)?$" ],
		"class" : "date",
		"displayLength" : 10,
		"format" : function(value) {
			return XsltForms_browser.i18n.formatDate(XsltForms_browser.i18n.parse(value, "yyyy-MM-dd"), null, true);
		},
		"parse" : function(value) {
			return XsltForms_browser.i18n.format(XsltForms_browser.i18n.parseDate(value), "yyyy-MM-dd", true);
		}
	},

		

	"time" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+\\-]([01][0-9]|2[0-3]):[0-5][0-9])?)?$" ],
		"displayLength" : 8
	},

		

	"duration" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(-?P(?!$)([0-9]+Y)?([0-9]+M)?([0-9]+D)?(T(?!$)([0-9]+H)?([0-9]+M)?([0-9]+(\\.[0-9]+)?S)?)?)?$" ]
	},

		

	"dayTimeDuration" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(-?P([0-9]+D(T([0-9]+(H([0-9]+(M([0-9]+(\\.[0-9]*)?S|\\.[0-9]+S)?|(\\.[0-9]*)?S)|(\\.[0-9]*)?S)?|M([0-9]+(\\.[0-9]*)?S|\\.[0-9]+S)?|(\\.[0-9]*)?S)|\\.[0-9]+S))?|T([0-9]+(H([0-9]+(M([0-9]+(\\.[0-9]*)?S|\\.[0-9]+S)?|(\\.[0-9]*)?S)|(\\.[0-9]*)?S)?|M([0-9]+(\\.[0-9]*)?S|\\.[0-9]+S)?|(\\.[0-9]*)?S)|\\.[0-9]+S)))?$" ]
	},

		

	"yearMonthDuration" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(-?P[0-9]+(Y([0-9]+M)?|M))?$" ]
	},

		

	"gDay" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(---(0[1-9]|[12][0-9]|3[01]))?$" ]
	},

		

	"gMonth" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(--(0[1-9]|1[012]))?$" ]
	},

		

	"gMonthDay" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(--(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01]))?$" ]
	},

		

	"gYear" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([\\-+]?([0-9]{4}|[1-9][0-9]{4,}))?$" ]
	},

		

	"gYearMonth" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([12][0-9]{3})-(0[1-9]|1[012]))?$" ]
	},

		

	"integer" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:decimal",
		"fractionDigits" : "0"
	},

		

	"nonPositiveInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^((-[0-9]+|0))?$" ]
	},

		

	"nonNegativeInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^(\\+?[0-9]+)?$" ]
	},

		

	"negativeInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^(-[0-9]+)?$" ]
	},

		

	"positiveInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^\\+?0*[1-9][0-9]*$" ]
	},

		

	"byte" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"minInclusive" : -128,
		"maxInclusive" : 127
	},

		

	"short" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"minInclusive" : -32768,
		"maxInclusive" : 32767
	},

		

	"int" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"minInclusive" : -2147483648,
		"maxInclusive" : 2147483647
	},

		

	"long" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"minInclusive" : -9223372036854775808,
		"maxInclusive" : 9223372036854775807
	},

		

	"unsignedByte" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:nonNegativeInteger",
		"maxInclusive" : 255
	},

		

	"unsignedShort" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:nonNegativeInteger",
		"maxInclusive" : 65535
	},

		

	"unsignedInt" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:nonNegativeInteger",
		"maxInclusive" : 4294967295
	},

		

	"unsignedLong" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:nonNegativeInteger",
		"maxInclusive" : 18446744073709551615
	},

		

	"normalizedString" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"whiteSpace" : "replace"
	},

		

	"token" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"whiteSpace" : "collapse"
	},

		

	"language" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^([a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*)?$" ]
	},

		

	"anyURI" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^((([a-zA-Z][0-9a-zA-Z+\\-\\.]*:)?/{0,2}[0-9a-zA-Z;/?:@&=+$\\.\\>> -_!~*'()%]+)?(>> #[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?)?$" ]
	},

		

	"Name" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^([" + XsltForms_typeDefs.ctes.i + ":][" + XsltForms_typeDefs.ctes.c + ":]*)?$" ]
	},

		

	"NCName" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^([" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*)?$" ]
	},

		

	"QName" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^((([a-zA-Z][0-9a-zA-Z+\\-\\.]*:)?/{0,2}[0-9a-zA-Z;/?:@&=+$\\.\\>> -_!~*'()%]+)?(>> #[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?:[" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*)?$" ]
	},

		

	"ID" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:NCName"
	},

		

	"IDREF" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:NCName"
	},

		

	"IDREFS" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]+( +[" + XsltForms_typeDefs.ctes.i + "][" + XsltForms_typeDefs.ctes.c + "]*)*)?$" ]
	},

		

	"NMTOKEN" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^[" + XsltForms_typeDefs.ctes.c + "]*$" ]
	},

		

	"NMTOKENS" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([" + XsltForms_typeDefs.ctes.c + "]+( +[" + XsltForms_typeDefs.ctes.c + "]+)*)?$" ]
	},

		

	"base64Binary" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^[a-zA-Z0-9+/]*$" ]
	},

		

	"hexBinary" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^[0-9A-Fa-f]*$" ],
		"format" : function(value) {
			return value.toUpperCase();
		},
		"parse" : function(value) {
			return value.toUpperCase();
		}
	},

		

	"email" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xsd_:string",
		"whiteSpace" : "collapse",
		"patterns" : [ "^([A-Za-z0-9!#-'\\*\\+\\-/=\\?\\^_`\\{-~]+(\\.[A-Za-z0-9!#-'\\*\\+\\-/=\\?\\^_`\\{-~]+)*@[A-Za-z0-9!#-'\\*\\+\\-/=\\?\\^_`\\{-~]+(\\.[A-Za-z0-9!#-'\\*\\+\\-/=\\?\\^_`\\{-~]+)*)?$" ]
	},

		

	"card-number" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xsd_:string",
		"patterns" : [ "^[0-9]*$" ]
	},

		

	"url" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xsd_:string",
		"whiteSpace" : "collapse",
		"patterns" : [ "^(ht|f)tp(s?)://([a-z0-9]*:[a-z0-9]*@)?([a-z0-9.]*\\.[a-z]{2,7})$" ]
	},

		

	"amount" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xsd_:decimal",
		"format" : function(value) {
			return XsltForms_browser.i18n.formatNumber(value, 2);
		}
	}
};

		

XsltForms_typeDefs.XSLTForms = {

		

	"decimal" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"patterns" : [ "^[\\-+]?\\(*[\\-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)(([+-/]|\\*)\\(*([0-9]+(\\.[0-9]*)?|\\.[0-9]+)\\)*)*$" ],
		"class" : "number",
		"eval" : "xsd:decimal"
	},

		

	"float" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:float"
	},

		

	"double" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:double"
	},

		

	"integer" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:integer"
	},

		

	"nonPositiveInteger" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:nonPositiveInteger"
	},

		

	"nonNegativeInteger" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:nonNegativeInteger"
	},

		

	"negativeInteger" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:negativeInteger"
	},

		

	"positiveInteger" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:positiveInteger"
	},

		

	"byte" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:byte"
	},

		

	"short" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:short"
	},

		

	"int" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:int"
	},

		

	"long" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:long"
	},

		

	"unsignedByte" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:unsignedByte"
	},

		

	"unsignedShort" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:unsignedShort"
	},

		

	"unsignedInt" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:unsignedInt"
	},

		

	"unsignedLong" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"base" : "xsltforms:decimal",
		"eval" : "xsd:unsignedLong"
	}
};

XsltForms_typeDefs.initAll();

	
	
		
		
		
		
		
function XsltForms_listener(observer, evtTarget, name, phase, handler) {
	phase = phase || "default";
	if (phase !== "default" && phase !== "capture") {
		XsltForms_globals.error(XsltForms_globals.defaultModel, "xforms-compute-exception", 
			"Unknown event-phase(" + phase +") for event(" + name + ")"+(observer ? " on element(" + observer.id + ")":"") + "!");
		return;
	}
	this.observer = observer;
	this.evtTarget = evtTarget;
	this.name = name;
	this.evtName = document.addEventListener? name : "errorupdate";
	this.phase = phase;
	this.handler = handler;
	XsltForms_browser.assert(observer);
	if (!observer.listeners) {
		observer.listeners = [];
	}
	observer.listeners.push(this);
	this.callback = function(event) {
		if (!document.addEventListener) {
			event = event || window.event;
			event.target = event.srcElement;
			event.currentTarget = observer;
			if (event.trueName && event.trueName !== name) {
				return;
			}
			if (!event.phase) {
				if (phase === "capture") {
					return;
				}
			} else if (event.phase !== phase) {
				return;
			}
			if (phase === "capture") {
				event.cancelBubble = true;
			}
			event.preventDefault = function() {
				this.returnValue = false;
			};
			event.stopPropagation = function() {
				this.cancelBubble = true;
				this.stopped      = true;
			};
		}
		var effectiveTarget = true;
		if (event.target && event.target.nodeType === 3) {
			event.target = event.target.parentNode;
		}
		if (event.currentTarget && event.type === "DOMActivate" && event.target.nodeName === "BUTTON" && !XsltForms_browser.isFF2) {
			effectiveTarget = false;
		}
		if (event.eventPhase === 3 && !event.target.xfElement && !XsltForms_browser.isFF2) {
			effectiveTarget = false;
		}
		if (event.eventPhase === 3 && event.target.xfElement && event.target === event.currentTarget && !XsltForms_browser.isFF2) {
			effectiveTarget = false;
		}
		if (evtTarget && event.target != evtTarget) {
			effectiveTarget = false;
		}
		if (effectiveTarget) {
			handler.call(event.target, event);
		}
		if (!document.addEventListener) {
			try {
				event.preventDefault = null;
				event.stopPropagation = null;
			} catch (e) {}
		}
	};
	this.attach();
}


		

XsltForms_listener.destructs = [];

XsltForms_listener.prototype.attach = function() {
	XsltForms_browser.events.attach(this.observer, this.evtName, this.callback, this.phase === "capture");
	if (this.evtName === "xforms-model-destruct") {
		XsltForms_listener.destructs.push({observer: this.observer, callback: this.callback});
	}
};


		

XsltForms_listener.prototype.detach = function() {
	XsltForms_browser.events.detach(this.observer, this.evtName, this.callback, this.phase === "capture");
};


		

XsltForms_listener.prototype.clone = function(element) {
	var unused = new XsltForms_listener(element, this.evtTarget, this.name, this.phase, this.handler);
};

	
		
		
		
var XsltForms_xmlevents = {

		

    REGISTRY : [],

		

	EventContexts : [],

		

	define : function(name, bubbles, cancelable, defaultAction) {
		XsltForms_xmlevents.REGISTRY[name] = {
			bubbles:       bubbles,
			cancelable:    cancelable,
			defaultAction: defaultAction? defaultAction : function() { }
		};
	},

		

	makeEventContext : function(evcontext, type, targetid, bubbles, cancelable) {
		if (!evcontext) {
			evcontext = {};
		}
		evcontext.type = type;
		evcontext.targetid = targetid;
		evcontext.bubbles = bubbles;
		evcontext.cancelable = cancelable;
		return evcontext;
	}
};


		

XsltForms_xmlevents.dispatchList = function(list, name) {
	for (var id = 0, len = list.length; id < len; id++) {
		XsltForms_xmlevents.dispatch(list[id], name);
	}
};


		

XsltForms_xmlevents.dispatch = function(target, name, type, bubbles, cancelable, defaultAction, evcontext) {
	target = target.element || target;
	XsltForms_browser.assert(target && typeof(target.nodeName) !== "undefined");
	XsltForms_browser.debugConsole.write("Dispatching event " + name + " on <" + target.nodeName +
		(target.className? " class=\"" + target.className + "\"" : "") +
		(target.id? " id=\"" + target.id + "\"" : "") + "/>");
	var reg = XsltForms_xmlevents.REGISTRY[name];
	if (reg) {
		bubbles = reg.bubbles;
		cancelable = reg.cancelable;
		defaultAction = reg.defaultAction;
	}
	if (!defaultAction) {
		defaultAction = function() { };
	}
	evcontext = XsltForms_xmlevents.makeEventContext(evcontext, name, target.id, bubbles, cancelable);
	XsltForms_xmlevents.EventContexts.push(evcontext);
	try {
		var event, res;
		if (target.dispatchEvent) {
			event = document.createEvent("Event");
			event.initEvent(name, bubbles, cancelable);
			res = target.dispatchEvent(event);
			if ((res && !event.stopped) || !cancelable) {
				defaultAction.call(target.xfElement, event);
			}
		} else {
			var fauxName = "errorupdate";
			var canceler = null;
			// Capture phase.
			var ancestors = [];
			for (var a = target.parentNode; a; a = a.parentNode) {
				ancestors.unshift(a);
			}
			for (var i = 0, len = ancestors.length; i < len; i++) {
				event = document.createEventObject();
				event.trueName = name;
				event.phase = "capture";
				ancestors[i].fireEvent("onerrorupdate", event);
				if (event.stopped) {
					return;
				}
			}
			event = document.createEventObject();
			event.trueName = name;
			event.phase = "capture";
			event.target = target;
			target.fireEvent("onerrorupdate" , event);
			// Bubble phase.
			if (!bubbles) {
				canceler = new XsltForms_listener(target, null, name, "default", function(event) { event.cancelBubble = true; });
			}
			event = document.createEventObject();
			event.trueName = name;
			event.phase = "default";
			event.target = target;
			res = target.fireEvent("onerrorupdate", event);
			try {
				if ((res && !event.stopped) || !cancelable) {
					defaultAction.call(target.xfElement, event);
				}
				if (!bubbles) {
					canceler.detach();
				}
			} catch (e2) {
			}
		}
	} catch (e) {
		alert("XSLTForms Exception\n--------------------------\n\nError dispatching event '"+name+"' :\n\n"+(typeof(e.stack)==="undefined"?"":e.stack)+"\n\n"+(e.name?e.name+(e.message?"\n\n"+e.message:""):e));
	} finally {
		if (XsltForms_xmlevents.EventContexts[XsltForms_xmlevents.EventContexts.length - 1].rheadsdoc) {
			XsltForms_xmlevents.EventContexts[XsltForms_xmlevents.EventContexts.length - 1].rheadsdoc = null;
		}
		if (XsltForms_xmlevents.EventContexts[XsltForms_xmlevents.EventContexts.length - 1]["response-body"]) {
			XsltForms_xmlevents.EventContexts[XsltForms_xmlevents.EventContexts.length - 1]["response-body"] = null;
		}
		XsltForms_xmlevents.EventContexts.pop();
	}
};


		

XsltForms_xmlevents.define("xforms-model-construct", true, false, function(event) { this.construct(); });

		

XsltForms_xmlevents.define("xforms-model-construct-done", true, false);

		

XsltForms_xmlevents.define("xforms-ready", true, false);

		

XsltForms_xmlevents.define("xforms-model-destruct", true, false);

		

XsltForms_xmlevents.define("xforms-rebuild", true, true, function(event) { this.rebuild(); });

		

XsltForms_xmlevents.define("xforms-recalculate", true, true, function(event) { this.recalculate(); });

		

XsltForms_xmlevents.define("xforms-revalidate", true, true, function(event) { this.revalidate(); });

		

XsltForms_xmlevents.define("xforms-reset", true, true, function(event) { this.reset(); });

		

XsltForms_xmlevents.define("xforms-submit", true, true, function(event) { this.submit(); });

		

XsltForms_xmlevents.define("xforms-submit-serialize", true, false);

		

XsltForms_xmlevents.define("xforms-refresh", true, true, function(event) { this.refresh(); });

		

XsltForms_xmlevents.define("xforms-focus", true, true, function(event) { this.focus(); } );


		

XsltForms_xmlevents.define("DOMActivate", true,  true);

		

XsltForms_xmlevents.define("DOMFocusIn", true, false);

		

XsltForms_xmlevents.define("DOMFocusOut", true, false);

		

XsltForms_xmlevents.define("xforms-select", true, false);

		

XsltForms_xmlevents.define("xforms-deselect", true, false);

		

XsltForms_xmlevents.define("xforms-value-changed", true, false);


		

XsltForms_xmlevents.define("xforms-insert", true, false);

		

XsltForms_xmlevents.define("xforms-delete", true, false);

		

XsltForms_xmlevents.define("xforms-valid", true, false);

		

XsltForms_xmlevents.define("xforms-invalid", true, false);

		

XsltForms_xmlevents.define("xforms-enabled", true, false);

		

XsltForms_xmlevents.define("xforms-disabled", true, false);

		

XsltForms_xmlevents.define("xforms-optional", true, false);

		

XsltForms_xmlevents.define("xforms-required", true, false);

		

XsltForms_xmlevents.define("xforms-readonly", true, false);

		

XsltForms_xmlevents.define("xforms-readwrite", true, false);

		

XsltForms_xmlevents.define("xforms-in-range", true, false);

		

XsltForms_xmlevents.define("xforms-out-of-range", true, false);

		

XsltForms_xmlevents.define("xforms-submit-done", true, false);

		

XsltForms_xmlevents.define("xforms-submit-error", true, false);

		

XsltForms_xmlevents.define("xforms-compute-exception", true, false);

		

XsltForms_xmlevents.define("xforms-binding-exception", true, false);

XsltForms_xmlevents.define("ajx-start", true, true, function(evt) { evt.target.xfElement.start(); });
XsltForms_xmlevents.define("ajx-stop", true, true, function(evt) { evt.target.xfElement.stop(); });
XsltForms_xmlevents.define("ajx-time", true, true);

		

XsltForms_xmlevents.define("xforms-dialog-open", true, true, function(evt) { XsltForms_browser.dialog.show(evt.target, null, true); });

		

XsltForms_xmlevents.define("xforms-dialog-close", true, true, function(evt) { XsltForms_browser.dialog.hide(evt.target, true); });

		

XsltForms_xmlevents.define("xforms-load-done", true, false);

		

XsltForms_xmlevents.define("xforms-load-error", true, false);

	
	
		
		
		
		
		
var XsltForms_xpathAxis = {
	ANCESTOR_OR_SELF: 'ancestor-or-self',
	ANCESTOR: 'ancestor',
	ATTRIBUTE: 'attribute',
	CHILD: 'child',
	DESCENDANT_OR_SELF: 'descendant-or-self',
	DESCENDANT: 'descendant',
	FOLLOWING_SIBLING: 'following-sibling',
	FOLLOWING: 'following',
	NAMESPACE: 'namespace',
	PARENT: 'parent',
	PRECEDING_SIBLING: 'preceding-sibling',
	PRECEDING: 'preceding',
	SELF: 'self'
};


		

var XsltForms_nodeType = {
	ELEMENT : 1,
	ATTRIBUTE : 2,
	TEXT : 3,
	CDATA_SECTION : 4,
	ENTITY_REFERENCE : 5,
	ENTITY : 6,
	PROCESSING_INSTRUCTION : 7,
	COMMENT : 8,
	DOCUMENT : 9,
	DOCUMENT_TYPE : 10,
	DOCUMENT_FRAGMENT : 11,
	NOTATION : 12
};

	
		
		
		
function ArrayExpr(exprs) {
	this.exprs = exprs;
}


		

ArrayExpr.prototype.evaluate = function(ctx) {
	var nodes = [];
	for (var i = 0, len = this.exprs.length; i < len; i++) {
		nodes[i] = this.exprs[i].evaluate(ctx);
	}
	return nodes;
};

	
		
		
		
function XsltForms_binaryExpr(expr1, op, expr2) {
	this.expr1 = expr1;
	this.expr2 = expr2;
	this.op = op.replace("&gt;", ">").replace("&lt;", "<");
}


		

XsltForms_binaryExpr.prototype.evaluate = function(ctx) {
	var v1 = this.expr1.evaluate(ctx);
	var v2 = this.expr2.evaluate(ctx);
	var n1;
	var n2;
	if (v1 && v2 && (((typeof v1) === "object" && v1.length > 1) || ((typeof v2) === "object" && v2.length > 1)) && 
		(this.op === "=" || this.op === "!=" || this.op === "<" || this.op === "<=" || this.op === ">" || this.op === ">=")) {
		if (typeof v1 !== "object") {
			v1 = [v1];
		}
		if (typeof v2 !== "object") {
			v2 = [v2];
		}
		for (var i = 0, len = v1.length; i < len; i++) {
			n1 = XsltForms_globals.numberValue([v1[i]]);
			if (isNaN(n1)) {
				n1 = XsltForms_globals.stringValue([v1[i]]);
			}
			for (var j = 0, len1 = v2.length; j < len1; j++) {
				n2 = XsltForms_globals.numberValue([v2[j]]);
				if (isNaN(n2)) {
					n2 = XsltForms_globals.stringValue([v2[j]]);
				}
				switch (this.op) {
					case '=':
						if (n1 == n2) {
							return true;
						}
						break;
					case '!=':
						if (n1 != n2) {
							return true;
						}
						break;
					case '<':
						if (n1 < n2) {
							return true;
						}
						break;
					case '<=':
						if (n1 <= n2) {
							return true;
						}
						break;
					case '>':
						if (n1 > n2) {
							return true;
						}
						break;
					case '>=':
						if (n1 >= n2) {
							return true;
						}
						break;
				}
			}
		}
		return false;
	}
	n1 = XsltForms_globals.numberValue(v1);
	n2 = XsltForms_globals.numberValue(v2);
	if (isNaN(n1) || isNaN(n2)) {
		n1 = XsltForms_globals.stringValue(v1);
		n2 = XsltForms_globals.stringValue(v2);
	}
	var res = 0;
	switch (this.op) {
		case 'or'  : res = XsltForms_globals.booleanValue(v1) || XsltForms_globals.booleanValue(v2); break;
		case 'and' : res = XsltForms_globals.booleanValue(v1) && XsltForms_globals.booleanValue(v2); break;
		case '+'   : res = n1 + n2; break;
		case '-'   : res = n1 - n2; break;
		case '*'   : res = n1 * n2; break;
		case 'mod' : res = n1 % n2; break;
		case 'div' : res = n1 / n2; break;
		case '='   : res = n1 === n2; break;
		case '!='  : res = n1 !== n2; break;
		case '<'   : res = n1 < n2; break;
		case '<='  : res = n1 <= n2; break;
		case '>'   : res = n1 > n2; break;
		case '>='  : res = n1 >= n2; break;
	}
	return typeof res === "number" ? Math.round(res*1000000)/1000000 : res;
};

	
		
		
		
function XsltForms_exprContext(node, position, nodelist, parent, nsresolver, current, depsNodes, depsId, depsElements) {
	this.node = node;
	this.current = current || node;
	if(!position) {
		var repeat = node ? XsltForms_browser.getMeta(node, "repeat") : null;
		if(repeat) {
			var xrepeat = document.getElementById(repeat).xfElement;
			var len;
			for(position = 1, len = xrepeat.nodes.length; position <= len; position++) {
				if(node === xrepeat.nodes[position-1]) {
					break;
				}
			}
		}
	}
	this.position = position || 1;
	this.nodelist = nodelist || [ node ];
	this.parent = parent;
	this.root = parent ? parent.root : node ? node.ownerDocument : null;
	this.nsresolver = nsresolver;
	this.depsId = depsId;
	this.initDeps(depsNodes, depsElements);
}


		

XsltForms_exprContext.prototype.clone = function(node, position, nodelist) {
	return new XsltForms_exprContext(node || this.node, 
		typeof position === "undefined" ? this.position : position,
		nodelist || this.nodelist, this, this.nsresolver, this.current,
		this.depsNodes, this.depsId, this.depsElements);
};


		

XsltForms_exprContext.prototype.setNode = function(node, position) {
	this.node = node;
	this.position = position;
};


		

XsltForms_exprContext.prototype.initDeps = function(depsNodes, depsElements) {
	this.depsNodes = depsNodes;
	this.depsElements = depsElements;
};


		

XsltForms_exprContext.prototype.addDepNode = function(node) {
	var deps = this.depsNodes;
	if (deps && node.nodeType !== XsltForms_nodeType.DOCUMENT && (!this.depsId || !XsltForms_browser.inValueMeta(node, "depfor", this.depsId))) { // !inArray(node, deps)) {
		if (this.depsId) {
			XsltForms_browser.addValueMeta(node, "depfor", this.depsId);
		}
		deps.push(node);
	}
};


		

XsltForms_exprContext.prototype.addDepElement = function(element) {
	var deps = this.depsElements;
	if (deps && !XsltForms_browser.inArray(element, deps)) {
		deps.push(element);
	}
};

	
		
		
		
function XsltForms_tokenExpr(m) {
	this.value = m;
}


		

XsltForms_tokenExpr.prototype.evaluate = function() {
	return XsltForms_globals.stringValue(this.value);
};


		

function XsltForms_unaryMinusExpr(expr) {
	this.expr = expr;
}


		

XsltForms_unaryMinusExpr.prototype.evaluate = function(ctx) {
	return -XsltForms_globals.numberValue(this.expr.evaluate(ctx));
};


		

function XsltForms_cteExpr(value) {
	this.value = XsltForms_browser.isEscaped ? typeof value === "string" ? XsltForms_browser.unescape(value) : value : value;
}


		

XsltForms_cteExpr.prototype.evaluate = function() {
	return this.value;
};

	
		
		
		
function XsltForms_filterExpr(expr, predicate) {
	this.expr = expr;
	this.predicate = predicate;
}


		

XsltForms_filterExpr.prototype.evaluate = function(ctx) {
	var nodes = XsltForms_globals.nodeSetValue(this.expr.evaluate(ctx));
	for (var i = 0, len = this.predicate.length; i < len; ++i) {
		var nodes0 = nodes;
		nodes = [];
		for (var j = 0, len1 = nodes0.length; j < len1; ++j) {
			var n = nodes0[j];
			var newCtx = ctx.clone(n, j, nodes0);
			if (XsltForms_globals.booleanValue(this.predicate[i].evaluate(newCtx))) {
				nodes.push(n);
			}
		}
	}
	return nodes;
};

	
		
		
		
function XsltForms_functionCallExpr(name) {
	this.name = name;
	this.func = XsltForms_xpathCoreFunctions[name];
	this.args = [];
	if (!this.func) {
		throw {name: "Function " + name + "() not found"};
	}
	for (var i = 1, len = arguments.length; i < len; i++) {
		this.args.push(arguments[i]);
	}
}


		

XsltForms_functionCallExpr.prototype.evaluate = function(ctx) {
	var arguments_ = [];
	for (var i = 0, len = this.args.length; i < len; i++) {
		arguments_[i] = this.args[i].evaluate(ctx);
	}
	return this.func.call(ctx, arguments_);
};

	
		
		
		
function XsltForms_locationExpr(absolute) {
	this.absolute = absolute;
	this.steps = [];
	for (var i = 1, len = arguments.length; i < len; i++) {
		this.steps.push(arguments[i]);
	}
}


		

XsltForms_locationExpr.prototype.evaluate = function(ctx) {
	var start = this.absolute? ctx.root : ctx.node;
	var m = XsltForms_browser.getMeta((start.documentElement ? start.documentElement : start.ownerDocument.documentElement), "model");
	if (m) {
		ctx.addDepElement(document.getElementById(m).xfElement);
	}
	var nodes = [];
	if (this.steps[0]) {
		this.xPathStep(nodes, this.steps, 0, start, ctx);
	} else {
		nodes[0] = start;
	}
	return nodes;
};

XsltForms_locationExpr.prototype.xPathStep = function(nodes, steps, step, input, ctx) {
	var s = steps[step];
	var nodelist = s.evaluate(ctx.clone(input));
	for (var i = 0, len = nodelist.length; i < len; ++i) {
		var node = nodelist[i];
		if (step === steps.length - 1) {
			if (!XsltForms_browser.inArray(node, nodes)) {
				nodes.push(node);
			}
			ctx.addDepNode(node);
		} else {
			this.xPathStep(nodes, steps, step + 1, node, ctx);
		}
	}
};
    
	
		
		
		
function XsltForms_nodeTestAny() {
}


		

XsltForms_nodeTestAny.prototype.evaluate = function(node) {
	var n = node.localName || node.baseName;
    return !n || (n.substr(0, 10) !== "xsltforms_" && node.namespaceURI !== "http://www.w3.org/2000/xmlns/");
};

	
		
		

function XsltForms_nodeTestName(prefix, name) {
    this.prefix = prefix;
    this.name = name;
	this.uppercase = name.toUpperCase();
	this.wildcard = name === "*";
	this.notwildcard = name !== "*";
	this.notwildcardprefix = prefix !== "*";
	this.hasprefix = prefix && this.notwildcardprefix;
}


		

XsltForms_nodeTestName.prototype.evaluate = function(node, nsresolver, csensitive) {
	var nodename = node.localName || node.baseName;
	if (this.notwildcard && (nodename !== this.name || (csensitive && nodename.toUpperCase() !== this.uppercase))) {
		return false;
	}
	if (this.wildcard) {
		return this.hasprefix ? node.namespaceURI === nsresolver.lookupNamespaceURI(this.prefix) : true;
	}
	var ns = node.namespaceURI;
	return this.hasprefix ? ns === nsresolver.lookupNamespaceURI(this.prefix) :
		(this.notwildcardprefix ? !ns || ns === "" || ns === nsresolver.lookupNamespaceURI("") : true);
};
    
	
		
		
		
function XsltForms_nodeTestPI(target) {
	this.target = target;
}


		

XsltForms_nodeTestPI.prototype.evaluate = function(node) {
	return node.nodeType === XsltForms_nodeType.PROCESSING_INSTRUCTION &&
		(!this.target || node.nodeName === this.target);
};

	
		
		
		
function XsltForms_nodeTestType(type) {
	this.type = type;
}


		

XsltForms_nodeTestType.prototype.evaluate = function(node) {
	return node.nodeType === this.type;
};
	
	
		
		
		
function XsltForms_nsResolver() {
	this.map = {};
	this.notfound = false;
}


		

XsltForms_nsResolver.prototype.registerAll = function(resolver) {
	for (var prefix in resolver.map) {
		if (resolver.map.hasOwnProperty(prefix)) {
			this.map[prefix] = resolver.map[prefix];
		}
	}
};


		

XsltForms_nsResolver.prototype.register = function(prefix, uri) {
	this.map[prefix] = uri;
	if( uri === "notfound" ) {
		this.notfound = true;
	}
};


		

XsltForms_nsResolver.prototype.registerNotFound = function(prefix, uri) {
	if( this.map[prefix] === "notfound" ) {
		this.map[prefix] = uri;
		for (var p in this.map) {
			if (this.map.hasOwnProperty(p)) {
				if (this.map[p] === "notfound") {
					this.notfound = true;
				}
			}
		}
	}
};


		

XsltForms_nsResolver.prototype.lookupNamespaceURI = function(prefix) {
	return this.map[prefix];
};

	
		
		
		
function XsltForms_pathExpr(filter, rel) {
	this.filter = filter;
	this.rel = rel;
}


		

XsltForms_pathExpr.prototype.evaluate = function(ctx) {
	var nodes = XsltForms_globals.nodeSetValue(this.filter.evaluate(ctx));
	var nodes1 = [];
	for (var i = 0, len = nodes.length; i < len; i++) {
		var newCtx = ctx.clone(nodes[i], i, nodes);
		var nodes0 = XsltForms_globals.nodeSetValue(this.rel.evaluate(newCtx));
		for (var j = 0, len1 = nodes0.length; j < len1; j++) {
			nodes1.push(nodes0[j]);
		}
	}
	return nodes1;
};

	
		
		
		
function XsltForms_predicateExpr(expr) {
	this.expr = expr;
}


		

XsltForms_predicateExpr.prototype.evaluate = function(ctx) {
	var v = this.expr.evaluate(ctx);
	return typeof v === "number" ? ctx.position === v : XsltForms_globals.booleanValue(v);
};

	
		
		
		
function XsltForms_stepExpr(axis, nodetest) {
	this.axis = axis;
	this.nodetest = nodetest;
	this.predicates = [];
	for (var i = 2, len = arguments.length; i < len; i++) {
		this.predicates.push(arguments[i]);
	}
}


		

XsltForms_stepExpr.prototype.evaluate = function(ctx) {
	var input = ctx.node;
	var list = [];
	switch(this.axis) {
		case XsltForms_xpathAxis.ANCESTOR_OR_SELF :
			XsltForms_stepExpr.push(ctx, list, input, this.nodetest);
			if (input.nodeType === XsltForms_nodeType.ATTRIBUTE) {
				input = input.ownerElement ? input.ownerElement : input.selectSingleNode("..");
				XsltForms_stepExpr.push(ctx, list, input, this.nodetest);
			}
			for (var pn = input.parentNode; pn.parentNode; pn = pn.parentNode) {
				XsltForms_stepExpr.push(ctx, list, pn, this.nodetest);
			}
			break;
		case XsltForms_xpathAxis.ANCESTOR :
			if (input.nodeType === XsltForms_nodeType.ATTRIBUTE) {
				input = input.ownerElement ? input.ownerElement : input.selectSingleNode("..");
				XsltForms_stepExpr.push(ctx, list, input, this.nodetest);
			}
			for (var pn2 = input.parentNode; pn2.parentNode; pn2 = pn2.parentNode) {
				XsltForms_stepExpr.push(ctx, list, pn2, this.nodetest);
			}
			break;
		case XsltForms_xpathAxis.ATTRIBUTE :
			XsltForms_stepExpr.pushList(ctx, list, input.attributes, this.nodetest, !input.namespaceURI || input.namespaceURI === "http://www.w3.org/1999/xhtml");
			break;
		case XsltForms_xpathAxis.CHILD :
			XsltForms_stepExpr.pushList(ctx, list, input.childNodes, this.nodetest);
			break;
		case XsltForms_xpathAxis.DESCENDANT_OR_SELF :
			XsltForms_stepExpr.push(ctx, list, input, this.nodetest);
			XsltForms_stepExpr.pushDescendants(ctx, list, input, this.nodetest);
			break;
		case XsltForms_xpathAxis.DESCENDANT :
			XsltForms_stepExpr.pushDescendants(ctx, list, input, this.nodetest);
			break;
		case XsltForms_xpathAxis.FOLLOWING :
			var n = input.nodeType === XsltForms_nodeType.ATTRIBUTE ? input.ownerElement ? input.ownerElement : input.selectSingleNode("..") : input;
			while (n.nodeType !== XsltForms_nodeType.DOCUMENT) {
				for (var nn = n.nextSibling; nn; nn = nn.nextSibling) {
					XsltForms_stepExpr.push(ctx, list, nn, this.nodetest);
					XsltForms_stepExpr.pushDescendants(ctx, list, nn, this.nodetest);
				}
				n = n.parentNode;
			}
			break;
		case XsltForms_xpathAxis.FOLLOWING_SIBLING :
			for (var ns = input.nextSibling; ns; ns = ns.nextSibling) {
				XsltForms_stepExpr.push(ctx, list, ns, this.nodetest);
			}
			break;
		case XsltForms_xpathAxis.NAMESPACE : 
			alert('not implemented: axis namespace');
			break;
		case XsltForms_xpathAxis.PARENT :
			if (input.parentNode) {
				XsltForms_stepExpr.push(ctx, list, input.parentNode, this.nodetest);
			} else {
				if (input.nodeType === XsltForms_nodeType.ATTRIBUTE) {
					XsltForms_stepExpr.push(ctx, list, input.ownerElement ? input.ownerElement : input.selectSingleNode(".."), this.nodetest);
				}
			}
			break;
		case XsltForms_xpathAxis.PRECEDING :
			var p = input.nodeType === XsltForms_nodeType.ATTRIBUTE ? input.ownerElement ? input.ownerElement : input.selectSingleNode("..") : input;
			while (p.nodeType !== XsltForms_nodeType.DOCUMENT) {
				for (var ps = p.previousSibling; ps; ps = ps.previousSibling) {
					XsltForms_stepExpr.pushDescendantsRev(ctx, list, ps, this.nodetest);
					XsltForms_stepExpr.push(ctx, list, ps, this.nodetest);
				}
				p = p.parentNode;
			}
			break;
		case XsltForms_xpathAxis.PRECEDING_SIBLING :
			for (var ps2 = input.previousSibling; ps2; ps2 = ps2.previousSibling) {
				XsltForms_stepExpr.push(ctx, list, ps2, this.nodetest);
			}
			break;
		case XsltForms_xpathAxis.SELF :
			XsltForms_stepExpr.push(ctx, list, input, this.nodetest);
			break;
		default :
			throw {name:'ERROR -- NO SUCH AXIS: ' + this.axis};
	}
	for (var i = 0, len = this.predicates.length; i < len; i++) {
		var pred = this.predicates[i];
		var newList = [];
		for (var j = 0, len1 = list.length; j < len1; j++) {
			var x = list[j];
			var newCtx = ctx.clone(x, j + 1, list);
			if (XsltForms_globals.booleanValue(pred.evaluate(newCtx))) {
				newList.push(x);
			}
		}
		list = newList;
	}
	return list;
};

XsltForms_stepExpr.push = function(ctx, list, node, test, csensitive) {
	if (test.evaluate(node, ctx.nsresolver, csensitive) && !XsltForms_browser.inArray(node, list)) {
		list.push(node);
	}
};

XsltForms_stepExpr.pushList = function(ctx, list, l, test, csensitive) {
	for (var i = 0, len = l ? l.length : 0; i < len; i++) {
		XsltForms_stepExpr.push(ctx, list, l[i], test, csensitive);
	}
};

XsltForms_stepExpr.pushDescendants = function(ctx, list, node, test) {
	for (var n = node.firstChild; n; n = n.nextSibling) {
		XsltForms_stepExpr.push(ctx, list, n, test);
		arguments.callee(ctx, list, n, test);
	}
};

XsltForms_stepExpr.pushDescendantsRev = function(ctx, list, node, test) {
	for (var n = node.lastChild; n; n = n.previousSibling) {
		XsltForms_stepExpr.push(ctx, list, n, test);
		arguments.callee(ctx, list, n, test);
	}
};

	
		
		
		
function XsltForms_unionExpr(expr1, expr2) {
	this.expr1 = expr1;
	this.expr2 = expr2;
}


		

XsltForms_unionExpr.prototype.evaluate = function(ctx) {
	var nodes1 = XsltForms_globals.nodeSetValue(this.expr1.evaluate(ctx));
	var nodes2 = XsltForms_globals.nodeSetValue(this.expr2.evaluate(ctx));
	var len1 = nodes1.length;
	for (var i2 = 0, len = nodes2.length; i2 < len; i2++) {
		var found = false;
		for (var i1 = 0; i1 < len1; i1++) {
			found = nodes1[i1] === nodes2[i2];
			if (found) {
				break;
			}
		}
		if (!found) {
			nodes1.push(nodes2[i2]);
		}
	}
	return nodes1;
};

	
		
		
		
XsltForms_globals.stringValue = function(value) {
	return typeof value !== "object"? "" + value : (!value || value.length === 0 ? "" : XsltForms_globals.xmlValue(value[0]));
};


		

XsltForms_globals.booleanValue = function(value) {
	return typeof value === "undefined" || !value ? false : (typeof value.length !== "undefined"? value.length > 0 : !!value);
};


		

var nbvalcount = 0;
XsltForms_globals.numberValue = function(value) {
	if (typeof value === "boolean") {
		return 'A' - 0;
	} else {
		var v = typeof value === "object"?  XsltForms_globals.stringValue(value) : value;
		return v === '' ? NaN : v - 0;
	}
};


		

XsltForms_globals.nodeSetValue = function(value) {
	if (typeof value !== "object") {
		throw {name: this, message: Error().stack};
	}
	return value;
};


		

if (XsltForms_browser.isIE) {
	XsltForms_globals.xmlValue = function(node) {
		if (typeof node !== "object") {
			return node;
		}
		var ret = node.text;
		var schtyp = XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string");
		if (schtyp["eval"]) {
			try {
				ret = ret === "" ? 0 : eval(ret);
			} catch (e) {}
		}
		return ret;
	};
} else {
	XsltForms_globals.xmlValue = function(node) {
		if (typeof node !== "object") {
			return node;
		}
		var ret = typeof node.text !== "undefined" ? node.text : typeof node.textContent !== "undefined" ? node.textContent : typeof node.documentElement.text !== "undefined" ? node.documentElement.text : node.documentElement.textContent;
		var schtyp = XsltForms_schema.getType(XsltForms_browser.getType(node) || "xsd_:string");
		if (schtyp["eval"]) {
			try {
				ret = ret === "" ? 0 : eval(ret);
			} catch (e) {}
		}
		return ret;
	};
}


		

XsltForms_globals.xmlResolveEntities = function(s) {
	var parts = XsltForms_globals.stringSplit(s, '&');
	var ret = parts[0];
	for (var i = 1, len = parts.length; i < len; ++i) {
		var p = parts[i];
		var index = p.indexOf(";");
		if (index === -1) {
			ret += parts[i];
			continue;
		}
		var rp = p.substring(0, index);
		var ch;
		switch (rp) {
			case 'lt': ch = '<'; break;
			case 'gt': ch = '>'; break;
			case 'amp': ch = '&'; break;
			case 'quot': ch = '"'; break;
			case 'apos': ch = '\''; break;
			case 'nbsp': ch = String.fromCharCode(160); break;
			default:
				var span = XsltForms_browser.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", 'span') : document.createElement('span');
				span.innerHTML = '&' + rp + '; ';
				ch = span.childNodes[0].nodeValue.charAt(0);
		}
		ret += ch + p.substring(index + 1);
	}
	return ret;
};


		

XsltForms_globals.stringSplit = function(s, c) {
	var a = s.indexOf(c);
	if (a === -1) {
		return [s];
	}
	var cl = c.length;
	var parts = [];
	parts.push(s.substr(0,a));
	while (a !== -1) {
		var a1 = s.indexOf(c, a + cl);
		if (a1 !== -1) {
			parts.push(s.substr(a + cl, a1 - a - cl));
		} else {
			parts.push(s.substr(a + cl));
		} 
		a = a1;
	}
	return parts;
};

	
		
		
		
function XsltForms_xpath(subform, expression, unordered, compiled, ns) {
	this.subforms = [];
	this.subforms[subform] = true;
	this.nbsubforms = 1;
	subform.xpaths.push(this);
	this.expression = expression;
	this.unordered = unordered;
	if (typeof compiled === "string") {
		alert("XSLTForms Exception\n--------------------------\n\nError parsing the following XPath expression :\n\n"+expression+"\n\n"+compiled);
		return;
	}
	this.compiled = compiled;
	this.compiled.isRoot = true;
	this.nsresolver = new XsltForms_nsResolver();
	XsltForms_xpath.expressions[expression] = this;
	//if (ns.length > 0)  {
	for (var i = 0, len = ns.length; i < len; i += 2) {
		this.nsresolver.register(ns[i], ns[i + 1]);
	}
	//} else {
	//	this.nsresolver.register("", "http://www.w3.org/1999/xhtml");
	//}
	if (this.nsresolver.notfound) {
		XsltForms_xpath.notfound = true;
	}
	this.evaltime = 0;
}


		

XsltForms_xpath.prototype.evaluate = function(ctx, current) {
	var d1 = new Date();
	XsltForms_browser.assert(ctx);
//	alert("XPath evaluate \""+this.expression+"\"");
	if (!ctx.node) {
		ctx = new XsltForms_exprContext(ctx, null, null, null, this.nsresolver, current);
	} else if (!ctx.nsresolver) {
		ctx.nsresolver = this.nsresolver;
	}
	try {
		var res = this.compiled.evaluate(ctx);
		if (this.unordered && (res instanceof Array) && res.length > 1) {
			var posres = [];
			for (var i = 0, len = res.length; i < len; i++) {
				posres.push({count: XsltForms_browser.selectNodesLength("preceding::* | ancestor::*", res[i]), node: res[i]});
			}
			posres.sort(function(a,b){return a.count - b.count;});
			for (var i2 = 0, len2 = posres.length; i2 < len2; i2++) {
				res[i2] = posres[i2].node;
			}
		}
		var d2 = new Date();
		this.evaltime += d2 - d1;
		return res;
	} catch(e) {
		alert("XSLTForms Exception\n--------------------------\n\nError evaluating the following XPath expression :\n\n"+this.expression+"\n\n"+e.name+"\n\n"+e.message);
		return null;
	}
};


		

XsltForms_xpath.expressions = {};
XsltForms_xpath.notfound = false;


		

XsltForms_xpath.get = function(str) {
	return XsltForms_xpath.expressions[str];
};

		

XsltForms_xpath.create = function(subform, expression, unordered, compiled) {
	var xp = XsltForms_xpath.get(expression);
	if (xp) {
		compiled = null;
		if (!xp.subforms[subform]) {
			xp.subforms[subform] = true;
			xp.nbsubforms++;
			subform.xpaths.push(xp);
		}
	} else {
		var ns = [];
		for (var i = 4, len = arguments.length; i < len; i += 2) {
			ns[i-4] = arguments[i];
			ns[i-3] = arguments[i+1];
		}
		xp = new XsltForms_xpath(subform, expression, unordered, compiled, ns);
	}
};

		

XsltForms_xpath.prototype.dispose = function(subform) {
	if (subform && this.nbsubforms !== 1) {
		this.subforms[subform] = null;
		this.nbsubforms--;
		return;
	}
	this.compiled = null;
	this.nsresolver = null;
	XsltForms_xpath.expressions[this.expression] = null;
};

		

XsltForms_xpath.registerNS = function(prefix, uri) {
	if (XsltForms_xpath.notfound) {
		XsltForms_xpath.notfound = false;
		for (var exp in XsltForms_xpath.expressions) {
			if (XsltForms_xpath.expressions.hasOwnProperty(exp)) {
				XsltForms_xpath.expressions[exp].nsresolver.registerNotFound(prefix, uri);
				if (XsltForms_xpath.expressions[exp].nsresolver.notfound) {
					XsltForms_xpath.notfound = true;
				}
			}
		}
	}
};

	
		
		
		
function XsltForms_xpathFunction(acceptContext, defaultTo, returnNodes, body) {
	this.evaluate = body;
	this.defaultTo = defaultTo;
	this.acceptContext = acceptContext;
	this.returnNodes = returnNodes;
}

XsltForms_xpathFunction.DEFAULT_NONE = null;
XsltForms_xpathFunction.DEFAULT_NODE = 0;
XsltForms_xpathFunction.DEFAULT_NODESET = 1;
XsltForms_xpathFunction.DEFAULT_STRING = 2;


		

XsltForms_xpathFunction.prototype.call = function(context, arguments_) {
	if (arguments_.length === 0) {
		switch (this.defaultTo) {
		case XsltForms_xpathFunction.DEFAULT_NODE:
			if (context.node) {
				arguments_ = [context.node];
			}
			break;
		case XsltForms_xpathFunction.DEFAULT_NODESET:
			if (context.node) {
				arguments_ = [[context.node]];
			}
			break;
		case XsltForms_xpathFunction.DEFAULT_STRING:
			arguments_ = [XsltForms_xpathCoreFunctions.string.evaluate([context.node])];
			break;
		}
	}
	if (this.acceptContext) {
		arguments_.unshift(context);
	}
	return this.evaluate.apply(null, arguments_);
};

	
		
		
		
var XsltForms_mathConstants = {
	"PI":      "3.14159265358979323846264338327950288419716939937510582",
	"E":       "2.71828182845904523536028747135266249775724709369995958",
	"SQRT2":   "1.41421356237309504880168872420969807856967187537694807",
	"LN2":     "0.693147180559945309417232121458176568075500134360255254",
	"LN10":    "2.30258509299404568401799145468436420760110148862877298",
	"LOG2E":   "1.44269504088896340735992468100189213742664595415298594",
	"SQRT1_2": "0.707106781186547524400844362104849039284835937688474038"
};
		
var XsltForms_xpathCoreFunctions = {

		

	"http://www.w3.org/2005/xpath-functions last" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(ctx) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.lastInvalidArgumentsNumber;
			}
			return ctx.nodelist.length;
		} ),

		

	"http://www.w3.org/2005/xpath-functions position" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(ctx) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.positionInvalidArgumentsNumber;
			}
			return ctx.position;
		} ),

		

	"http://www.w3.org/2002/xforms context" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(ctx) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.positionInvalidArgumentsNumber;
			}
			return [ctx.current];
		} ),

		

	"http://www.w3.org/2005/xpath-functions count" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet) { 
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.countInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.countInvalidArgumentType;
			}
			return nodeSet.length;
		} ),

		

	"http://www.w3.org/2005/xpath-functions id" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NODE, false,
		function(context, object, ref) {
			if (arguments.length !== 2 && arguments.length !== 3) {
				throw XsltForms_xpathFunctionExceptions.idInvalidArgumentsNumber;
			}
			if (typeof object !== "object" && typeof object !== "string") {
				throw XsltForms_xpathFunctionExceptions.idInvalidArgumentType;
			}
			var result = [];
			if (!ref) {
				ref = context.node.ownerDocument ? [context.node.ownerDocument] : [context.node];
			}
			if (typeof object !== "string" && typeof(object.length) !== "undefined") {
				for (var i = 0, len = object.length; i < len; ++i) {
					var res = XsltForms_xpathCoreFunctions['http://www.w3.org/2005/xpath-functions id'].evaluate(context, object[i], ref);
					for (var j = 0, len1 = res.length; j < len1; j++) {
						result.push(res[j]);
					}
				}
			} else if (context.node) {
				var ids = XsltForms_globals.stringValue(object).split(/\s+/);
				var idattr = XsltForms_globals.IDstr ? XsltForms_globals.IDstr : "@xml:id";
				for (var k = 0, len2 = ids.length; k < len2; k++) {
					var n = XsltForms_browser.selectSingleNode("descendant-or-self::*[" + idattr + "='" + ids[k] + "']", ref[0]);
					if (n) {
						result.push(n);
					}
				}
			}
			return result;
		} ),

		

	"http://www.w3.org/2005/xpath-functions local-name" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.localNameInvalidArgumentsNumber;
			}
			if (arguments.length === 1 && typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.localNameInvalidArgumentType;
			}
			if (arguments.length === 0) {
				throw XsltForms_xpathFunctionExceptions.localNameNoContext;
			}
			return nodeSet.length === 0 ? "" : nodeSet[0].nodeName.replace(/^.*:/, "");
		} ),

		

	"http://www.w3.org/2005/xpath-functions namespace-uri" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.namespaceUriInvalidArgumentsNumber;
			}
			if (arguments.length === 1 && typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.namespaceUriInvalidArgumentType;
			}
			return nodeSet.length === 0? "" : nodeSet[0].namespaceURI || "";
		} ),

		

	"http://www.w3.org/2005/xpath-functions name" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.nameInvalidArgumentsNumber;
			}
			if (arguments.length === 1 && typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.nameInvalidArgumentType;
			}
			return nodeSet.length === 0? "" : nodeSet[0].nodeName;
		} ),

		

	"http://www.w3.org/2005/xpath-functions string" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(object) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.stringInvalidArgumentsNumber;
			}
			return XsltForms_globals.stringValue(object);
		} ),

		

	"http://www.w3.org/2005/xpath-functions concat" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length <2) {
				throw XsltForms_xpathFunctionExceptions.concatInvalidArgumentsNumber;
			}
			var string = "";
			for (var i = 0, len = arguments.length; i < len; ++i) {
				string += XsltForms_globals.stringValue(arguments[i]);
			}
			return string;
		} ),

		

	"http://www.w3.org/2005/xpath-functions starts-with" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, prefix) {   
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.startsWithInvalidArgumentsNumber;
			}
			return XsltForms_globals.stringValue(string).indexOf(XsltForms_globals.stringValue(prefix)) === 0;
		} ),

		

	"http://www.w3.org/2005/xpath-functions contains" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, substring) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.containsInvalidArgumentsNumber;
			}
			return XsltForms_globals.stringValue(string).indexOf(XsltForms_globals.stringValue(substring)) !== -1;
		} ),

		

	"http://www.w3.org/2005/xpath-functions substring-before" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, substring) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.substringBeforeInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string);
			return string.substring(0, string.indexOf(XsltForms_globals.stringValue(substring)));
		} ),

		

	"http://www.w3.org/2005/xpath-functions substring-after" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, substring) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.substringAfterInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string);
			substring = XsltForms_globals.stringValue(substring);
			var index = string.indexOf(substring);
			return index === -1 ? "" : string.substring(index + substring.length);
		} ),

		

	"http://www.w3.org/2005/xpath-functions substring" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, index, length) {
			if (arguments.length !== 2 && arguments.length !== 3) {
				throw XsltForms_xpathFunctionExceptions.substringInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string);
			index  = Math.round(XsltForms_globals.numberValue(index));
			if (isNaN(index)) {
				return "";
			}
			if (length) {
				length = Math.round(XsltForms_globals.numberValue(length));
				if (index <= 0) {
					return string.substr(0, index + length - 1);
				}
				return string.substr(index - 1, length);
			}
			return string.substr(Math.max(index - 1, 0));
		} ),

		

	"http://www.w3.org/2005/xpath-functions compare" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string1, string2) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.compareInvalidArgumentsNumber;
			}
			string1 = XsltForms_globals.stringValue(string1);
			string2 = XsltForms_globals.stringValue(string2);
			return (string1 === string2 ? 0 : (string1 > string2 ? 1 : -1));
		} ),

		

	"http://www.w3.org/2005/xpath-functions string-length" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_STRING, false,
		function(string) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.stringLengthInvalidArgumentsNumber;
			}
			return XsltForms_globals.stringValue(string).length;
		} ),

		

	"http://www.w3.org/2005/xpath-functions normalize-space" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_STRING, false,
		function(string) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.normalizeSpaceLengthInvalidArgumentsNumber;
			}
			return XsltForms_globals.stringValue(string).replace(/^\s+|\s+$/g, "")
				.replace(/\s+/, " ");
		} ),

		

	"http://www.w3.org/2005/xpath-functions translate" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, from, to) {
			if (arguments.length !== 3) {
				throw XsltForms_xpathFunctionExceptions.translateInvalidArgumentsNumber;
			}
			string =  XsltForms_globals.stringValue(string);
			from = XsltForms_globals.stringValue(from);
			to = XsltForms_globals.stringValue(to);
			var result = "";
			for (var i = 0, len = string.length; i < len; ++i) {
				var index = from.indexOf(string.charAt(i));
				result += index === -1? string.charAt(i) : to.charAt(index);
			}
			return result;
		} ),

		

	"http://www.w3.org/2005/xpath-functions boolean" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(object) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.booleanInvalidArgumentsNumber;
			}
			return XsltForms_globals.booleanValue(object);
		} ),

		

	"http://www.w3.org/2005/xpath-functions not" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(condition) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.notInvalidArgumentsNumber;
			}
			return !XsltForms_globals.booleanValue(condition);
		} ),

		

	"http://www.w3.org/2005/xpath-functions true" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length !== 0) {
				throw XsltForms_xpathFunctionExceptions.trueInvalidArgumentsNumber;
			}
			return true;
		} ),

		

	"http://www.w3.org/2005/xpath-functions false" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length !== 0) {
				throw XsltForms_xpathFunctionExceptions.falseInvalidArgumentsNumber;
			}
			return false;
		} ),

		

	"http://www.w3.org/2005/xpath-functions lang" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(context, language) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.langInvalidArgumentsNumber;
			}
			language = XsltForms_globals.stringValue(language);
			for (var node = context.node; node; node = node.parentNode) {
				if (typeof(node.attributes) === "undefined") {
					continue;
				}
				var xmlLang = node.attributes.getNamedItemNS("http://www.w3.org/XML/1998/namespace", "lang");
				if (xmlLang) {
					xmlLang  = xmlLang.value.toLowerCase();
					language = language.toLowerCase();
					return xmlLang.indexOf(language) === 0 && (language.length === xmlLang.length || language.charAt(xmlLang.length) === '-');
				}
			}
			return false;
		} ),

		

	"http://www.w3.org/2005/xpath-functions number" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(object) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.numberInvalidArgumentsNumber;
			}
			return XsltForms_globals.numberValue(object);
		} ),

		

	"http://www.w3.org/2005/xpath-functions sum" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.sumInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.sumInvalidArgumentType;
			}
			var sum = 0;
			for (var i = 0, len = nodeSet.length; i < len; ++i) {
				sum += XsltForms_globals.numberValue(XsltForms_globals.xmlValue(nodeSet[i]));
			}
			return sum;
		} ),

		

	"http://www.w3.org/2005/xpath-functions floor" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.floorInvalidArgumentsNumber;
			}
			return Math.floor(XsltForms_globals.numberValue(number));
		} ),

		

	"http://www.w3.org/2005/xpath-functions ceiling" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.ceilingInvalidArgumentsNumber;
			}
			return Math.ceil(XsltForms_globals.numberValue(number));
		} ),

		

	"http://www.w3.org/2005/xpath-functions round" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.roundInvalidArgumentsNumber;
			}
			return Math.round(XsltForms_globals.numberValue(number));
		} ),

		

	"http://www.w3.org/2002/xforms power" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(x, y) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.powerInvalidArgumentsNumber;
			}
			return Math.pow(XsltForms_globals.numberValue(x), XsltForms_globals.numberValue(y));
		} ),

		

	"http://www.w3.org/2002/xforms random" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.randomInvalidArgumentsNumber;
			}
			return Math.random();
		} ),

		

	"http://www.w3.org/2002/xforms boolean-from-string" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.booleanFromStringInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string);
			switch (string.toLowerCase()) {
				case "true":  case "1": return true;
				case "false": case "0": return false;
				default: return false;
			}
		} ),

		

	"http://www.w3.org/2002/xforms if" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, true,
		function(condition, onTrue, onFalse) {
			if (arguments.length !== 3) {
				throw XsltForms_xpathFunctionExceptions.ifInvalidArgumentsNumber;
			}
			return XsltForms_globals.booleanValue(condition)? onTrue : onFalse;
		} ),

		

	"http://www.w3.org/2002/xforms choose" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, true,
		function(condition, onTrue, onFalse) {
			if (arguments.length !== 3) {
				throw XsltForms_xpathFunctionExceptions.chooseInvalidArgumentsNumber;
			}
			return XsltForms_globals.booleanValue(condition)? onTrue : onFalse;
		} ),

		

	"http://www.w3.org/2005/xpath-functions avg" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.avgInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.avgInvalidArgumentType;
			}
			var sum = XsltForms_xpathCoreFunctions['http://www.w3.org/2005/xpath-functions sum'].evaluate(nodeSet);
			var quant = XsltForms_xpathCoreFunctions['http://www.w3.org/2005/xpath-functions count'].evaluate(nodeSet);
			return sum / quant;
		} ),

		

	"http://www.w3.org/2005/xpath-functions min" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function (nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.minInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.minInvalidArgumentType;
			}
			if (nodeSet.length === 0) {
				return NaN;
			}
			var minimum = XsltForms_globals.numberValue(XsltForms_globals.xmlValue(nodeSet[0]));
			for (var i = 1, len = nodeSet.length; i < len; ++i) {
				var value = XsltForms_globals.numberValue(XsltForms_globals.xmlValue(nodeSet[i]));
				if (isNaN(value)) {
					return NaN;
				}
				if (value < minimum) {
					minimum = value;
				}
			}
			return minimum;
		} ),

		

	"http://www.w3.org/2005/xpath-functions max" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function (nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.maxInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.maxInvalidArgumentType;
			}
			if (nodeSet.length === 0) {
				return NaN;
			}
			var maximum = XsltForms_globals.numberValue(XsltForms_globals.xmlValue(nodeSet[0]));
			for (var i = 1, len = nodeSet.length; i < len; ++i) {
				var value = XsltForms_globals.numberValue(XsltForms_globals.xmlValue(nodeSet[i]));
				if (isNaN(value)) {
					return NaN;
				}
				if (value > maximum) {
					maximum = value;
				}
			}
			return maximum;
		} ),

		

	"http://www.w3.org/2002/xforms count-non-empty" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.countNonEmptyInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.countNonEmptyInvalidArgumentType;
			}
			var count = 0;
			for (var i = 0, len = nodeSet.length; i < len; ++i) {
				if (XsltForms_globals.xmlValue(nodeSet[i]).length > 0) {
					count++;
				}
			}
			return count;
		} ),

		

	"http://www.w3.org/2002/xforms index" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(ctx, id) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.indexInvalidArgumentsNumber;
			}
			var xf = XsltForms_idManager.find(XsltForms_globals.stringValue(id)).xfElement;
			ctx.addDepElement(xf);
			return xf.index;
		} ),

		

	"http://www.w3.org/2002/xforms nodeindex" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(ctx, id) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.nodeIndexInvalidArgumentsNumber;
			}
			var control = XsltForms_idManager.find(XsltForms_globals.stringValue(id));
			var node = control.node;
			ctx.addDepElement(control.xfElement);
			if (node) {
				ctx.addDepNode(node);
				ctx.addDepElement(document.getElementById(XsltForms_browser.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "model")).xfElement);
			}
			return node? [ node ] : [];
		} ),

		

	"http://www.w3.org/2002/xforms property" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(name) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.propertyInvalidArgumentsNumber;
			}
			name = XsltForms_globals.stringValue(name);
			switch (name) {
				case "version": return "1.1";
				case "conformance-level": return "full";
				case "xsltforms:debug-mode": return XsltForms_globals.debugMode ? "on" : "off";
				case "xsltforms:version": return XsltForms_globals.fileVersion;
				case "xsltforms:version-number": return ""+XsltForms_globals.fileVersionNumber;
				default:
					if (name.substring(0,4) === "xsl:") {
						var xslname = name.substring(4);
						var xsltsrc = '<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:msxsl="urn:schemas-microsoft-com:xslt">' +
						'	<xsl:output method="xml"/>' +
						'	<xsl:template match="/">' +
						'		<xsl:variable name="version">' +
						'			<xsl:if test="system-property(\'xsl:vendor\')=\'Microsoft\'">' +
						'				<xsl:value-of select="system-property(\'msxsl:version\')"/>' +
						'			</xsl:if>' +
						'		</xsl:variable>' +
						'		<properties><xsl:value-of select="concat(\'|vendor=\',system-property(\'xsl:vendor\'),\'|vendor-url=\',system-property(\'xsl:vendor-url\'),\'|vendor-version=\',$version,\'|\')"/></properties>' +
						'	</xsl:template>' +
						'</xsl:stylesheet>';
						var res = XsltForms_browser.transformText("<dummy/>", xsltsrc, true);
						var spres = res.split("|");
						for (var i = 1, len = spres.length; i < len; i++) {
							var spprop = spres[i].split("=", 2);
							if (spprop[0] === xslname) {
								return spprop[1];
							}
						}
					}
			}
			return "";
		} ),

		

	"http://www.w3.org/2002/xforms instance" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, true,
		function(ctx, idRef) {
			if (arguments.length > 2) {
				throw XsltForms_xpathFunctionExceptions.instanceInvalidArgumentsNumber;
			}
			var name = idRef ? XsltForms_globals.stringValue(idRef) : "";
			if (name !== "") {
				var instance = document.getElementById(name);
				if (!instance) { throw {name: "instance " + name + " not found"}; }
				return [instance.xfElement.doc.documentElement];
			} else {
				return [ctx.node.ownerDocument.documentElement];
			}
		} ),

		

	"http://www.w3.org/2002/xforms now" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length !== 0) {
				throw XsltForms_xpathFunctionExceptions.nowInvalidArgumentsNumber;
			}
			return XsltForms_browser.i18n.format(new Date(), "yyyy-MM-ddThh:mm:ssz", false);
		} ),

		

	"http://www.w3.org/2002/xforms local-date" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length !== 0) {
				throw XsltForms_xpathFunctionExceptions.localDateInvalidArgumentsNumber;
			}
			return XsltForms_browser.i18n.format(new Date(), "yyyy-MM-ddz", true);
		} ),

		

	"http://www.w3.org/2002/xforms local-dateTime" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length !== 0) {
				throw XsltForms_xpathFunctionExceptions.localDateTimeInvalidArgumentsNumber;
			}
			return XsltForms_browser.i18n.format(new Date(), "yyyy-MM-ddThh:mm:ssz", true);
		} ),

		

	"http://www.w3.org/2002/xforms days-from-date" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.daysFromDateInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string);
			if( !XsltForms_schema.getType("xsd_:date").validate(string) && !XsltForms_schema.getType("xsd_:dateTime").validate(string)) {
				return "NaN";
			}
			var p = /^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])/;
			var c = p.exec(string);
			var d = new Date(Date.UTC(c[1], c[2]-1, c[3]));
			return Math.floor(d.getTime()/ 86400000 + 0.000001);
		} ),

		

	"http://www.w3.org/2002/xforms days-to-date" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.daysToDateInvalidArgumentsNumber;
			}
			number = XsltForms_globals.numberValue(number);
			if( isNaN(number) ) {
				return "";
			}
			var d = new Date();
			d.setTime(Math.floor(number + 0.000001) * 86400000);
			return XsltForms_browser.i18n.format(d, "yyyy-MM-dd", false);
		} ),

		

	"http://www.w3.org/2002/xforms seconds-from-dateTime" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.secondsFromDateTimeInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string);
			if( !XsltForms_schema.getType("xsd_:dateTime").validate(string)) {
				return "NaN";
			}
			var p = /^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])(\.[0-9]+)?(Z|[+\-])?([01][0-9]|2[0-3])?:?([0-5][0-9])?/;
			var c = p.exec(string);
			var d = new Date(Date.UTC(c[1], c[2]-1, c[3], c[4], c[5], c[6]));
			if (c[8] && c[8] !== "Z") {
				d.setUTCMinutes(d.getUTCMinutes() + (c[8] === "+" ? 1 : -1)*(c[9]*60 + c[10]));
			}
			return Math.floor(d.getTime() / 1000 + 0.000001) + (c[7]?c[7]:0);
		} ),

		

	"http://www.w3.org/2002/xforms seconds-to-dateTime" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.secondsToDateTimeInvalidArgumentsNumber;
			}
			number = XsltForms_globals.numberValue(number);
			if( isNaN(number) ) {
				return "";
			}
			var d = new Date();
			d.setTime(Math.floor(number + 0.000001) * 1000);
			return XsltForms_browser.i18n.format(d, "yyyy-MM-ddThh:mm:ssz", false);
		} ),

		

	"http://www.w3.org/2002/xforms current" : new XsltForms_xpathFunction(true, XsltForms_xpathFunction.DEFAULT_NONE, true,
		function(ctx) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.currentInvalidArgumentsNumber;
			}
			ctx.addDepNode(ctx.node);
			ctx.addDepElement(document.getElementById(XsltForms_browser.getMeta(ctx.node.documentElement ? ctx.node.documentElement : ctx.node.ownerDocument.documentElement, "model")).xfElement);
			return [ctx.current];
		} ),

		

	"http://www.w3.org/2002/xforms is-valid" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.isValidInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.isValidInvalidArgumentType;
			}
			var valid = true;
			for (var i = 0, len = nodeSet.length; valid && i < len; i++) {
				valid = valid && XsltForms_globals.validate_(nodeSet[i]);
			}
			return valid;
		} ),

		

	"http://www.w3.org/2002/xforms is-card-number" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(string) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.isCardNumberInvalidArgumentsNumber;
			}
			string = XsltForms_globals.stringValue(string).trim();
			var sum = 0;
			var tab = new Array(string.length);
			for (var i = 0, l = string.length; i < l; i++) {
				tab[i] = string.charAt(i) - '0';
				if( tab[i] < 0 || tab[i] > 9 ) {
					return false;
				}
			}
			for (var j = tab.length-2; j >= 0; j -= 2) {
				tab[j] *= 2;
				if( tab[j] > 9 ) {
					tab[j] -= 9;
				}
			}
			for (var k = 0, l2 = tab.length; k < l2; k++) {
				sum += tab[k];
			}
			return sum % 10 === 0;
		} ),

		

/*jshint bitwise:false */
	"http://www.w3.org/2002/xforms digest" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(str, algo, enco) {
			if (arguments.length !== 2 && arguments.length !== 3) {
				throw XsltForms_xpathFunctionExceptions.digestInvalidArgumentsNumber;
			}
			str = XsltForms_globals.stringValue(str);
			algo = XsltForms_globals.stringValue(algo);
			enco = enco ? XsltForms_globals.stringValue(enco) : "base64";
			var i;
			var b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
			switch (algo) {
				case "SHA-1" :
					var l = str.length;
					var bl = l*8;
					var W = [];
					var H0 = 0x67452301;
					var H1 = 0xefcdab89;
					var H2 = 0x98badcfe;
					var H3 = 0x10325476;
					var H4 = 0xc3d2e1f0;
					var a, b, c, d, e, T;
					var msg = [];
					for(i = 0; i < l; i++){
						msg[i >> 2] |= (str.charCodeAt(i)& 0xFF)<<((3-i%4)<<3);
					}
					msg[bl >> 5] |= 0x80 <<(24-bl%32);
					msg[((bl+65 >> 9)<< 4)+ 15] = bl;
					l = msg.length;
					var rotl = function(x,n){
						return(x <<  n)|(x >>>(32-n));
					};
					var add32 = function(x,y){
						var lsw = (x & 0xFFFF)+(y & 0xFFFF);
						return ((((x >>> 16)+(y >>> 16)+(lsw >>> 16)) & 0xFFFF)<< 16)|(lsw & 0xFFFF);
					};
					for(i = 0; i < l; i += 16){
						a = H0;
						b = H1;
						c = H2;
						d = H3;
						e = H4;
						var t;
						for(t = 0; t<20; t++){
							T = add32(add32(add32(add32(rotl(a,5),(b & c)^(~b & d)),e),0x5a827999),W[t] = t<16 ? msg[t+i] : rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d;
							d = c;
							c = rotl(b,30);
							b = a;
							a = T;
						}
						for(t = 20; t<40; t++){
							T = add32(add32(add32(add32(rotl(a,5),b^c^d),e),0x6ed9eba1),W[t] = rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d;
							d = c;
							c = rotl(b,30);
							b = a;
							a = T;
						}
						for(t = 40; t<60; t++){
							T = add32(add32(add32(add32(rotl(a,5),(b & c)^(b & d)^(c & d)),e),0x8f1bbcdc),W[t] = rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d;
							d = c;
							c = rotl(b,30);
							b = a;
							a = T;
						}
						for(t = 60; t<80; t++){
							T = add32(add32(add32(add32(rotl(a,5),b^c^d),e),0xca62c1d6),W[t] = rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d;
							d = c;
							c = rotl(b,30);
							b = a;
							a = T;
						}
						H0 = add32(a,H0);
						H1 = add32(b,H1);
						H2 = add32(c,H2);
						H3 = add32(d,H3);
						H4 = add32(e,H4);
					}
					var hex32 = function(v) {
						var h = v >>> 16;
						var l = v & 0xFFFF;
						return (h >= 0x1000 ? "" : h >= 0x100 ? "0" : h >= 0x10 ? "00" : "000")+h.toString(16)+(l >= 0x1000 ? "" : l >= 0x100 ? "0" : l >= 0x10 ? "00" : "000")+l.toString(16);
					};
					var b12 = function(v) {
						return b64.charAt((v >>> 6) & 0x3F)+b64.charAt(v & 0x3F);
					};
					var b30 = function(v) {
						return b64.charAt(v >>> 24)+b64.charAt((v >>> 18) & 0x3F)+b64.charAt((v >>> 12) & 0x3F)+b64.charAt((v >>> 6) & 0x3F)+b64.charAt(v & 0x3F);
					};
					switch (enco) {
						case "hex" :
							return hex32(H0)+hex32(H1)+hex32(H2)+hex32(H3)+hex32(H4);
						case "base64" :
							return b30(H0 >>> 2)+b30(((H0 & 0x3) << 28) | (H1 >>> 4))+b30(((H1 & 0xF) << 26) | (H2 >>> 6))+b30(((H2 & 0x3F) << 24) | (H3 >>> 8))+b30(((H3 & 0xFF) << 22) | (H4 >>> 10))+b12((H4 & 0x3FF)<<2)+"=";
					}
					break;
				case 'BASE64':
					str = str.replace(/\r\n/g,"\n");
					var l2 = str.length;
					var str2 = "";
					for (i = 0; i < l2; i++) {
						var c0 = str.charCodeAt(i);
						str2 += c0 < 128 ? str.charAt(i) : c0 > 127 && c0 < 2048 ? String.fromCharCode(c0 >> 6 | 192, c0 & 63 | 128) : String.fromCharCode(c0 >> 12 | 224, c0 >> 6 & 63 | 128, c0 & 63 | 128);
					}
					l2 = str2.length;
					var res = "";
					for (i = 0; i < l2; i += 3) {
						var c1 = str2.charCodeAt(i);
						var c2 = i + 1 < l2 ? str2.charCodeAt(i + 1) : 0;
						var c3 = i + 2 < l2 ? str2.charCodeAt(i + 2) : 0;
						res += b64.charAt(c1 >> 2) + b64.charAt((c1 & 3) << 4 | c2 >> 4) + (i + 1 < l ? b64.charAt((c2 & 15) << 2 | c3 >> 6) : "=") + (i + 2 < l ? b64.charAt(c3 & 63) : "=");
					}
					return res;
			}
			return "unsupported";
		} ),
/*jshint bitwise:true */

		

	"http://www.w3.org/2005/xpath-functions upper-case" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(str) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.upperCaseInvalidArgumentsNumber;
			}
			str = XsltForms_globals.stringValue(str);
			return str.toUpperCase();
		} ),

		

	"http://www.w3.org/2005/xpath-functions lower-case" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(str) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.lowerCaseInvalidArgumentsNumber;
			}
			str = XsltForms_globals.stringValue(str);
			return str.toLowerCase();
		} ),

		

	"http://www.w3.org/2005/xpath-functions distinct-values" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.distinctValuesInvalidArgumentsNumber;
			}
			var nodeSet2 = [];
			var values = {};
			for (var i = 0, len = nodeSet.length; i < len; ++i) {
				var xvalue = XsltForms_globals.xmlValue(nodeSet[i]);
				if (!values[xvalue]) {
					nodeSet2.push(nodeSet[i]);
					values[xvalue] = true;
				}
			}
			return nodeSet2;
		} ),

		

	"http://www.w3.org/2002/xforms transform" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet, xslhref) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.transformInvalidArgumentsNumber;
			}
			xslhref = XsltForms_globals.stringValue(xslhref);
			return nodeSet.length === 0? "" : XsltForms_browser.transformText(XsltForms_browser.saveXML(nodeSet[0]), xslhref, false);
		} ),

		

	"http://www.w3.org/2002/xforms serialize" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODE, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.serializeInvalidArgumentsNumber;
			}
			if (arguments.length === 1 && typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.serializeInvalidArgumentType;
			}
			if (arguments.length === 0) {
				throw XsltForms_xpathFunctionExceptions.serializeNoContext;
			}
			return nodeSet.length === 0 ? "" : XsltForms_browser.saveXML(nodeSet[0]);
		} ),

		

	"http://www.w3.org/2002/xforms event" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(attribute) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.eventInvalidArgumentsNumber;
			}
			var context = XsltForms_xmlevents.EventContexts[XsltForms_xmlevents.EventContexts.length - 1];
			if (context) {
				return context[attribute];
			} else {
				return null;
			}
		} ),

		

	"http://www.w3.org/2005/xpath-functions is-non-empty-array" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NODESET, false,
		function(nodeset) {
			if (arguments.length > 1) {
				throw XsltForms_xpathFunctionExceptions.isNonEmptyArrayInvalidArgumentsNumber;
			}
			if (typeof nodeset[0] !== "object") {
				throw XsltForms_xpathFunctionExceptions.isNonEmptyArrayInvalidArgumentType;
			}
			return nodeset[0].getAttribute("exsi:maxOccurs") && nodeset[0].getAttribute("xsi:nil") !== "true";
		} ),

		

	"http://exslt.org/math abs" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.abs(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math acos" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.acos(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math asin" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.asin(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math atan" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.atan(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math atan2" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number1, number2) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.math2InvalidArgumentsNumber;
			}
			return Math.atan2(XsltForms_globals.numberValue(number1), XsltForms_globals.numberValue(number2));
		} ),

		

	"http://exslt.org/math constant" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(string, number) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.math2InvalidArgumentsNumber;
			}
			var val = XsltForms_mathConstants[XsltForms_globals.stringValue(string)] || "0";
			return parseFloat(val.substr(0, XsltForms_globals.numberValue(number)+2));
		} ),

		

	"http://exslt.org/math cos" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.cos(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math exp" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.exp(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math log" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.log(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math power" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number1, number2) {
			if (arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.math2InvalidArgumentsNumber;
			}
			return Math.pow(XsltForms_globals.numberValue(number1), XsltForms_globals.numberValue(number2));
		} ),

		

	"http://exslt.org/math sin" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.sin(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math sqrt" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.sqrt(XsltForms_globals.numberValue(number));
		} ),

		

	"http://exslt.org/math tan" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.math1InvalidArgumentsNumber;
			}
			return Math.tan(XsltForms_globals.numberValue(number));
		} ),

		

	"http://www.w3.org/2005/xpath-functions alert" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(arg) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.alertInvalidArgumentsNumber;
			}
			alert(XsltForms_globals.stringValue(arg));
			return arg;
		} ),

		

	"http://www.w3.org/2005/xpath-functions js-eval" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(arg) {
			if (arguments.length !== 1) {
				throw XsltForms_xpathFunctionExceptions.jsevalInvalidArgumentsNumber;
			}
			return eval(XsltForms_globals.stringValue(arg));
		} ),

		

	"http://www.w3.org/2005/xpath-functions string-join" : new XsltForms_xpathFunction(false, XsltForms_xpathFunction.DEFAULT_NONE, false,
		function(nodeSet, joinString) { 
			if (arguments.length !== 1 && arguments.length !== 2) {
				throw XsltForms_xpathFunctionExceptions.stringJoinInvalidArgumentsNumber;
			}
			if (typeof nodeSet !== "object") {
				throw XsltForms_xpathFunctionExceptions.stringJoinInvalidArgumentType;
			}
			var strings = [];
			joinString = joinString || "";
			for (var i = 0, len = nodeSet.length; i < len; i++) {
				strings.push(XsltForms_globals.xmlValue(nodeSet[i]));
			}
			return strings.join(joinString);
		} )
};

var XsltForms_xpathFunctionExceptions = {
	lastInvalidArgumentsNumber : {
		name : "last() : Invalid number of arguments",
		message : "last() function has no argument"
	},
	positionInvalidArgumentsNumber : {
		name : "position() : Invalid number of arguments",
		message : "position() function has no argument"
	},
	countInvalidArgumentsNumber : {
		name : "count() : Invalid number of arguments",
		message : "count() function must have one argument exactly"
	},
	countInvalidArgumentType : {
		name : "count() : Invalid type of argument",
		message : "count() function must have a nodeset argument"
	},
	idInvalidArgumentsNumber : {
		name : "id() : Invalid number of arguments",
		message : "id() function must have one argument exactly"
	},
	idInvalidArgumentType : {
		name : "id() : Invalid type of argument",
		message : "id() function must have a nodeset or string argument"
	},
	localNameInvalidArgumentsNumber : {
		name : "local-name() : Invalid number of arguments",
		message : "local-name() function must have one argument at most"
	},
	localNameInvalidArgumentType : {
		name : "local-name() : Invalid type of argument",
		message : "local-name() function must have a nodeset argument"
	},
	localNameNoContext : {
		name : "local-name() : no context node",
		message : "local-name() function must have a nodeset argument"
	},
	namespaceUriInvalidArgumentsNumber : {
		name : "namespace-uri() : Invalid number of arguments",
		message : "namespace-uri() function must have one argument at most"
	},
	namespaceUriInvalidArgumentType : {
		name : "namespace-uri() : Invalid type of argument",
		message : "namespace-uri() function must have a nodeset argument"
	},
	nameInvalidArgumentsNumber : {
		name : "name() : Invalid number of arguments",
		message : "name() function must have one argument at most"
	},
	nameInvalidArgumentType : {
		name : "name() : Invalid type of argument",
		message : "name() function must have a nodeset argument"
	},
	stringInvalidArgumentsNumber : {
		name : "string() : Invalid number of arguments",
		message : "string() function must have one argument at most"
	},
	concatInvalidArgumentsNumber : {
		name : "concat() : Invalid number of arguments",
		message : "concat() function must have at least two arguments"
	},
	startsWithInvalidArgumentsNumber : {
		name : "starts-with() : Invalid number of arguments",
		message : "starts-with() function must have two arguments exactly"
	},
	containsInvalidArgumentsNumber : {
		name : "contains() : Invalid number of arguments",
		message : "contains() function must have two arguments exactly"
	},
	substringBeforeInvalidArgumentsNumber : {
		name : "substring-before() : Invalid number of arguments",
		message : "substring-before() function must have two arguments exactly"
	},
	substringAfterInvalidArgumentsNumber : {
		name : "substring-after() : Invalid number of arguments",
		message : "substring-after() function must have two arguments exactly"
	},
	substringInvalidArgumentsNumber : {
		name : "substring() : Invalid number of arguments",
		message : "substring() function must have two or three arguments"
	},
	compareInvalidArgumentsNumber : {
		name : "compare() : Invalid number of arguments",
		message : "compare() function must have two arguments exactly"
	},
	stringLengthInvalidArgumentsNumber : {
		name : "string-length() : Invalid number of arguments",
		message : "string-length() function must have one argument at most"
	},
	normalizeSpaceInvalidArgumentsNumber : {
		name : "normalize-space() : Invalid number of arguments",
		message : "normalize-space() function must have one argument at most"
	},
	translateInvalidArgumentsNumber : {
		name : "translate() : Invalid number of arguments",
		message : "translate() function must have three argument exactly"
	},
	booleanInvalidArgumentsNumber : {
		name : "boolean() : Invalid number of arguments",
		message : "boolean() function must have one argument exactly"
	},
	notInvalidArgumentsNumber : {
		name : "not() : Invalid number of arguments",
		message : "not() function must have one argument exactly"
	},
	trueInvalidArgumentsNumber : {
		name : "true() : Invalid number of arguments",
		message : "true() function must have no argument"
	},
	falseInvalidArgumentsNumber : {
		name : "false() : Invalid number of arguments",
		message : "false() function must have no argument"
	},
	langInvalidArgumentsNumber : {
		name : "lang() : Invalid number of arguments",
		message : "lang() function must have one argument exactly"
	},
	numberInvalidArgumentsNumber : {
		name : "number() : Invalid number of arguments",
		message : "number() function must have one argument exactly"
	},
	sumInvalidArgumentsNumber : {
		name : "sum() : Invalid number of arguments",
		message : "sum() function must have one argument exactly"
	},
	sumInvalidArgumentType : {
		name : "sum() : Invalid type of argument",
		message : "sum() function must have a nodeset argument"
	},
	floorInvalidArgumentsNumber : {
		name : "floor() : Invalid number of arguments",
		message : "floor() function must have one argument exactly"
	},
	ceilingInvalidArgumentsNumber : {
		name : "ceiling() : Invalid number of arguments",
		message : "ceiling() function must have one argument exactly"
	},
	roundInvalidArgumentsNumber : {
		name : "round() : Invalid number of arguments",
		message : "round() function must have one argument exactly"
	},
	powerInvalidArgumentsNumber : {
		name : "power() : Invalid number of arguments",
		message : "power() function must have one argument exactly"
	},
	randomInvalidArgumentsNumber : {
		name : "random() : Invalid number of arguments",
		message : "random() function must have no argument"
	},
	booleanFromStringInvalidArgumentsNumber : {
		name : "boolean-from-string() : Invalid number of arguments",
		message : "boolean-from-string() function must have one argument exactly"
	},
	ifInvalidArgumentsNumber : {
		name : "if() : Invalid number of arguments",
		message : "if() function must have three argument exactly"
	},
	chooseInvalidArgumentsNumber : {
		name : "choose() : Invalid number of arguments",
		message : "choose() function must have three argument exactly"
	},
	avgInvalidArgumentsNumber : {
		name : "avg() : Invalid number of arguments",
		message : "avg() function must have one argument exactly"
	},
	avgInvalidArgumentType : {
		name : "avg() : Invalid type of argument",
		message : "avg() function must have a nodeset argument"
	},
	minInvalidArgumentsNumber : {
		name : "min() : Invalid number of arguments",
		message : "min() function must have one argument exactly"
	},
	minInvalidArgumentType : {
		name : "min() : Invalid type of argument",
		message : "min() function must have a nodeset argument"
	},
	maxInvalidArgumentsNumber : {
		name : "max() : Invalid number of arguments",
		message : "max() function must have one argument exactly"
	},
	maxInvalidArgumentType : {
		name : "max() : Invalid type of argument",
		message : "max() function must have a nodeset argument"
	},
	serializeInvalidArgumentType : {
		name : "serialize() : Invalid type of argument",
		message : "serialize() function must have a nodeset argument"
	},
	countNonEmptyInvalidArgumentsNumber : {
		name : "count-non-empty() : Invalid number of arguments",
		message : "count-non-empty() function must have one argument exactly"
	},
	countNonEmptyInvalidArgumentType : {
		name : "count-non-empty() : Invalid type of argument",
		message : "count-non-empty() function must have a nodeset argument"
	},
	indexInvalidArgumentsNumber : {
		name : "index() : Invalid number of arguments",
		message : "index() function must have one argument exactly"
	},
	nodeIndexInvalidArgumentsNumber : {
		name : "nodeIndex() : Invalid number of arguments",
		message : "nodeIndex() function must have one argument exactly"
	},
	propertyInvalidArgumentsNumber : {
		name : "property() : Invalid number of arguments",
		message : "property() function must have one argument exactly"
	},
	propertyInvalidArgument : {
		name : "property() : Invalid argument",
		message : "Invalid property name"
	},
	instanceInvalidArgumentsNumber : {
		name : "instance() : Invalid number of arguments",
		message : "instance() function must have zero or one argument"
	},
	nowInvalidArgumentsNumber : {
		name : "now() : Invalid number of arguments",
		message : "now() function must have no argument"
	},
	localDateInvalidArgumentsNumber : {
		name : "local-date() : Invalid number of arguments",
		message : "local-date() function must have no argument"
	},
	localDateTimeInvalidArgumentsNumber : {
		name : "local-dateTime() : Invalid number of arguments",
		message : "local-dateTime() function must have no argument"
	},
	daysFromDateInvalidArgumentsNumber : {
		name : "days-from-date() : Invalid number of arguments",
		message : "days-from-date() function must have one argument exactly"
	},
	daysToDateInvalidArgumentsNumber : {
		name : "days-to-date() : Invalid number of arguments",
		message : "days-to-date() function must have one argument exactly"
	},
	secondsToDateTimeInvalidArgumentsNumber : {
		name : "seconds-to-dateTime() : Invalid number of arguments",
		message : "seconds-to-dateTime() function must have one argument exactly"
	},
	secondsFromDateTimeInvalidArgumentsNumber : {
		name : "seconds-from-dateTime() : Invalid number of arguments",
		message : "seconds-from-dateTime() function must have one argument exactly"
	},
	currentInvalidArgumentsNumber : {
		name : "current() : Invalid number of arguments",
		message : "current() function must have no argument"
	},
	isValidInvalidArgumentsNumber : {
		name : "is-valid() : Invalid number of arguments",
		message : "is-valid() function must have one argument exactly"
	},
	isValidInvalidArgumentType : {
		name : "is-valid() : Invalid type of argument",
		message : "is-valid() function must have a nodeset argument"
	},
	isNonEmptyArrayArgumentsNumber : {
		name : "is-non-empty-array() : Invalid number of arguments",
		message : "is-non-empty-array() function must have zero or one argument"
	},
	isNonEmptyArrayInvalidArgumentType : {
		name : "is-non-empty-array() : Invalid type of argument",
		message : "is-non-empty-array() function must have a node argument"
	},
	isCardNumberInvalidArgumentsNumber : {
		name : "is-card-number() : Invalid number of arguments",
		message : "is-card-number() function must have one argument exactly"
	},
	upperCaseInvalidArgumentsNumber : {
		name : "upper-case() : Invalid number of arguments",
		message : "upper-case() function must have one argument exactly"
	},
	lowerCaseInvalidArgumentsNumber : {
		name : "lower-case() : Invalid number of arguments",
		message : "lower-case() function must have one argument exactly"
	},
	distinctValuesInvalidArgumentsNumber : {
		name : "distinct-values() : Invalid number of arguments",
		message : "distinct-values() function must have one argument exactly"
	},
	transformInvalidArgumentsNumber : {
		name : "transform() : Invalid number of arguments",
		message : "transform() function must have two arguments exactly"
	},
	serializeNoContext : {
		name : "serialize() : no context node",
		message : "serialize() function must have a node argument"
	},
	serializeInvalidArgumentsNumber : {
		name : "serialize() : Invalid number of arguments",
		message : "serialize() function must have one argument exactly"
	},
	eventInvalidArgumentsNumber : {
		name : "event() : Invalid number of arguments",
		message : "event() function must have one argument exactly"
	},
	alertInvalidArgumentsNumber : {
		name : "alert() : Invalid number of arguments",
		message : "alert() function must have one argument exactly"
	},
	jsevalInvalidArgumentsNumber : {
		name : "js-eval() : Invalid number of arguments",
		message : "js-eval() function must have one argument exactly"
	},
	stringJoinInvalidArgumentsNumber : {
		name : "string-join() : Invalid number of arguments",
		message : "string-join() function must have one or two arguments"
	},
	stringJoinInvalidArgumentType : {
		name : "string-join() : Invalid type of argument",
		message : "string-join() function must have a nodeset argument"
	}
};

XsltForms_globals.validate_ = function (node) {
	if (XsltForms_browser.getBoolMeta(node, "notvalid")) {
		return false;
	}
	var atts = node.attributes || [];
	for (var i = 0, len = atts.length; i < len; i++) {
		if (atts[i].nodeName.substr(0,10) !== "xsltforms_" && !XsltForms_globals.validate_(atts[i])) {
			return false;
		}
	}
	var childs = node.childNodes || [];
	for (var j = 0, len2 = childs.length; j < len2; j++) {
		if (!XsltForms_globals.validate_(childs[j])) {
			return false;
		}
	}
	return true;
};

	
	
	