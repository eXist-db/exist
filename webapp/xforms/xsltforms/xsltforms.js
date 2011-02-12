/* Rev. 480

Copyright (C) 2008-2011 <agenceXML> - Alain COUTHURES
Contact at : <info@agencexml.com>

Copyright (C) 2006 AJAXForms S.L.
Contact at: <info@ajaxforms.com>

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
		
		
		
		// JSLint declarations:
		/*global Core, DebugConsole, Event, ExprContext, I8N */
		/*global NodeType, XFElement, XFRepeat, XMLEvents, XNode, XPath, xforms */
		/*global Calendar, Schema, Dialog, XDocument, XFProcessor */
		/*global ActiveXObject, NumberList, XPathAxis, XPathCoreFunctions, */
		/*global escape */
		
		

var Core = {
    fileName : "jsCore.js",

		

    isOpera : navigator.userAgent.match(/\bOpera\b/) != null,
    isIE : navigator.userAgent.match(/\bMSIE\b/) != null
           && navigator.userAgent.match(/\bOpera\b/) == null,
		isIE6 : navigator.userAgent.match(/\bMSIE 6.0/) != null,
    isMozilla : navigator.userAgent.match(/\bGecko\b/) != null,
		isFF2 : navigator.userAgent.match(/\bFirefox[\/\s]2.\b/) != null,
		isXhtml : document.documentElement.namespaceURI == "http://www.w3.org/1999/xhtml-false",
    setClass : function(element, className, value) {
        assert(element && className);

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

    	return inArray(className, (cn && cn.split(" ")) || []);
    },
    initHover : function(element) {
        XSLTFormsEvent.attach(element, "mouseover", function(event) {
            Core.setClass(XSLTFormsEvent.getTarget(event), "hover", true);
        } );

        XSLTFormsEvent.attach(element, "mouseout", function(event) {
            Core.setClass(XSLTFormsEvent.getTarget(event), "hover", false);
        } );
    },
    getEventPos : function(ev) {
        ev = ev || window.event;
		return { x : ev.pageX || ev.clientX + window.document.body.scrollLeft || 0,
		         y : ev.pageY || ev.clientY + window.document.body.scrollTop || 0 };
    },
    getAbsolutePos : function(e) {
        var r = Core.getPos(e);

        if (e.offsetParent) {
            var tmp = Core.getAbsolutePos(e.offsetParent);
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
            var tmp = Core.getAbsolutePos(element.offsetParent);
            left -= tmp.x;
            top -= tmp.y;
        }

        element.style.top = top + "px";
        element.style.left = left + "px";
    },

		

    loadProperties : function(name) {
			if (!this.ROOT) {
				var scripts = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "script") : document.getElementsByTagName("script");
				for (var i = 0, len = scripts.length; i < len; i++) {
					var src = scripts[i].src;
					if (src.indexOf(Core.fileName) != -1) {
						this.ROOT = src.replace(Core.fileName, "");
						break;
					}
				}
			}
			var uri = this.ROOT + name;
			var req = Core.openRequest("GET", uri, false);
			if (req.overrideMimeType) {
				req.overrideMimeType("application/xml");
			}
			try {        
				req.send(null);
			} catch(e) {
				alert("File not found: " + uri);
			}

			if (req.status == 200) {
				Core.loadNode(Core.config, Core.selectSingleNode('//properties', req.responseXML));
				var inst = document.getElementById("xf-instance-config").xfElement
				Core.config = inst.doc.documentElement;
				inst.srcXML = Core.saveXML(Core.config);
				Core.setMeta(Core.config, "instance", "xf-instance-config");
				Core.setMeta(Core.config, "model", "xf-model-config");
				//XMLEvents.dispatch(properties.model, "xforms-rebuild");
				//xforms.refresh();
			}
    },

		

    constructURI : function(uri) {
			if (uri.match(/^[a-zA-Z0-9+.-]+:\/\//)) {
				return uri;
			}
			if (uri.charAt(0) == '/') {
				return document.location.href.substr(0, document.location.href.replace(/:\/\//, ":\\\\").indexOf("/")) + uri;
			}
			var href = document.location.href;
			var idx = href.indexOf("?");
			href =  idx == -1 ? href : href.substr(0, idx);
			idx = href.replace(/:\/\//, ":\\\\").lastIndexOf("/");
			if (href.length > idx + 1) {
				return (idx == -1 ? href : href.substr(0, idx)) + "/" + uri;
			}
			return href + uri;
    },

		

    createElement : function(type, parent, content, className) {
				var el = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", type) : document.createElement(type);
        if (className) { el.className = className; }
        if (parent) { parent.appendChild(el); }
        if (content) { el.appendChild(document.createTextNode(content)); }
        return el;
    },

		

    getWindowSize : function() {
				var myWidth = 0, myHeight = 0, myOffsetX = 0, myOffsetY = 0, myScrollX = 0, myScrollY = 0;
				if( typeof( window.innerWidth ) == 'number' ) {
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

		

if (window.XMLHttpRequest) {
	Core.openRequest = function(method, uri, async) {
		// netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
		var req = new XMLHttpRequest();
		try {
			req.open(method, Core.constructURI(uri), async);
		} catch (e) {
			try {
				req = new ActiveXObject("Msxml2.XMLHTTP.3.0"); 
			} catch (e) {
				try {
					req = new ActiveXObject("Msxml2.XMLHTTP");
				} catch (e) {
					throw new Error("This browser does not support XHRs(Ajax)! \n Cause: " + (e.message || e.description || e) + " \n Enable Javascript or ActiveX controls (on IE) or lower security restrictions.");
				}
			}
			req.open(method, Core.constructURI(uri), async);
		}
		if (Core.isMozilla) {
			req.overrideMimeType("text/xml");
		}
		return req;
	};
} else if (window.ActiveXObject) {
	Core.openRequest = function(method, uri, async) {
		try {
			req = new ActiveXObject("Msxml2.XMLHTTP.3.0"); 
		} catch (e) {
			try {
				req = new ActiveXObject("Msxml2.XMLHTTP");
			} catch (e) {
				throw new Error("This browser does not support XHRs(Ajax)! \n Cause: " + (e.message || e.description || e) + " \n Enable Javascript or ActiveX controls (on IE) or lower security restrictions.");
			}
		}
		req.open(method, Core.constructURI(uri), async);
		return req;
	};
} else {
	throw new Error("This browser does not support XHRs(Ajax)! \n Enable Javascript or ActiveX controls (on IE) or lower security restrictions.");
}

		

if (Core.isIE) {
    Core.transformText = function(xml, xslt, inline) {
			var xmlDoc = new ActiveXObject("MSXML2.DOMDocument.6.0");
			xmlDoc.loadXML(xml);
			var xslDoc = new ActiveXObject("MSXML2.FreeThreadedDOMDocument.6.0");
			if (inline) {
				xslDoc.loadXML(xml);
			} else {
				xslDoc.async = false;
				xslDoc.load(xslt);
			}
			var xslTemplate = new ActiveXObject("MSXML2.XSLTemplate.6.0");
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
    Core.transformText = function(xml, xslt, inline) {
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
			if (!Core.isMozilla) {
				xsltProcessor.setParameter(null, "xsltforms_caller", "true");
			}
			xsltProcessor.setParameter(null, "xsltforms_config", document.getElementById("xf-instance-config").xfElement.srcXML);
			xsltProcessor.setParameter(null, "xsltforms_debug", DebugMode+"");
			xsltProcessor.setParameter(null, "xsltforms_lang", Language);
			xsltProcessor.importStylesheet(xsltDoc);
			var resultDocument = xsltProcessor.transformToDocument(xmlDoc);
			return serializer.serializeToString(resultDocument);
    };
}

Core.xsltsrc =
 '<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">'
+'	<xsl:output method="xml" omit-xml-declaration="yes"/>'
+'	<xsl:template match="@*[starts-with(name(),\'xsltforms_\')]" priority="1"/>'
+'	<xsl:template match="@*|node()" priority="0">'
+'	  <xsl:copy>'
+'	    <xsl:apply-templates select="@*|node()"/>'
+'	  </xsl:copy>'
+'	</xsl:template>'
+'</xsl:stylesheet>'
;
if (Core.isIE) {
	Core.createXMLDocument = function(xml) {
		var d = new ActiveXObject("MSXML2.DOMDocument.3.0");
		d.setProperty("SelectionLanguage", "XPath"); 
		d.loadXML(xml);
		return d;
	}
	Core.setAttributeNS = function(node, ns, name, value) {
		node.setAttributeNode(node.ownerDocument.createNode(NodeType.ATTRIBUTE, name, ns));
		node.setAttribute(name, value);
	}
	Core.selectSingleNode = function(xpath, node) {
		try {
			return node.selectSingleNode(xpath);
		} catch (e) {
			return null;
		}
	}
	Core.selectSingleNodeText = function(xpath, node) {
		try {
			return node.selectSingleNode(xpath).text;
		} catch (e) {
			return "";
		}
	}
	Core.selectNodesLength = function(xpath, node) {
		try {
			return node.selectNodes(xpath).length;
		} catch (e) {
			return 0;
		}
	}
	Core.xsltDoc = new ActiveXObject("MSXML2.DOMDocument.3.0");
	Core.xsltDoc.loadXML(Core.xsltsrc);
	Core.loadNode = function(dest, src) {
		var r = src.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	}
	Core.loadXML = function(dest, xml) {
		var result = new ActiveXObject("MSXML2.DOMDocument.3.0");
		result.setProperty("SelectionLanguage", "XPath"); 
		result.loadXML(xml);
		var r = result.documentElement.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	}
	Core.saveXML = function(node) {
		var xmlDoc = new ActiveXObject("MSXML2.DOMDocument.3.0");
		xmlDoc.setProperty("SelectionLanguage", "XPath"); 
		xmlDoc.appendChild(node.documentElement ? node.documentElement.cloneNode(true) : node.cloneNode(true));
		return xmlDoc.transformNode(Core.xsltDoc);
	}
} else {
	Core.createXMLDocument = function(xml) {
		return Core.parser.parseFromString(xml, "text/xml");
	}
	Core.setAttributeNS = function(node, ns, name, value) {
		node.setAttributeNS(ns, name, value);
	}
	Core.selectSingleNode = function(xpath, node) {
		try {
			if (node.evaluate) {
				return node.evaluate(xpath, node, null, XPathResult.ANY_TYPE, null).iterateNext();
			} else {
				return node.ownerDocument.evaluate(xpath, node, null, XPathResult.ANY_TYPE, null).iterateNext();
			}
		} catch (e) {
			return null;
		}
	}
	Core.selectSingleNodeText = function(xpath, node) {
		try {
			if (node.evaluate) {
				return node.evaluate(xpath, node, null, XPathResult.ANY_TYPE, null).iterateNext().textContent;
			} else {
				return node.ownerDocument.evaluate(xpath, node, null, XPathResult.ANY_TYPE, null).iterateNext().textContent;
			}
		} catch (e) {
			return "";
		}
	}
	Core.selectNodesLength = function(xpath, node) {
		try {
			if (node.evaluate) {
				return node.evaluate(xpath, node, null, XPathResult.UNORDERED_NODE_SNAPSHOT_TYPE, null).snapshotLength;
			} else {
				return node.ownerDocument.evaluate(xpath, node, null, XPathResult.UNORDERED_NODE_SNAPSHOT_TYPE, null).snapshotLength;
			}
		} catch (e) {
			return 0;
		}
	}
	Core.parser = new DOMParser();
	Core.xsltDoc = Core.parser.parseFromString(Core.xsltsrc, "text/xml");
	Core.xsltProcessor = new XSLTProcessor();
	Core.xsltProcessor.importStylesheet(Core.xsltDoc);
	Core.serializer = new XMLSerializer();
	Core.loadNode = function(dest, src) {
		var r = src.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	}
	Core.loadXML = function(dest, xml) {
		var result = Core.parser.parseFromString(xml, "text/xml");
		var r = result.documentElement.cloneNode(true);
		dest.parentNode.replaceChild(r, dest);
	}
	Core.saveXML = function(node) {
		var resultDocument = Core.xsltProcessor.transformToDocument(node);
		return Core.serializer.serializeToString(resultDocument);
	}
}
Core.unescape = function(xml) {
	if (xml == null) {
		return "";
	}
	var regex_escapepb = /^\s*</;
	if (!xml.match(regex_escapepb)) {
		xml = xml.replace(/&lt;/g, "<");
		xml = xml.replace(/&gt;/g, ">");
		xml = xml.replace(/&amp;/g, "&");
	}
	return xml;
}
Core.escape = function(text) {
	if (text == null) {
		return "";
	}
	if (typeof(text) == "string") {
		text = text.replace(/&/g, "&amp;");
		text = text.replace(/</g, "&lt;");
		text = text.replace(/>/g, "&gt;");
	}
	return text;
}

Core.getMeta = function(node, meta) {
	return node.nodeType == NodeType.ELEMENT ? node.getAttribute("xsltforms_"+meta) : node.ownerElement ? node.ownerElement.getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) : node.selectSingleNode("..").getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);	
}

Core.getBoolMeta = function(node, meta) {
	return Boolean(node.nodeType == NodeType.ELEMENT ? node.getAttribute("xsltforms_"+meta) : node.nodeType == NodeType.ATTRIBUTE ? node.ownerElement ? node.ownerElement.getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) :  node.selectSingleNode("..").getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) : false);
}

Core.getType = function(node) {
	if (node.nodeType == NodeType.ELEMENT) {
		var t = node.getAttribute("xsltforms_type");
		if (t && t != '') {
			return t;
		}
		if (node.getAttributeNS) {
			return node.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type");
		} else {
			var att = node.selectSingleNode("@*[local-name()='type' and namespace-uri()='http://www.w3.org/2001/XMLSchema-instance']");
			if (att && att.value != '') {
				return att.value;
			} else {
				return null;
			}
		}
	} else {
		if (node.ownerElement) {
			return node.ownerElement.getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type");
		} else {
			return node.selectSingleNode("..").getAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type");
		}
	}
}

Core.setMeta = function(node, meta, value) {
	if (node) {
		node.nodeType == NodeType.ELEMENT ? node.setAttribute("xsltforms_"+meta, value) : node.ownerElement ? node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value) : node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value);
	}
}

Core.setBoolMeta = function(node, meta, value) {
	if (node) {
		if (value) {
			node.nodeType == NodeType.ELEMENT ? node.setAttribute("xsltforms_"+meta, value) : node.ownerElement ? node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value) : node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, value);
		} else {
			node.nodeType == NodeType.ELEMENT ? node.removeAttribute("xsltforms_"+meta) : node.ownerElement ? node.ownerElement.removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) : node.selectSingleNode("..").removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
		}
	}
}

Core.setTrueBoolMeta = function(node, meta) {
	if (node) {
		node.nodeType == NodeType.ELEMENT ? node.setAttribute("xsltforms_"+meta, true) : node.ownerElement ? node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, true) : node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta, true);
	}
}

Core.setFalseBoolMeta = function(node, meta) {
	if (node) {
		node.nodeType == NodeType.ELEMENT ? node.removeAttribute("xsltforms_"+meta) : node.ownerElement ? node.ownerElement.removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta) : node.selectSingleNode("..").removeAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_"+meta);
	}
}

Core.setType = function(node, value) {
	if (node) {
		if (node.nodeType == NodeType.ELEMENT) {
			node.setAttribute("xsltforms_type", value);
		} else {
			if (node.ownerElement) {
				node.ownerElement.setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type", value);
			} else {
				node.selectSingleNode("..").setAttribute("xsltforms_"+(node.localName ? node.localName : node.baseName)+"_type", value);
			}
		}
	}
}

if (!Core.isIE) {
	if (typeof XMLDocument == "undefined") {
		XMLDocument = Document;
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
	}
	XMLDocument.prototype.selectSingleNode = function(path) {
		return this.selectNodes(path, true);
	}
	XMLDocument.prototype.createNode = function(t, name, ns) {
		switch(t) {
			case NodeType.ELEMENT:
				return this.createElementNS(ns, name);
				break;
			case NodeType.ATTRIBUTE:
				return this.createAttributeNS(ns, name);
				break;
			default:
				return null;
		}
	}
	Element.prototype.selectNodes = function(path) {
		return this.ownerDocument.selectNodes(path, false, this);
	}
	Element.prototype.selectSingleNode = function(path) {	
		return this.ownerDocument.selectNodes(path, true, this);
	}
}

		

var DebugConsole = {
    element_ : null,
    isInit_ : false,
    time_ : 0,
    init_ : function() {
        this.element_ = document.getElementById("console");
        this.isInit_ = true;
        this.time_ = new Date().getTime();
    },

		

    write : function(text) {
			try {
        if (this.isOpen()) {
            var time = new Date().getTime();
            this.element_.appendChild(document.createTextNode(time - this.time_ + " -> " + text));
            Core.createElement("br", this.element_);
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
        
        return this.element_ != null;
    }
};


		

var Dialog = {
    openPosition: {},
    dialogs : [],
    init : false,
		initzindex : 50,
		zindex: 0,
		selectstack : [],

		

	dialogDiv : function(id) {
		var div = null;
		if (typeof id != "string") {
			var divid = id.getAttribute("id");
			if (divid != null && divid != "") {
				div = IdManager.find(divid);
			} else {
				div = id;
			}
		} else {
			div = IdManager.find(id);
		}
		if (!div) {
			DebugConsole.write("Unknown dialog("+id+")!");
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
			this.dialogs = removeArrayItem(this.dialogs, div);
			this.dialogs.push(div);
			
			if (modal) {
				var surround = document.getElementById('xforms-dialog-surround');
				surround.style.display = "block";
				surround.style.zIndex = (this.zindex + this.initzindex)*2;
				this.zindex++;
				var size = Core.getWindowSize();
				surround.style.height = size.height+"px";
				surround.style.width = size.width+"px";
				surround.style.top = size.scrollY+"px";
				surround.style.left = size.scrollX+"px";
				var surroundresize = function () {
					var surround = document.getElementById('xforms-dialog-surround');
					var size = Core.getWindowSize();
					surround.style.height = size.height+"px";
					surround.style.width = size.width+"px";
					surround.style.top = size.scrollY+"px";
					surround.style.left = size.scrollX+"px";
				}
				window.onscroll = surroundresize;
				window.onresize = surroundresize;
			}
			
			div.style.display = "block";
			div.style.zIndex = (this.zindex + this.initzindex)*2-1;
			this.showSelects(div, false, modal);
			
			if (parent) {
				var absPos = Core.getAbsolutePos(parent);
				Core.setPos(div, absPos.x, (absPos.y + parent.offsetHeight));
			} else {
				var size = Core.getWindowSize();
				var h = size.scrollY + (size.height - div.offsetHeight) / 2;
				Core.setPos(div, (size.width - div.offsetWidth) / 2, h > 0 ? h : 100);
			}
		},

		

    hide : function(div, modal) {
			if (!(div = this.dialogDiv(div))) {
				return;
			}
			
			var oldlen = this.dialogs.length;
			this.dialogs = removeArrayItem(this.dialogs, div);
			if (this.dialogs.length == oldlen) {
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
			if (Core.isIE6) {
				for (var i = 0, len = this.zindex; i < len; i++) {
					for (var j = 0, len1 = this.selectstack[i].length; j < len1; j++) {
						if (this.selectstack[i][j].select == s) {
							return true;
						}
					}
				}
			}
			return false;
		},

		

    showSelects : function(div, value, modal) {
			if (Core.isIE6) {
				var selects = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "select") : document.getElementsByTagName("select");
				var pos = Core.getAbsolutePos(div);
				var w = div.offsetWidth;
				var h = div.offsetHeight;
				var dis = [];
				for (var i = 0, len = selects.length; i < len; i++) {
					var s = selects[i];
					var p = s.parentNode;
					
					while (p && p != div) {
						p = p.parentNode;
					}

					if (p != div) {
						var ps = Core.getAbsolutePos(s);
						var ws = s.offsetWidth;
						var hs = s.offsetHeight;
						var under = ps.x + ws > pos.x && ps.x < pos.x + w && ps.y + hs > pos.y && ps.y < pos.y + h;
						if (modal) {
							if (value) {
								dis = this.selectstack[this.zindex];
								for (var j = 0, len1 = dis.length; j < len1; j++) {
									if (dis[j].select == s) {
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


		

var XSLTFormsEvent = {
    cache :null,
    add_ : function() {
        if (!XSLTFormsEvent.cache) {
            XSLTFormsEvent.cache = [];
            XSLTFormsEvent.attach(window, "unload", XSLTFormsEvent.flush_);
        }

        XSLTFormsEvent.cache.push(arguments);
    },
    flush_ : function() {
				if (!XSLTFormsEvent.cache) return;
        for (var i = XSLTFormsEvent.cache.length - 1; i >= 0; i--) {
            var item = XSLTFormsEvent.cache[i];
            XSLTFormsEvent.detach(item[0], item[1], item[2], item[3]);
        }
        
        if (XSLTFormsEvent.onunload) {
            XSLTFormsEvent.onunload();
        }
        
        XSLTFormsEvent.onunload = null;
    },
    onunload : null
};

if (Core.isIE) {
    XSLTFormsEvent.attach = function(target, name, handler, phase) {
    	var func = function(evt) { handler.call(window.event.srcElement, evt); };
        target.attachEvent("on" + name, func);
        this.add_(target, name, func, phase);
    };

    XSLTFormsEvent.detach = function(target, name, handler, phase) {
        target.detachEvent("on" + name, handler);
    };

    XSLTFormsEvent.getTarget = function() {
        return window.event.srcElement;
    };
    
    XSLTFormsEvent.dispatch = function(target, name) {
        target.fireEvent("on" + name, document.createEventObject());
    };
} else {
    XSLTFormsEvent.attach = function(target, name, handler, phase) {
        if (target == window && !window.addEventListener) {
            target = document;
        }

        target.addEventListener(name, handler, phase);
        this.add_(target, name, handler, phase);
    };
    
    XSLTFormsEvent.detach = function(target, name, handler, phase) {
        if (target == window && !window.addEventListener) {
            target = document;
        }

        target.removeEventListener(name, handler, phase);
    };

    XSLTFormsEvent.getTarget = function(ev) {
        return ev.target;
    };
    
    XSLTFormsEvent.dispatch = function(target, name) {
        var event = document.createEvent("Event");
        event.initEvent(name, true, true);
        target.dispatchEvent(event);
    };
}


		

var I8N = {
    messages : null,
    lang : null,
    langs : ["cz", "de", "el", "en", "en_us", "es", "fr" , "gl", "it", "ja", "nb_no", "nl", "nn_no", "pt", "ro", "ru", "si", "sk"],

		

    get : function(key) {
			if (!Core.config) {
				return "Initializing";
			}
			if (Language == "navigator" || Language != Core.selectSingleNodeText('language', Core.config)) {
				var lan = Language == "navigator" ? (navigator.language || navigator.userLanguage) : Core.selectSingleNodeText('language', Core.config);
				lan = lan.replace("-", "_").toLowerCase();
				var finded = inArray(lan, I8N.langs);
				if (!finded) {
					ind = lan.indexOf("_");
					if (ind != -1) {
							lan = lan.substring(0, ind);
					}
					finded = inArray(lan, I8N.langs);
				}
				if (finded) {
					Core.loadProperties("config_" + lan + ".xsl");
					Language = Core.selectSingleNodeText('language', Core.config);
				} else {
					Language = "default";
				}
			}
			return Core.selectSingleNodeText(key, Core.config);
    },

		

    parse : function(str, pattern) {
        if (str == null || str.match("^\\s*$")) {
            return null;
        }

        if (!pattern) { pattern = I8N.get("format.datetime"); }
        var d = new Date();
        I8N._parse(d, "Year", str, pattern, "yyyy");
        I8N._parse(d, "Month", str, pattern, "MM");
        I8N._parse(d, "Date", str, pattern, "dd");
        I8N._parse(d, "Hours", str, pattern, "hh");
        I8N._parse(d, "Minutes", str, pattern, "mm");
        I8N._parse(d, "Seconds", str, pattern, "ss");

        return d;
    },

		

    format : function(date, pattern, loc) {
        if (!date) {
            return "";
        }

        if (!pattern) { pattern = I8N.get("format.datetime"); }

        var str = I8N._format(pattern, (loc ? date.getDate() : date.getUTCDate()), "dd");
        str = I8N._format(str, (loc ? date.getMonth() : date.getUTCMonth()) + 1, "MM");
				y = (loc ? date.getFullYear() : date.getUTCFullYear());
        str = I8N._format(str, y < 1000? 1900 + y : y, "yyyy");
        str = I8N._format(str, (loc ? date.getSeconds() : date.getUTCSeconds()), "ss");
        str = I8N._format(str, (loc ? date.getMinutes() : date.getUTCMinutes()), "mm");
        str = I8N._format(str, (loc ? date.getHours() : date.getUTCHours()), "hh");
				o = date.getTimezoneOffset();
				str = I8N._format(str, (loc ? (o < 0 ? "+" : "-")+zeros(Math.floor(Math.abs(o)/60),2)+":"+zeros(Math.abs(o) % 60,2) : "Z"), "z");

        return str;
    },

		

    parseDate : function(str) {
        return I8N.parse(str, I8N.get("format.date"));
    },

		

    formatDate : function(str) {
        return I8N.format(str, I8N.get("format.date"), true);
    },
 
		

   formatNumber : function(number, decimals) {
    	if (isNaN(number)) { return number; }

    	var value = "" + number;
		var index = value.indexOf(".");
		var integer = parseInt(index != -1? value.substring(0, index) : value, 10);
		var decimal = index != -1? value.substring(index + 1) : "";
		var decsep = I8N.get("format.decimal");

    	return integer
    		+ (decimals > 0? decsep + zeros(decimal, decimals, true) 
    		: (decimal? decsep + decimal : ""));
    },

		

    parseNumber : function(value) {
		var decsep = I8N.get("format.decimal");

		if(!value.match("^[\\-+]?([0-9]+(\\" + decsep + "[0-9]*)?|\\" + decsep + "[0-9]+)$")) {
			throw "Invalid number " + value;
		}

		var index = value.indexOf(decsep);
		var integer = parseInt(index != -1? value.substring(0, index) : value, 10);
		var decimal = index != -1? value.substring(index + 1) : null;
		
		return integer + (decimal? "." + decimal : "");
    },
    _format : function(returnValue, value, el) {
        return returnValue.replace(el, zeros(value, el.length));
    },
    _parse : function(date, prop, str, format, el) {
        var index = format.indexOf(el);
        
        if (index != -1) {
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
            
            if (val.charAt(0) === '0') val = val.substring(1);
            
            val = parseInt(val, 10);
        
            if (isNaN(val)) {
                throw "Error parsing date " + str + " with pattern " + format;
            }

						var n = new Date();
						n = n.getFullYear() - 2000;
            date["set" + prop](prop == "Month"? val - 1 : (prop == "Year" && val <= n+10 ? val+2000 : val));
        }
    }
};

var NumberList = function(parent, className, input, min, max, minlengh) {
    this.element = Core.createElement("ul", parent, null, className);
    this.move = 0;
    this.input = input;
    this.min = min;
    this.max = max;
    this.minlength = minlengh || 1;
    var list = this;

    this.createChild("+", function() { list.start(1); }, function() { list.stop(); } );

    for (var i = 0; i < 7; i++) {
        this.createChild(" ", function(event) {
            list.input.value = XSLTFormsEvent.getTarget(event).childNodes[0].nodeValue;
            list.close();
            XSLTFormsEvent.dispatch(list.input, "change");
        } );
    }
    
    this.createChild("-", function() { list.start(-1); }, function() { list.stop(); } );
};

NumberList.prototype.show = function() {
	var input = this.input;
    this.current = parseInt(input.value, 10);
    this.refresh();
    Dialog.show(this.element, input, false);
};

NumberList.prototype.close = function() {
    Dialog.hide(this.element, false);
}; 

NumberList.prototype.createChild = function(content, handler, handler2) {
    var child = Core.createElement("li", this.element, content);
    Core.initHover(child);

    if (handler2) {
        XSLTFormsEvent.attach(child, "mousedown", handler);
        XSLTFormsEvent.attach(child, "mouseup", handler2);
    } else {
        XSLTFormsEvent.attach(child, "click", handler);
    }
};
    
NumberList.prototype.refresh = function()  {
    var childs = this.element.childNodes;
    var cur = this.current;
    
    if (cur >= this.max - 3) {
        cur = this.max - 3;
    } else if (cur <= this.min + 3) {
    	cur = this.min + 3;
    }
    
    var top = cur + 4;

    for (var i = 1; i < 8; i++) {
        Core.setClass(childs[i], "hover", false);
        var str = (top - i) + "";
        
        for (; str.length < this.minlength; str = '0' + str) {}
        
        childs[i].firstChild.nodeValue = str;
    }
};

NumberList.prototype.start = function(value) {
    this.move = value;
    NumberList.current = this;
    this.run();
};
    
NumberList.prototype.stop = function() {
    this.move = 0;
};

NumberList.prototype.run = function() {
    if (   (this.move > 0 && this.current + 3 < this.max)
        || (this.move < 0 && this.current - 3> this.min)) {
        this.current += this.move;
        this.refresh();
        var list = this;
        setTimeout("NumberList.current.run()", 60);
    }
};

NumberList.current = null;


		

function forEach(object, block) {
    var args = [];
   
    for (var i = 0, len = arguments.length - 2; i < len; i++) {
        args[i] = arguments[i + 2];
    }

    if (object) {
        if (typeof object.length == "number") {
            for (var j = 0, len1 = object.length; j < len1; j++) {
            	var obj = object[j];
            	var func = typeof block == "string"? obj[block] : block;
                func.apply(obj, args);
            }
        } else {
            for (var key in object) {
            	var obj2 = object[key];
            	var func2 = typeof block == "string"? obj2[block] : block;
                func2.apply(obj2, args);
            }   
        }
    }
}


		

function assert(condition, message) {
    if (!condition && DebugConsole.isOpen()) {
        DebugConsole.write("Assertion failed: " + message);
        var callstack = null;

        if (arguments.caller) { // Internet Explorer
            this.callstack = [];
    
            for (var caller = arguments.caller; caller != null; caller = caller.caller) {
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
                DebugConsole.write("> " + this.callstack[i]);
            }
        }

        throw message;
    }
}


		

function inArray(value, array) {
    for (var i = 0, len = array.length; i < len; i++) {
        if (value == array[i]) {
            return true;
        }
    }
    
    return false;
}


		

function zeros(value, length, right) {
	var res = "" + value;

	for (; res.length < length; res = right? res + '0' : '0' + res) {}

	return res;
}
    
	
		
		
		
function getValue(node, format, serialize) {
	assert(node);
	if (serialize) {
		return node.nodeType == NodeType.ATTRIBUTE ? node.nodeValue : Core.saveXML(node);
	}
	/*****************
	var value = node.nodeType == NodeType.ATTRIBUTE ? node.nodeValue :
		(node.firstChild != null? node.firstChild.nodeValue : "");
	var value = "";
	if (node.nodeType == NodeType.ATTRIBUTE) {
		value = node.nodeValue;
	} else if (node.firstChild == null) {
	} else if (node.firstChild.nodeType == NodeType.TEXT) {
		var childNodes = node.childNodes;
		var len = childNodes.length;
		for (var i = 0; i < len; i++) {
			value = value + childNodes[i].nodeValue;
		}
	} else {
		value = node.firstChild.nodeValue;
	}
	******************/
	var value = node.text != undefined ? node.text : node.textContent;

	if (value && format) {
		var schtyp = Schema.getType(Core.getType(node) || "xsd_:string");
		if (schtyp.format) {
			try { value = schtyp.format(value); } catch(e) { }
		}
	}

	return value;
}


		

function setValue(node, value) {
	assert(node);
	if (node.nodeType == NodeType.ATTRIBUTE) {
		node.nodeValue = value;
	} else if (Core.isIE && node.innerHTML) {
		node.innerHTML = value;
	} else {
		while (node.firstChild) {
			node.removeChild(node.firstChild);
		}
		if (value != null) {
			for (var i = 0, l = value.length; i < l; i += 4096) {
				node.appendChild(node.ownerDocument.createTextNode(value.substr(i, 4096)));
			}
		}
	}
}


		

function run(action, element, evt, synch, propagate) {
	if (synch) {
		Dialog.show("statusPanel", null, false);

		setTimeout(function() { 
			xforms.openAction();
			action.execute(IdManager.find(element), null, evt);
			Dialog.hide("statusPanel", false);
			if (!propagate) {
				evt.stopPropagation();
			}
			xforms.closeAction();
		}, 1 );
	} else {
		xforms.openAction();
		action.execute(IdManager.find(element), null, evt);
		if (!propagate) {
			evt.stopPropagation();
		}
		xforms.closeAction();
	}
}


		

function getId(element) {
	if(element.id) {
		return element.id;
	} else {
		return element.parentNode.parentNode.parentNode.parentNode.id;
	}
}


		

function show(el, type, value) {
	el.parentNode.lastChild.style.display = value? 'inline' : 'none';
}


		

String.prototype.trim = function() {
	return this.replace(/^\s+|\s+$/, '');
};


		

String.prototype.addslashes = function() {
  return this.replace(/\\/g,"\\\\").replace(/\'/g,"\\'").replace(/\"/g,"\\\"");
}


		

function copyArray(source, dest) {
	if( dest ) {
		for (var i = 0, len = source.length; i < len; i++) {
			dest[i] = source[i];
		}
	}
}

		

function removeArrayItem(array, item) {
	var narr = [];
	for (var i = 0, len = array.length; i < len; i++) {
		if (array[i] != item ) {
			narr.push(array[i]);
		}
	}
	return narr;
}

	
		
		
		
var xforms = {
	cont : 0,
	ready : false,
	body : null,
	models : [],
	defaultModel : null,
	changes : [],
	newChanges : [],
	building : false,
	posibleBlur : false,
	bindErrMsgs : [],		// binding-error messages gathered during refreshing
	loadingtime: 0,
	refreshtime: 0,
	refreshcount: 0,

		

	profiling : function() {
		var s = "XSLTForms Version Id: Rev. 480\n";
		s += "\nLoading Time: " + this.loadingtime + "ms";
		s += "\nRefresh Counter: " + this.refreshcount;
		s += "\nCumulative Refresh Time: " + this.refreshtime + "ms";
		s += "\nXPath Evaluation:";
		var exprtab = [];
		for (var expr in XPath.expressions) {
			exprtab[exprtab.length] = {expr: expr, evaltime: XPath.expressions[expr].evaltime};
		}
		exprtab.sort(function(a,b) { return b.evaltime - a.evaltime; });
		var top = 0;
		for (var i = 0; i < exprtab.length && i < 20; i++) {
			s += "\n   \"" + (exprtab[i].expr.length > 60 ? exprtab[i].expr.substring(0,60)+"..." : exprtab[i].expr) + "\": " + exprtab[i].evaltime + "ms";
			top += exprtab[i].evaltime;
		}
		if (exprtab.length > 20) {
			var others = 0;
			for (var i = 20; i < exprtab.length; i++) {
				others += exprtab[i].evaltime;
			}
			s += "\n   Others (" + (exprtab.length - 20) + " expr.): " + others + "ms";
			top += others;
			s += "\n   Total: " + top + "ms";
		}
		s += "\n\n(c) Alain Couthures - agenceXML - 2011   http://www.agencexml.com/xsltforms";
		s += "\nPlease donate at http://sourceforge.net/projects/xsltforms/";
		alert(s);
	},

		

	init : function() {
	   setValue(document.getElementById("statusPanel"), I8N.get("status"));

		var b = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
		this.body = b;

		document.onhelp = new Function("return false;");
		window.onhelp = new Function("return false;");
		XSLTFormsEvent.attach(document, "keydown", function(evt) {
			if (evt.keyCode == 112) {
				xforms.profiling();
				evt.stopPropagation();
				evt.preventDefault();
				return false;
			}
		});
		XSLTFormsEvent.attach(b, "click", function(evt) {
			var target = XSLTFormsEvent.getTarget(evt);
			var parent = target;
			
			while (parent && parent.nodeType == NodeType.ELEMENT) {
				if (Core.hasClass(parent, "xforms-repeat-item")) {
					XFRepeat.selectItem(parent);
				}
				parent = parent.parentNode;
			}
			
			parent = target;
			while (parent && parent.nodeType == NodeType.ELEMENT) {
				var xf = parent.xfElement;

				if (xf) {
					if(typeof parent.node != "undefined" && parent.node != null && xf.focus && !Core.getBoolMeta(parent.node, "readonly")) {
						var name = target.nodeName.toLowerCase();
						xf.focus(name == "input" || name == "textarea");
					}
					if(xf.click) {
						xf.click(target);
						break;
					}
				}

				parent = parent.parentNode;
			}
		} );

		XSLTFormsEvent.onunload = function() {
			xforms.close();
		};

		this.openAction();
		XMLEvents.dispatchList(this.models, "xforms-model-construct");
		XMLEvents.dispatchList(this.models, "xforms-ready");
		this.refresh();
		this.closeAction();
		this.ready = true;
		Dialog.hide("statusPanel", false);
	},

		

	close : function() {
		if (xforms.body) {
			xforms.openAction();
			XMLEvents.dispatchList(xforms.models, "xforms-model-destruct");
			xforms.closeAction();
			IdManager.clear();
			xforms.defaultModel = null;
			xforms.changes = [];
			xforms.models = [];
			xforms.body = null;
			xforms.cont = 0;
			xforms.dispose(document.documentElement);
			//XSLTFormsEvent.flush_();
	    if (XSLTFormsEvent.cache) 

	        for (var i = XSLTFormsEvent.cache.length - 1; i >= 0; i--) {
	            var item = XSLTFormsEvent.cache[i];
	            XSLTFormsEvent.detach(item[0], item[1], item[2], item[3]);
	        }

			Schema.all = {};
			TypeDefs.initAll();
			xforms.ready = false;
			xforms.building = false;
			xforms.posibleBlur = false;
		}
	},

		

	openAction : function() {
		if (this.cont++ == 0) {
			DebugConsole.clear();
		}
	},

		

	closeAction : function() {
		if (this.cont == 1) {
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
					XMLEvents.dispatch(change, "xforms-rebuild");
				} else {
					XMLEvents.dispatch(change, "xforms-recalculate");
				}
			} else { // Repeat or tree
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
		Dialog.hide("statusPanel", false);
		
		setValue(document.getElementById("statusPanel"), message);
		Dialog.show("statusPanel", null, false);

		if (element != null) {
			XMLEvents.dispatch(element, event);
		}
		
		if (causeMessage) {
			message += " : " + causeMessage;
		}

		DebugConsole.write("Error: " + message);
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
			this.error(this.defaultModel, "xforms-binding-exception",	"Binding Errors: \n" + this.bindErrMsgs.join("\n  "));
			this.bindErrMsgs = [];
		}
		
		var d2 = new Date();
		this.refreshtime += d2 - d1;
		this.refreshcount++;
	},

		

	build : function(element, ctx, selected) {
		if (   element.nodeType != NodeType.ELEMENT
			|| element.id == "console" || element.hasXFElement == false) { return; }
		var xf = element.xfElement;
		var hasXFElement = !!xf;
		
		if (element.getAttribute("mixedrepeat") == "true") {
			ctx = element.node || ctx;
			selected = element.selected;
		}

		//if (!ctx) { alert("xforms.build " + element.id + " no ctx"); }
		
		if (xf) {
			xf.build(ctx);

			if (xf.isRepeat) {
				xf.refresh(selected);
			}
		}

   		ctx = element.node || ctx;
		var childs = element.childNodes;
		var sel = element.selected;

		if (typeof sel != "undefined") {
			selected = sel;
		}

		if (!xf || !xf.isRepeat || xf.nodes.length > 0) {
			for (var i = 0; i < childs.length && this.building; i++) {
				hasXFElement = (childs[i].nodeType == NodeType.ELEMENT && !childs[i].getAttribute("cloned") ? this.build(childs[i], ctx, selected) : false) || hasXFElement;
			}
		}
		
		if(this.building) {
			if (xf && xf.changed) {
				xf.refresh(selected);
				xf.changed = false;
			}
			
			if (element.hasXFElement == null) {
				element.hasXFElement = hasXFElement;
			}
		}

		return hasXFElement;
	},

		

	addChange : function(element) {
		var list = this.building? this.newChanges : this.changes;

		if (!inArray(element, list)) {
			list.push(element);
		}
	},

		

	dispose : function(element) {
		if (element.nodeType != NodeType.ELEMENT || element.id == "console") { return; }

		element.listeners = null;
		element.node = null;
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
			if(this.focus.element) {
				this.openAction();
				XMLEvents.dispatch(this.focus, "DOMFocusOut");
				Core.setClass(this.focus.element, "xforms-focus", false);
				this.focus.blur();
				this.closeAction();
			}

			this.posibleBlur = false;
			this.focus = null;
		}
	}
};
    
	
		
		
		
function Binding(isvalue, xpath, model, bind) {
	this.isvalue = isvalue;
	this.bind = bind? bind : null;
	this.xpath = xpath? XPath.get(xpath) : null;
	var modelelt = model;
	if( typeof model == "string" ) {
		modelelt = document.getElementById(model);
	}
	this.model = model? (modelelt != null ? modelelt.xfElement : model) : null;
}


		

Binding.prototype.evaluate = function(ctx, depsNodes, depsId, depsElements) {
//alert("Binding: "+depsId);
	var result = null;
	if (this.bind) {
		if (typeof this.bind == "string") {
			var idel = document.getElementById(this.bind);
			if (!idel) {
				DebugConsole.write("Binding evaluation returned null for bind: " + this.bind); 
				return null;	// A 'null' signifies bind-ID not found.
			}
			this.bind = idel.xfElement;
		}
		result = this.bind.nodes;
		copyArray(this.bind.depsNodes, depsNodes);
		copyArray(this.bind.depsElements, depsElements);
/*
if(this.bind.depsNodes) {
var s = this.bind.depsNodes.length > 0 ? "Binding: "+depsId+" depsNodes=" : "";
for(var i = 0; i < this.bind.depsNodes.length; i++) s += this.bind.depsNodes[i].nodeName + " ";
if(s!="")alert(s);
}
*/
	} else {
		var exprCtx = new ExprContext(!ctx || (this.model && this.model != document.getElementById(Core.getMeta(ctx.ownerDocument.documentElement, "model")).xfElement) ? this.model ? this.model.getInstanceDocument().documentElement : xforms.defaultModel.getInstanceDocument(): ctx,
			null, null, null, null, ctx, depsNodes, depsId, depsElements);
		result = this.xpath.evaluate(exprCtx);
/*
if(exprCtx.depsNodes) {
var s = exprCtx.depsNodes.length > 0 ? "Binding: "+depsId+" depsNodes=" : "";
for(var i = 0; i < exprCtx.depsNodes.length; i++) s += exprCtx.depsNodes[i].nodeName + " ";
if(s!="")alert(s);
}
*/
	}
	return this.isvalue ? stringValue(result) : result;
};
    
	
		
		
		
var IdManager = {
    cloneId : function(element) {
        assert(element && element.id);
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
						while (parent.nodeType == NodeType.ELEMENT) {
							if (Core.hasClass(parent, "xforms-repeat-item")) {
								if (Core.hasClass(parent, "xforms-repeat-item-selected")) {
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
				index = 0;
    },
    data : [],
    index : 0
};
    
	
	
		
		
		
		
		
function XFCoreElement() {
}


		

XFCoreElement.prototype.init = function(id, parent, className) {
	parent = parent? parent.element : Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "head")[0]: document.getElementsByTagName("head")[0];
	this.element = Core.createElement("span", parent, null, className);
	this.element.id = id;
	this.element.xfElement = this;
};


		

XFCoreElement.prototype.dispose = function() {
	this.element.xfElement = null;
	this.element.parentNode.removeChild(this.element);
	this.element = null;
	this.model = null;
};
    
	
		
		
		
function XFModel(id, schemas) {
	this.init(id, null, "xforms-model");
	this.instances = {};
	this.binds = [];
	this.nodesChanged = [];
	this.newNodesChanged = [];
	this.schemas = [];
	this.defaultInstance = null;
	this.defaultSubmission = null;
	xforms.models.push(this);
	xforms.defaultModel = xforms.defaultModel || this;
	if (document.getElementById(id)) {
		document.getElementById(id).getInstanceDocument = function(modid) {
			return this.xfElement.getInstanceDocument(modid);
		};
		document.getElementById(id).rebuild = function() {
			return this.xfElement.rebuild();
		};
		document.getElementById(id).recalculate = function() {
			return this.xfElement.recalculate();
		};
		document.getElementById(id).revalidate = function() {
			return this.xfElement.revalidate();
		};
		document.getElementById(id).refresh = function() {
			return this.xfElement.refresh();
		};
		document.getElementById(id).reset = function() {
			return this.xfElement.reset();
		};
	}

	if (schemas) {
		schemas = schemas.split(" ");

		for (var i = 0, len = schemas.length; i < len; i++) {
			var founded = false;
			
			for (var sid in Schema.all) {
				var schema = Schema.all[sid];

				if (schema.name == schemas[i]) {
					this.schemas.push(schema);
					founded = true;
					break;
				}
			}
			
			if (!founded) {
				xforms.error(this, "xforms-link-exception", "Schema " + schemas[i] + " not found");
			}
		}
	}
}

XFModel.prototype = new XFCoreElement();


		

XFModel.prototype.addInstance = function(instance) {
	this.instances[instance.element.id] = instance;
	this.defaultInstance = this.defaultInstance || instance;
};


		

XFModel.prototype.addBind = function(bind) {
	this.binds.push(bind);
};


		

XFModel.prototype.dispose = function() {
	this.instances = null;
	this.binds = null;
	this.defaultInstance = null;
	XFCoreElement.prototype.dispose.call(this);
};


		

XFModel.prototype.getInstance = function(id) {
	return id? this.instances[id] : this.defaultInstance;
};


		

XFModel.prototype.getInstanceDocument = function(id) {
	var instance = this.getInstance(id);
	return instance? instance.doc : null;
};


		

XFModel.prototype.findInstance = function(node) {
	var doc = node.nodeType == NodeType.DOCUMENT ? node : node.ownerDocument;
	for (var id in this.instances) {
		var inst = this.instances[id];
		if (doc == inst.doc) {
			return inst;
		}
	}
	return null;
};


		

XFModel.prototype.construct = function() {
	if (!xforms.ready) {
		forEach(this.instances, "construct");
	}
	XMLEvents.dispatch(this, "xforms-rebuild");
	XMLEvents.dispatch(this, "xforms-model-construct-done");
};


		

XFModel.prototype.reset = function() {
	forEach(this.instances, "reset");
	this.setRebuilded(true);
	xforms.addChange(this);
};


		

XFModel.prototype.rebuild = function() {
	if (xforms.ready) {
		this.setRebuilded(true);
	}
	forEach(this.binds, "refresh");
	XMLEvents.dispatch(this, "xforms-recalculate");
};


		

XFModel.prototype.recalculate = function() { 
	forEach(this.binds, "recalculate");
	XMLEvents.dispatch(this, "xforms-revalidate");
};


		

XFModel.prototype.revalidate = function() {
	forEach(this.instances, "revalidate");
		if (xforms.ready) {
		XMLEvents.dispatch(this, "xforms-refresh");
	}
};


		

XFModel.prototype.refresh = function() {
	// Nada?
};


		

XFModel.prototype.addChange = function(node) {
	var list = xforms.building? this.newNodesChanged : this.nodesChanged;

	if (!inArray(node, list)) {
		xforms.addChange(this);
	}
	if (node.nodeType == NodeType.ATTRIBUTE && !inArray(node, list)) {
		list.push(node);
		node = node.ownerElement ? node.ownerElement : node.selectSingleNode("..");
	}
	while (node.nodeType ==  NodeType.ELEMENT && !inArray(node, list)) {
		list.push(node);
		node = node.parentNode;
	}
};


		

XFModel.prototype.setRebuilded = function(value) {
	if (xforms.building) {
		this.newRebuilded = value;
	} else {
		this.rebuilded = value;		
	}
};
    
	
		
		
		
function XFInstance(id, model, readonly, mediatype, src, srcXML) {
	this.init(id, model, "xforms-instance");
	this.readonly = readonly;
	this.mediatype = mediatype;
	this.src = Core.unescape(src);
	switch(mediatype) {
		case "application/xml":
			this.srcXML = Core.unescape(srcXML);
			break;
		case "application/json":
			var json;
			eval("json = " + Core.unescape(srcXML));
			this.srcXML = json2xml("", json, true, false);
			break;
		default:
			alert("Unsupported mediatype '" + mediatype + "' for instance #" + id);
			return;
	}
	this.model = model;
	this.doc = Core.createXMLDocument("<dummy/>");
	Core.setMeta(this.doc.documentElement, "instance", id);
	Core.setMeta(this.doc.documentElement, "model", model.element.id);
	model.addInstance(this);
}

XFInstance.prototype = new XFCoreElement();
 

		

XFInstance.prototype.dispose = function() {
	XFCoreElement.prototype.dispose.call(this);
};


		

XFInstance.prototype.construct = function() {
	if (!xforms.ready) {
		if (this.src) {
			var cross = false;
			if (this.src.match(/^[a-zA-Z0-9+.-]+:\/\//)) {
				var domain = /^([a-zA-Z0-9+.-]+:\/\/[^\/]*)/;
				var sdom = domain.exec(this.src);
				var ldom = domain.exec(document.location.href);
				cross = sdom[0] != ldom[0];
			}
			if (cross) {
				this.setDoc('<dummy xmlns=""/>');
				jsoninstobj = this;
				var scriptelt = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
				scriptelt.setAttribute("src", this.src+"&callback=jsoninst");
				scriptelt.setAttribute("id", "jsoninst");
				scriptelt.setAttribute("type", "text/javascript");
				var body = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
				body.insertBefore(scriptelt, body.firstChild);
			} else {
				try {
					var req = Core.openRequest("GET", this.src, false);
					DebugConsole.write("Loading " + this.src);
					req.send(null);
					if (req.status != 200 && req.status != 0) {
						throw "Request error: " + req.status;
					}
					this.setDoc(req.responseText);
				} catch(e) {
					xforms.error(this.element, "xforms-link-exception", "Fatal error loading " + this.src, e.toString());
				}
			}
		} else {
			this.setDoc(this.srcXML);
		}
	}
};


		

XFInstance.prototype.reset = function() {
	this.setDoc(this.oldXML, true);
};
 

		

XFInstance.prototype.store = function(isReset) {
	if (this.oldXML && !isReset) {
		this.oldXML = null;
	}
	this.oldXML = Core.saveXML(this.doc.documentElement);
};


		

XFInstance.prototype.setDoc = function(xml, isReset, preserveOld) {
	var instid = Core.getMeta(this.doc.documentElement, "instance");
	var modid = Core.getMeta(this.doc.documentElement, "model");
	Core.loadXML(this.doc.documentElement, xml);
	Core.setMeta(this.doc.documentElement, "instance", instid);
	Core.setMeta(this.doc.documentElement, "model", modid);
	if (!preserveOld) {
		this.store(isReset);
	}
	if (instid == "xf-instance-config") {
		Core.config = this.doc.documentElement;
	}
};
        

		

XFInstance.prototype.revalidate = function() {
	if (!this.readonly) {
		this.validation_(this.doc.documentElement);
	}
};

XFInstance.prototype.validation_ = function(node, readonly, notrelevant) {
    if (readonly == null) { readonly = false; }
    if (notrelevant == null) { notrelevant = false; }

    this.validate_(node, readonly, notrelevant);
    readonly = Core.getBoolMeta(node, "readonly");
    notrelevant = Core.getBoolMeta(node, "notrelevant");
    var atts = node.attributes || [];

	if (atts) {
			var atts2 = [];
	    for (var i = 0, len = atts.length; i < len; i++) {
				if (atts[i].nodeName.substr(0,10) != "xsltforms_") {
					atts2[atts2.length] = atts[i];
				}
	    }
	    for (var i = 0, len = atts2.length; i < len; i++) {
				this.validation_(atts2[i], readonly, notrelevant);
	    }
	}
   
    for (var j = 0, len1 = node.childNodes.length; j < len1; j++) {
        var child = node.childNodes[j];

        if (child.nodeType == NodeType.ELEMENT) {
            this.validation_(child, readonly, notrelevant);
        }
    }
};

XFInstance.prototype.validate_ = function(node, readonly, notrelevant) {
	var bindid = Core.getMeta(node, "bind");
	var value = xmlValue(node);
	var schtyp = Schema.getType(Core.getType(node) || "xsd_:string");
	if (bindid) {
		var bind = document.getElementById(bindid).xfElement;
		var nodes = bind.nodes;
		var i = 0;
		for (var len = nodes.length; i < len; i++) {
			if (nodes[i] == node) {
				break;
			}
		}
		for (var j = 0, len = bind.depsNodes.length; j < len; j++) {
			Core.setFalseBoolMeta(bind.depsNodes[j], "depfor_"+bind.depsId);
		}
		bind.depsNodes.length = 0;
		var ctx = new ExprContext(node, i, nodes, null, null, null, [], bind.depsId);
		if (bind.required) {
			this.setProperty_(node, "required", booleanValue(bind.required.evaluate(ctx)));
		}
		this.setProperty_(node, "notrelevant", notrelevant || !(bind.relevant? booleanValue(bind.relevant.evaluate(ctx)) : true));
		this.setProperty_(node, "readonly", readonly || (bind.readonly? booleanValue(bind.readonly.evaluate(ctx)) : bind.calculate ? true : false));
		this.setProperty_(node, "notvalid",
			!Core.getBoolMeta(node, "notrelevant") && !(!(Core.getBoolMeta(node, "required") && (!value || value == ""))
			&& (!schtyp || schtyp.validate(value))
			&& (!bind.constraint || booleanValue(bind.constraint.evaluate(ctx)))));
		copyArray(ctx.depsNodes, bind.depsNodes);
	} else {
		this.setProperty_(node, "notrelevant", notrelevant);
		this.setProperty_(node, "readonly", readonly);
		this.setProperty_(node, "notvalid", schtyp && !schtyp.validate(value));
	}
};

XFInstance.prototype.setProperty_ = function (node, property, value) {
	if (Core.getBoolMeta(node, property) != value) {
		Core.setBoolMeta(node, property, value);
		this.model.addChange(node);   
	}
};

		

function json2xml(name, json, root, inarray) {
	var fullname = "";
	if (name == "________") {
		fullname = " exml:fullname=\"" + Core.escape(name) + "\"";
		name = "________";
	}
	var ret = root ? "<exml:anonymous xmlns:exml=\"http://www.agencexml.com/exml\" xmlns:xsi=\"http://www.w3.org/1999/XMLSchema-instance\" xmlns:exsi=\"http://www.agencexml.com/exi\" xmlns=\"\">" : "";
	if (json instanceof Array) {
		if (inarray) {
			ret += "<exml:anonymous exsi:maxOccurs=\"unbounded\">";
		}
		if (json.length == 0) {
			ret += "<" + (name == "" ? "exml:anonymous" : name) + fullname + " exsi:maxOccurs=\"unbounded\" xsi:nil=\"true\"/>";
		} else {
			for (var i = 0, len = json.length; i < len; i++) {
				ret += json2xml(name == "" ? "exml:anonymous" : name, json[i], false, true);
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
		ret += name == "" ? "" : "<"+name+fullname+(inarray?" exsi:maxOccurs=\"unbounded\"":"")+xsdtype+">";
		if (typeof(json) == "object" && !(json instanceof Date)) {
			for (var m in json) {
				ret += json2xml(m, json[m], false, false);
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
				ret += Core.escape(json);
			}
		}
		ret += name == "" ? "" : "</"+name+">";
	}
	ret += root ? "</exml:anonymous>" : "";
	return ret;
}

		

var jsoninstobj;
function jsoninst(json) {
	//alert(json2xml("", json, true, false));
	jsoninstobj.setDoc(json2xml("", json, true, false));
	xforms.addChange(jsoninstobj.model);
	XMLEvents.dispatch(jsoninstobj.model, "xforms-rebuild");
	xforms.refresh();
	document.body.removeChild(document.getElementById("jsoninst"));
}
    
	
		
		
		
function XFBind(id, parent, nodeset, type, readonly, required, relevant, calculate, constraint) {
	this.init(id, parent, "xforms-bind");
	this.model = parent.model || parent;
	this.type = type? Schema.getType(type) : null;
	this.nodeset = nodeset;
	this.readonly = XPath.get(readonly);
	this.required = XPath.get(required);
	this.relevant = XPath.get(relevant);
	this.calculate = XPath.get(calculate);
	this.constraint = XPath.get(constraint);
	this.depsNodes = [];
	this.depsElements = [];
	this.nodes = [];
	this.binding = new Binding(false, this.nodeset);
	parent.addBind(this);
	this.depsId = XFElement.depsId++;
}

XFBind.prototype = new XFCoreElement();

XFBind.prototype.addBind = function() {};


		

XFBind.prototype.refresh = function(ctx, index) {
	if (!index) {
		for (var i = 0, len = this.depsNodes.length; i < len; i++) {
			Core.setFalseBoolMeta(this.depsNodes[i], "depfor_"+this.depsId);
		}
		this.depsNodes.length = 0;
		this.depsElements.length = 0;
		this.nodes.length = 0;
	}

	ctx = ctx || (this.model ? this.model.getInstanceDocument() ? this.model.getInstanceDocument().documentElement : null : null);
	copyArray(this.binding.evaluate(ctx, this.depsNodes, this.depsId, this.depsElements), this.nodes);
	var el = this.element;

	for (var i = 0, len = this.nodes.length; i < len; i++) {
		var node = this.nodes[i];
		var bindid = Core.getMeta(node, "bind");
		if (bindid && this.element.id != bindid) {
			XFProcessor.error(el, "xforms-binding-exception", "Two binds affect one node");
		} else {
			Core.setMeta(node, "bind", this.element.id);

			if (this.type) {
				if (Core.getMeta(node, "schemaType")) {
					XFProcessor.error(el, "xforms-binding-exception", "Type especified in xsi:type attribute");
				} else {
					var name = this.type.name;
					var ns = this.type.nsuri;
					for (var key in Schema.prefixes) {
						if (Schema.prefixes[key] == ns) {
							name = key + ":" + name;
							break;
						}
					}
					Core.setType(node, name);
				}
			}
		}

		for (var j = 0, len1 = el.childNodes.length; j < len1; j++) {
			el.childNodes[j].xfElement.refresh(node, i);
		}
	}
};


		

XFBind.prototype.recalculate = function() {
	var el = this.element;

	if (this.calculate) {
		for (var i = 0, len = this.nodes.length; i < len; i++) {
			var node = this.nodes[i];
			var ctx = new ExprContext(node, i + 1, this.nodes);
			var value = stringValue(this.calculate.evaluate(ctx));
			value = Schema.getType(Core.getType(node) || "xsd_:string").normalize(value);
			setValue(node, value);
			this.model.addChange(node);
			DebugConsole.write("Calculate " + node.nodeName + " " + value);
		}
	}

	for (var j = 0, len1 = el.childNodes.length; j < len1; j++) {
		el.childNodes[j].xfElement.recalculate();
	}
};
    
	
		
		
		
function XFSubmission(id, model, ref, bind, action, method, version, indent,
			mediatype, encoding, omitXmlDeclaration, cdataSectionElements,
			replace, instance, separator, includenamespaceprefixes, validate,
			synchr, show, serialization) {
	this.init(id, model, "xforms-submission");
	this.model = model;
	if (model.defaultSubmission == null) {
		model.defaultSubmission = this;
	}
	this.action = action;
	this.method = method;
	this.replace = replace;
	this.version = version;
	this.indent = indent;
	this.validate = validate;
	this.synchr = synchr;
	this.show = show;
	this.serialization = serialization;

	if (mediatype != null) {
		var lines = mediatype.split(";");
		this.mediatype = lines[0];
         
		for (var i = 1, len = lines.length; i < len; i++) {
			var vals = lines[i].split("=");

			switch (vals[0]) {
				case "action" : this.soapAction = vals[1]; break;
				case "charset" : this.charset = vals[1]; break;
			}
		}
	}
    
	this.encoding = encoding;
	this.omitXmlDeclaration = omitXmlDeclaration;
	this.cdataSectionElements = cdataSectionElements;
	this.instance = instance;
	this.separator = separator == "&amp;"? "&" : separator;
	this.includenamespaceprefixes = includenamespaceprefixes;
	this.headers = [];

	if (ref || bind) {
		this.binding = new Binding(false, ref, model.element, bind);
        
		this.eval_ = function() {
			return this.binding.evaluate()[0];
		};
	} else {
		this.eval_ = function() {
			return this.model.getInstanceDocument();
		};
	}
};

XFSubmission.prototype = new XFCoreElement();


		

XFSubmission.prototype.header = function(nodeset, combine, name, values) {
	this.headers.push({nodeset: nodeset, combine: combine, name: name, values: values});
	return this;
};

		

XFSubmission.prototype.submit = function() {
	xforms.openAction();
	var node = this.eval_();
	var action = "error";
	if(this.action.evaluate) {
		action = stringValue(this.action.evaluate());
	} else {
		action = this.action;
	}
	var method = "post";
	if(this.method.evaluate) {
		var method = stringValue(this.method.evaluate());
	} else {
		method = this.method;
	}
	var evcontext = {"method": method, "resource-uri": action};

	if (node) {
		if (this.validate && !validate_(node)) {
			evcontext["error-type"] = "validation-error";
			this.issueSubmitException_(evcontext, null, null);
			xforms.closeAction();
			return;
		}

		if ((method == "get" || method == "delete") && this.serialization != "none") {
			var tourl = XFSubmission.toUrl_(node, this.separator);
			action += (action.indexOf('?') == -1? '?' : this.separator)
				+ tourl.substr(0, tourl.length - this.separator.length);
		}
	}
	
	XMLEvents.dispatch(this, "xforms-submit-serialize");
	var ser = node ? Core.saveXML(node) : "";
	if (action.substr(0,7) == "file://") {
		alert('XSLTForms Submission\n---------------------------\n\n'+action+'\n\nfile:// is not supported for security reasons.\n\nContents copied instead in clipboard if possible\nand displayed by the browser.');
		if (window.clipboardData) {
			window.clipboardData.setData('Text', ser);
		}
		w = window.open("about:blank","_blank");
		w.document.write(ser);
		w.document.close();
		xforms.closeAction();
		return;
	}

	var synchr = this.synchr;
	var instance = this.instance;

	if(method == "xml-urlencoded-post") {
		var outForm = document.getElementById("xsltforms_form");
		if(outForm) {
			outForm.firstChild.value = ser;
		} else {
			outForm = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "form") : document.createElement("form");
			outForm.setAttribute("method", "post");
			outForm.setAttribute("action", action);
			outForm.setAttribute("id", "xsltforms_form");
			var txt = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "input") : document.createElement("input");
			txt.setAttribute("type", "hidden");
			txt.setAttribute("name", "postdata");
			txt.setAttribute("value", ser);
			outForm.appendChild(txt);
			body = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
			body.insertBefore(outForm, body.firstChild);
		}
 		outForm.submit(); 	
		xforms.closeAction();
	} else {
	
		var cross = false;
		if (action.match(/^[a-zA-Z0-9+.-]+:\/\//)) {
			var domain = /^([a-zA-Z0-9+.-]+:\/\/[^\/]*)/;
			var sdom = domain.exec(action);
			var ldom = domain.exec(document.location.href);
			cross = sdom[0] != ldom[0];
		}
		if (cross) {
			jsoninstobj = instance == null? (node ? document.getElementById(Core.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "instance")).xfElement : this.model.getInstance()) : document.getElementById(instance).xfElement;
			var scriptelt = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
			scriptelt.setAttribute("src", action.replace(/&amp;/g, "&")+"&callback=jsoninst");
			scriptelt.setAttribute("id", "jsoninst");
			scriptelt.setAttribute("type", "text/javascript");
			var body = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
			body.insertBefore(scriptelt, body.firstChild);
			XMLEvents.dispatch(this, "xforms-submit-done");
			xforms.closeAction();
		} else {

			// TODO: Validate binding target is not empty
			if (!node && (method != "get" || method != "delete")) {
		    evcontext["error-type"] = "no-data";
		    this.issueSubmitException_(evcontext, null, null);
		    return;
			}

			var req = null;
			var subm = this;
			try {
				req = Core.openRequest(method, action, !synchr);
			
				var func = function() {
					if (!synchr && req.readyState != 4) { return; }
					
					try {
						if (req.status != 200 && req.status != 0) {
							evcontext["error-type"] = "resource-error";
							subm.issueSubmitException_(evcontext, req, null);
							xforms.closeAction();
							return;
						}
			
						if (subm.replace == "instance") {
							var inst = instance == null? (node ? document.getElementById(Core.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "instance")).xfElement : subm.model.getInstance()) : document.getElementById(instance).xfElement;
							inst.setDoc(req.responseText, false, true);
							xforms.addChange(subm.model);
							XMLEvents.dispatch(subm.model, "xforms-rebuild");
							xforms.refresh();
						}
			
						subm.requesteventlog(evcontext, req);
						XMLEvents.dispatch(subm, "xforms-submit-done", null, null, null, null, evcontext);
						xforms.closeAction();
						
						if (subm.replace == "all") {
							var resp = req.responseText;
							var piindex = resp.indexOf("<?xml-stylesheet", 0);
							while ( piindex != -1) {
								var xslhref = resp.substr(piindex, resp.substr(piindex).indexOf("?>")).replace(/^.*href=\"([^\"]*)\".*$/, "$1");
								resp = Core.transformText(resp, xslhref, false);
								piindex = resp.indexOf("<?xml-stylesheet", 0);
							}
							if( subm.show == "new" ) {
								w = window.open("about:blank","_blank");
								w.document.write(resp);
								w.document.close();
							} else {
								Dialog.hide("statusPanel", false);
								xforms.close();
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
						DebugConsole.write(e || e.message);
						evcontext["error-type"] = "parse-error";
						subm.issueSubmitException_(evcontext, req, e);
						xforms.closeAction();
					}
				
				};
			
				if (!synchr) {
					req.onreadystatechange = func;
				}
			
				var media = this.mediatype;
				var mt = (media || "application/xml")
					+ (this.charset? ";charset=" + this.charset : "");
			
				DebugConsole.write("Submit " + this.method + " - " + mt + " - "
					+ action + " - " + synchr);
				
				var len = this.headers.length;
				if (len != 0) {
					var headers = [];
					for (var i = 0, len = this.headers.length; i < len; i++) {
						var nodes = [];
						if (this.headers[i].nodeset) {
							nodes = this.headers[i].nodeset.evaluate();
						} else {
							nodes = [subm.model.getInstanceDocument().documentElement];
						}
						var hname;
						for (var n = 0, lenn = nodes.length; n < lenn; n++) {
							if (this.headers[i].name.evaluate) {
								hname = stringValue(this.headers[i].name.evaluate(nodes[n]));
							} else {
								hname = this.headers[i].name;
							}
							if (hname != "") {
								var hvalue = "";
								var j;
								for (j = 0, len2 = this.headers[i].values.length; j < len2; j++) {
									var hv = this.headers[i].values[j];
									var hv2;
									if (hv.evaluate) {
										hv2 = stringValue(hv.evaluate(nodes[n]));
									} else {
										hv2 = hv;
									}
									hvalue += hv2;
									if (j < len2 - 1) {
										hvalue += ",";
									}
								}
								var len3 = headers.length;
								for (j = 0; j < len3; j++) {
									if (headers[j].name == hname) {
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
								if (j == len3) {
									headers.push({name: hname, value: hvalue});
								}
							}
						}
					}
					for (var i = 0, len = headers.length; i < len; i++) {
						req.setRequestHeader(headers[i].name, headers[i].value);
					}
				}

				if (method == "get" || method == "delete") {
					if (media == XFSubmission.SOAP_) {
						req.setRequestHeader("Accept", mt);
					} else {
						if (subm.replace == "instance") {
							req.setRequestHeader("Accept", "application/xml,text/xml");
						}
					}
			
					req.send(null);
				} else {
					req.setRequestHeader("Content-Type", mt);
			
					if (media == XFSubmission.SOAP_) {
						req.setRequestHeader("SOAPAction", this.soapAction);
					} else {
						if (subm.replace == "instance") {
							req.setRequestHeader("Accept", "application/xml,text/xml");
						}
					}
					req.send(ser);
				}
			
				if (synchr) {
					func();
				}
			} catch(e) {
				DebugConsole.write(e.message || e);
				evcontext["error-type"] = "resource-error";
				subm.issueSubmitException_(evcontext, req, e);
				xforms.closeAction();
			}
		}
	}
};


XFSubmission.SOAP_ = "application/soap+xml";


		

XFSubmission.prototype.requesteventlog = function(evcontext, req) {
	evcontext["response-status-code"] = req.status;
	evcontext["response-reason-phrase"] = req.statusText;
	var rheads = req.getAllResponseHeaders().replace(/\r/, "");
	var rheaderselts = "";
	if (rheads) {
		rheads = rheads.split("\n");
		for (var i = 0, len = rheads.length; i < len; i++) {
			var colon = rheads[i].indexOf(":");
			if (colon != -1) {
				var name = rheads[i].substring(0, colon).replace(/^\s+|\s+$/, "");
				var value = rheads[i].substring(colon+1).replace(/^\s+|\s+$/, "");
				rheaderselts += "<header><name>"+Core.escape(name)+"</name><value>"+Core.escape(value)+"</value></header>";
			}
		}
	}
	evcontext.rheadsdoc = Core.createXMLDocument("<data>"+rheaderselts+"</data>");
	if (evcontext.rheadsdoc.documentElement.firstChild) {
		var rh = evcontext.rheadsdoc.documentElement.firstChild;
		evcontext["response-headers"] = [rh];
		while (rh.nextSibling) {
			rh = rh.nextSibling;
			evcontext["response-headers"].push(rh);
		}
	}
	if (req.responseXML) {
		evcontext["response-body"] = Core.createXMLDocument(req.responseText);
	} else {
		evcontext["response-body"] = req.responseText;
	}
};

		

XFSubmission.prototype.issueSubmitException_ = function(evcontext, req, ex) {
	if (ex) {
		evcontext["message"] = ex.message || ex;
		evcontext["stack-trace"] = ex.stack;
	}
	if (req) {
		this.requesteventlog(evcontext, req);
	}
	XMLEvents.dispatch(this, "xforms-submit-error", null, null, null, null, evcontext);
};

		

XFSubmission.toUrl_ = function(node, separator) {
	var url = "";
	var val = "";
	var hasChilds = false;

	for(var i = 0, len = node.childNodes.length; i < len; i++) {
		var child = node.childNodes[i];

		switch (child.nodeType) {
		case NodeType.ELEMENT :
			hasChilds = true;
			url += this.toUrl_(child, separator);
			break;
		case NodeType.TEXT :
			val += child.nodeValue;
			break;
		}
	}
    
	if (!hasChilds && val.length > 0) {
		url += node.nodeName + '=' + encodeURIComponent(val) + separator;
	}
    
	return url;
};
    
	
		
		
		
var XFProcessor = {

		

	error : function(element, type, value) {
			alert(type+": "+value);
		}
};
    
	
		
		
function AJXTimer(id, time) {
	this.init(id, null, "xforms-timer");
	this.running = false;
	this.time = time;
}

AJXTimer.prototype = new XFCoreElement();

AJXTimer.prototype.start = function() {
	this.running = true;
	var timer = this;
	setTimeout(function() { timer.run(); }, this.time);
};

AJXTimer.prototype.stop = function() {
	this.running = false;
};

AJXTimer.prototype.run = function() {
	if (this.running) {
		var timer = this;
		xforms.openAction();
		XMLEvents.dispatch(timer.element, "ajx-time");
		xforms.closeAction();
		setTimeout(function() { timer.run(); }, this.time);
	}
};
    
	
	
		
		
		
		
function AJXConfirm(id, binding, ifexpr, whileexpr) {
	this.id = id;
	this.binding = binding;
	this.init(ifexpr, whileexpr);
}

AJXConfirm.prototype = new XFAbstractAction();

AJXConfirm.prototype.run = function(element, ctx, evt) {
	var text;

	if (this.binding) {
		var node = this.binding.evaluate(ctx)[0];

		if (node) {
			text = getValue(node);
		}
	} else {
		var e = IdManager.find(this.id);
		xforms.build(e, ctx);
		text = e.textContent || e.innerText;
	}

	if (text) {
		var res = confirm(text.trim());
        
		if (!res) {
			evt.stopPropagation();
			evt.stopped = true;
		}
	}
};
    
	
		
		
function AJXSetproperty(name, value, literal, ifexpr, whileexpr) {
	this.name = name;
	this.value = value;
	this.literal = literal;
	this.init(ifexpr, whileexpr);
}

AJXSetproperty.prototype = new XFAbstractAction();

AJXSetproperty.prototype.run = function(element, ctx) {
	var value = this.literal;

	if (this.value) {
		value = this.value.evaluate(node); // ??? What is node?
	    
		if (typeof(value) != "string" && typeof(value.length) != "undefined") {
			value = value.length > 0? getValue(value[0]) : "";
		}
	}
	
	if (value) {
		I8N.lang = value;
		DebugConsole.write("setproperty " + name + " = " + value);
	}
};
    
	
		
		
		
function XFAbstractAction() {
}


		

XFAbstractAction.prototype.init = function(ifexpr, whileexpr) {
	this.ifexpr = XPath.get(ifexpr);
	this.whileexpr = XPath.get(whileexpr);
};


		

XFAbstractAction.prototype.execute = function(element, ctx, evt) {
	if (evt.stopped) { return; }
	
	if (!ctx) {
		ctx = element.node || (xforms.defaultModel.getInstanceDocument() ? xforms.defaultModel.getInstanceDocument().documentElement : null);
	}

	if (this.whileexpr) {
		while(booleanValue(this.whileexpr.evaluate(ctx))) {
			if(!this.exec_(element, ctx, evt)) {
				break;
			}
		}
	} else {
		this.exec_(element, ctx, evt);
	}
};


		

XFAbstractAction.prototype.exec_ = function(element, ctx, evt) {
	if (this.ifexpr) {
		if (booleanValue(this.ifexpr.evaluate(ctx))) {
			this.run(element, ctx, evt);
		} else {
			return false;
		}
	} else {
		this.run(element, ctx, evt);
	}
	return true;
};


		

XFAbstractAction.prototype.run = function(element, ctx, evt) { };
    
	
		
		
		
function XFAction(ifexpr, whileexpr) {
	this.init(ifexpr, whileexpr);
	this.childs = [];
}

XFAction.prototype = new XFAbstractAction();


		

XFAction.prototype.add = function(action) {
	this.childs.push(action);
	return this;
};


		

XFAction.prototype.run = function(element, ctx, evt) {
	forEach(this.childs, "execute", element, ctx, evt);
};
    
	
		
		
		
function XFDelete(nodeset, model, bind, at, context, ifexpr, whileexpr) {
	this.binding = new Binding(false, nodeset, model, bind);
	//this.at = at?XPath.get(at):null;
	this.at = XPath.get(at);
	this.context = XPath.get(context);
	this.init(ifexpr, whileexpr);
}

XFDelete.prototype = new XFAbstractAction();


		

XFDelete.prototype.run = function(element, ctx) {
	if (this.context) {
		ctx = this.context.evaluate(ctx)[0];
	}
    
	if (!ctx) { return; }

	var nodes = this.binding.evaluate(ctx);
	
	if(this.at) {
		var index = numberValue(this.at.evaluate(new ExprContext(ctx, 1, nodes)));
		if(!nodes[index - 1]) { return; }
		nodes = [nodes[index - 1]];
	}

	var model;
	var instance;
	if( nodes.length > 0 ) {
		model = document.getElementById(Core.getMeta(nodes[0].documentElement ? nodes[0].documentElement : nodes[0].ownerDocument.documentElement, "model")).xfElement;
		instance = model.findInstance(nodes[0]);
	}

	for(var i = 0, len = nodes.length; i < len; i++) {
		var node = nodes[i];
		var repeat = Core.getMeta(node, "repeat");

		if (node.nodeType == NodeType.ATTRIBUTE) {
			node.ownerElement ? node.ownerElement.removeAttributeNS(node.namespaceURI, node.nodeName) : node.selectSingleNode("..").removeAttributeNS(node.namespaceURI, node.nodeName);
		} else {
			node.parentNode.removeChild(node);
		}
		

		if (repeat) {
			document.getElementById(repeat).xfElement.deleteNode(node);
		}
	}

	if( nodes.length > 0 ) {
		xforms.addChange(model);
		model.setRebuilded(true);
	  XMLEvents.dispatch(instance, "xforms-delete");
	}
};
    
	
		
		
		
function XFDispatch(name, target, ifexpr, whileexpr, delay) {
	this.name = name;
	this.target = target;
	this.init(ifexpr, whileexpr);
	this.delay = delay;
}

XFDispatch.prototype = new XFAbstractAction();


		

XFDispatch.prototype.run = function(element, ctx, evt) {
	var target;
	if (this.target == null) {
		switch (this.name) {
			case "xforms-submit":
				target = document.getElementById(Core.getMeta(ctx.ownerDocument.documentElement, "model")).xfElement.defaultSubmission;
				break;
			case "xforms-reset":
				target = document.getElementById(Core.getMeta(ctx.ownerDocument.documentElement, "model")).xfElement;
				break;
		}
	} else {
		target = typeof this.target == "string"? document.getElementById(this.target) : this.target;
	}
	var delay = 0;
	if (this.delay) {
		if (this.delay.evaluate) {
			delay = numberValue(this.delay.evaluate());
		} else {
			delay = numberValue(this.delay);
		}
	}
	if (delay > 0 ) {
		window.setTimeout("XMLEvents.dispatch(document.getElementById('"+target.id+"'),'"+this.name+"')", delay);
	} else {
		XMLEvents.dispatch(target, this.name);
	}
};
    
	
		
		
		
function XFInsert(nodeset, model, bind, at, position, origin, context, ifexpr, whileexpr) {
	this.binding = new Binding(false, nodeset, model, bind);
	this.origin = XPath.get(origin);
	this.context = XPath.get(context);
	this.at = XPath.get(at);
	this.position = position;
	this.init(ifexpr, whileexpr);
}

XFInsert.prototype = new XFAbstractAction();


		

XFInsert.prototype.run = function(element, ctx) {
	if (this.context) {
		ctx = this.context.evaluate(ctx)[0];
	}
    
	if (!ctx) { return; }

	var nodes = [];
	if( this.binding.bind || this.binding.xpath ) {
		nodes = this.binding.evaluate(ctx);
	}
	var index = 0;
	var node = null;
	var originNodes = [];
	var parent = null;
	var pos = this.position == "after"? 1 : 0;
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
			parent = nodes[0].nodeType == NodeType.DOCUMENT? nodes[0] : nodes[0].nodeType == NodeType.ATTRIBUTE? nodes[0].ownerDocument ? nodes[0].ownerDocument : nodes[0].selectSingleNode("..") : nodes[0].parentNode;
	        
			if (parent.nodeType != NodeType.DOCUMENT && node.nodeType != NodeType.ATTRIBUTE) {
				res = this.at? Math.round(numberValue(this.at.evaluate(new ExprContext(ctx, 1, nodes)))) + i - 1: nodes.length - 1;
				index = isNaN(res)? nodes.length : res + pos;
			}
		}

		DebugConsole.write("insert " + node.nodeName + " in " + parent.nodeName
			+ " at " + index + " - " + ctx.nodeName);
			
		if (node.nodeType == NodeType.ATTRIBUTE) {
			Core.setAttributeNS(parent, node.namespaceURI, (node.prefix ? node.prefix+":" : "")+node.nodeName, node.nodeValue);
		} else {
			var clone = node.cloneNode(true);

			if (parent.nodeType == NodeType.DOCUMENT) {
				var first = parent.documentElement;
				var prevmodel = Core.getMeta(first, "model");
				var previnst = Core.getMeta(first, "instance");
				parent.removeChild(first);
				first = null;
				Core.setMeta(clone, "instance", previnst);
				Core.setMeta(clone, "model", prevmodel);
				parent.appendChild(clone);
			} else {
				var nodeAfter;

				if (index >= nodes.length && nodes.length != 0) {
					nodeAfter = nodes[nodes.length - 1].nextSibling;
				} else {
					nodeAfter = nodes[index];
				}
				if (nodeAfter) {
					nodeAfter.parentNode.insertBefore(clone, nodeAfter);
				} else {
					parent.appendChild(clone);
				}

				var repeat = nodes.length > 0? Core.getMeta(nodes[0], "repeat") : null;
				nodes.push(clone);

				if (repeat) {
					document.getElementById(repeat).xfElement.insertNode(clone, nodeAfter);
				}
			}
		}
	}

	var model = document.getElementById(Core.getMeta(parent.documentElement ? parent.documentElement : parent.ownerDocument.documentElement, "model")).xfElement;
	xforms.addChange(model);
	model.setRebuilded(true);
	XMLEvents.dispatch(model.findInstance(parent), "xforms-insert");
};
    
	
		
		
		
function XFLoad(binding, resource, show, ifexpr, whileexpr) {
	this.binding = binding;
	this.resource = resource;
	this.show = show;
	this.init(ifexpr, whileexpr);
}

XFLoad.prototype = new XFAbstractAction();


		

XFLoad.prototype.run = function(element, ctx) {
	var href = this.resource;

	if (this.binding) {
		var node = this.binding.evaluate(ctx)[0];

		if (node) {
			href = getValue(node);
		}
	} else {
		if (typeof href == 'object') {
			href = stringValue(this.resource.xpath.evaluate(ctx));
		} else {
			if (typeof href == 'string') {
				href = Core.unescape(href); 
			}
		}
	}

	if (href) {
		if(href.substr(0, 11) == "javascript:") {
			try {
				eval("{XSLTFormsContext={elementId:\""+element.getAttribute("id")+"\"};"+href.substr(11)+"}");
			} catch (e) {
				alert("XSLTForms Exception\n--------------------------\n\nError evaluating the following Javascript expression :\n\n"+href.substr(11)+"\n\n"+e);
			}
		} else if (this.show == "new") {
			window.open(href);
		} else {
			location.href = href;
		}
	}
};
    
	
		
		
		
function XFMessage(id, binding, level, ifexpr, whileexpr) {
	this.binding = binding;
	this.id = id;
	this.level = level;
	this.init(ifexpr, whileexpr);
}

XFMessage.prototype = new XFAbstractAction();


		

XFMessage.prototype.run = function(element, ctx) {
	var text;

	if (this.binding) {
		var node = this.binding.evaluate(ctx)[0];

		if (node) {
			text = getValue(node);
		}
	} else {
		var e = IdManager.find(this.id);
		var building = xforms.building;
		xforms.building = true;
		xforms.build(e, ctx);
		xforms.building = building;
		text = e.textContent || e.innerText;
	}

	if (text) {
		alert(text.trim());
	}
};
    
	
		
		
		
function XFScript(binding, stype, script, ifexpr, whileexpr) {
	this.binding = binding;
	this.stype = stype;
	this.script = script;
	this.init(ifexpr, whileexpr);
}

XFScript.prototype = new XFAbstractAction();


		

XFScript.prototype.run = function(element, ctx) {
	var script = this.script;

	switch (this.stype) {
		case "text/javascript":
			if (this.binding) {
				var node = this.binding.evaluate(ctx)[0];
				if (node) {
					script = getValue(node);
				}
			} else {
				if (typeof script == 'object') {
					script = stringValue(this.script.xpath.evaluate(ctx));
				} else {
					if (typeof script == 'string') {
						script = Core.unescape(script); 
					}
				}
			}
			if (script) {
				try {
					eval("{XSLTFormsContext={elementId:\""+element.getAttribute("id")+"\"};"+script+"}");
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
    
	
		
		
		
function XFSetindex(repeat, index, ifexpr, whileexpr) {
	this.repeat = repeat;
	this.index = XPath.get(index);
	this.init(ifexpr, whileexpr);
}

XFSetindex.prototype = new XFAbstractAction();


		

XFSetindex.prototype.run = function(element, ctx) {
	var repeat = IdManager.find(this.repeat);
	var index = numberValue(this.index.evaluate(ctx));
    DebugConsole.write("setIndex " + index);

	if (!isNaN(index)) {
		repeat.xfElement.setIndex(index);
	}
};
    
	
		
		
		
function XFSetvalue(binding, value, literal, ifexpr, whileexpr) {
	this.binding = binding;
	this.value = value? XPath.get(value) : null;
	this.literal = literal;
	this.init(ifexpr, whileexpr);
}

XFSetvalue.prototype = new XFAbstractAction();


		

XFSetvalue.prototype.run = function(element, ctx) {
	var node = this.binding.evaluate(ctx)[0];

	if (node) {
		var value = this.value? stringValue(this.value.evaluate(node, ctx))
			: this.literal;
		xforms.openAction();
		setValue(node, value || "");
		document.getElementById(Core.getMeta(node.ownerDocument.documentElement, "model")).xfElement.addChange(node);
		DebugConsole.write("Setvalue " + node.nodeName + " = " + value); 
		xforms.closeAction();
	}
};
    
	
		
		
		
function XFToggle(caseId, ifexpr, whileexpr) {
	this.caseId = caseId;
	this.init(ifexpr, whileexpr);
}

XFToggle.prototype = new XFAbstractAction();


		

XFToggle.prototype.run = function(element, ctx) {
	XFToggle.toggle(this.caseId, ctx);
};


		

XFToggle.toggle = function(caseId, ctx) {
	xforms.openAction();
	if (typeof caseId == 'object') {
		if (!ctx) {
			ctx = xforms.defaultModel.getInstanceDocument() ? xforms.defaultModel.getInstanceDocument().documentElement : null;
		}
		caseId = stringValue(caseId.xpath.evaluate(ctx));
	}
	var element = IdManager.find(caseId);
	var childs = element.parentNode.childNodes;
	var ul;
	var index = -1;

	if (childs.length > 0 && childs[0].nodeName.toLowerCase() == "ul") {
		ul = childs[0];
	}

	for (var i = ul != null? 1 : 0, len = childs.length; i < len; i++) {
		var child = childs[i];

		if (child == element) {
			index = i - 1;
		} else {
			if (child.style && child.style.display != "none") {
				child.style.display = "none";
									 
				if (ul != null) {
					Core.setClass(ul.childNodes[i - 1], "ajx-tab-selected", false);
				}
			}
			XMLEvents.dispatch(child, "xforms-deselect");
		}
	}

	if (element.style.display == "none") {
		element.style.display = "block";
	    
		if (ul != null) {
			Core.setClass(ul.childNodes[index], "ajx-tab-selected", true);
		}
	}
	XMLEvents.dispatch(element, "xforms-select");

	xforms.closeAction();
};
    
	
	
		
		
		
		
function AJXTree(id, binding) {
	this.init(id);
	this.binding = binding;
	this.hasBinding = true;
	this.root = Core.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "ul")[0] : this.element.getElementsByTagName("ul")[0];
	this.label = this.root.firstChild.cloneNode(true);
}

AJXTree.prototype = new XFElement();

AJXTree.prototype.dispose = function() {
	this.root = null;
	this.selected = null;
	XFElement.prototype.dispose.call(this);
};

AJXTree.prototype.build_ = function(ctx) {
	var node = this.evaluateBinding(this.binding, ctx)[0];

	if (node) {
		var nodes = [];
		this.buildTree(this.root, 0, node, nodes);

		if (!this.selected || !inArray(this.selected, nodes)) {
			this.select(this.root.firstChild);
		}
	}
};

AJXTree.prototype.select = function(item) {
	var changed = true;
	var init = !!this.selected;

	if (init) {
		if (this.selected == item) {
			changed = false;
		} else {
			Core.setClass(this.selected, "xforms-tree-item-selected", false);
			Core.setClass(this.selected.childNodes[1], "xforms-tree-item-label-selected", false);
		}
	}
    
	if (changed) {
		this.element.node = item.node;
		this.selected = item;
		Core.setClass(item, "xforms-tree-item-selected", true);
		Core.setClass(item.childNodes[1], "xforms-tree-item-label-selected", true);
		xforms.openAction();
		xforms.addChange(this);
		xforms.addChange(document.getElementById(Core.getMeta(item.node.ownerDocument.documentElement, "model")).xfElement);
		xforms.closeAction();
	}
};

AJXTree.prototype.click = function(target) {
	if (target.className == "xforms-tree-item-button") {
		var ul = target.nextSibling.nextSibling;

		if (ul != null) {
			ul.style.display = ul.style.display != "none"? "none" : "block";
		}
	} else if (Core.hasClass(target, "xforms-tree-item-label")) {
		this.select(target.parentNode);
	}
};

AJXTree.prototype.buildTree = function(parent, index, node, nodes) {
	var li = null;
	var ul = null;
	var label = null;
	var childs = node.childNodes;
	var nochild = childs.length === 0;
	nodes.push(node);

	if (parent.childNodes.length < index + 1) {
		li = this.label.cloneNode(true);
		parent.appendChild(li);
		XFRepeat.initClone(li);
	} else {
		li = parent.childNodes[index];
		var last = li.lastChild;
		
		if (last.nodeName.toLowerCase() == "ul") {
			ul = last;

			if (nochild) {
				xforms.dispose(ul);
				li.removeChild(ul);
			}
		}
	}

	li.node = node;
	Core.setClass(li, "xforms-tree-item-fork", !nochild);
	Core.setClass(li, "xforms-tree-item-leaf", nochild);

	for (var i = 0, len = childs.length; i < len; i++) {
		var child = childs[i];

		if (child.nodeType == NodeType.ELEMENT) {
			if (!ul) {
				ul = Core.createElement("ul", li);
			}

			this.buildTree(ul, i, child, nodes);
		}
	}

	if (ul) {		
		for (var j = ul.childNodes.length, len1 = childs.length; j > len1; j--) {
			xforms.dispose(ul.lastChild);
			ul.removeChild(ul.lastChild);
		}
	}
};

AJXTree.prototype.refresh = function() {
};
    
	
		
		
		
function XFAVT(id, attrs) {
	this.init(id);
	this.attrs = attrs;
	this.bindings = [];
	for (var i = 0, len = attrs.length; i < len; i++) {
		
	}
	this.hasBinding = true;
	this.isOutput = true;
}

XFAVT.prototype = new XFControl();


		

XFAVT.prototype.clone = function(id) { 
	return new XFAVT(id, this.attrs);
};


		

XFAVT.prototype.dispose = function() {
	this.attrs = null;
	XFControl.prototype.dispose.call(this);
};


		

XFAVT.prototype.setValue = function(value) {
	var node = this.element.node;
	var element = this.valueElement;

    if (element.nodeName.toLowerCase() == "span") {
			if (this.mediatype == "application/xhtml+xml") {
				while (element.firstChild) {
					element.removeChild(element.firstChild);
				}
				XDocument.parse(value, element);
			} else {
        setValue(element, value);
			}
    } else {
        element.src = value;
    }
};

	
		
		
		
function XFElement() {
}


		

XFElement.depsId = 0;

XFElement.prototype.init = function(id) {
	this.element = document.getElementById(id);
	this.element.xfElement = this;
	this.depsElements = [];
	this.depsNodesBuild = [];
	this.depsNodesRefresh = [];
	this.depsIdB = XFElement.depsId++;
	this.depsIdR = XFElement.depsId++;
};


		

XFElement.prototype.dispose = function() {
	if(this.element) {
		this.element.xfElement = null;
		this.element = null;
	}
	this.depsElements = null;
	if (this.depsNodesBuild) {
		for (var i = 0, len = this.depsNodesBuild.length; i < len; i++) {
			Core.setFalseBoolMeta(this.depsNodesBuild[i], "depfor_"+this.depsIdB);
		}
	}
	this.depsNodesBuild = null;
	if (this.depsNodesRefresh) {
		for (var i = 0, len = this.depsNodesRefresh.length; i < len; i++) {
			Core.setFalseBoolMeta(this.depsNodesRefresh[i], "depfor_"+this.depsIdR);
		}
	}
	this.depsNodesRefresh = null;
};


		

XFElement.prototype.build = function(ctx) {
	if (this.hasBinding) {
		var deps = this.depsElements;
		var depsN = this.depsNodesBuild;
		var depsR = this.depsNodesRefresh;
		var build = !xforms.ready || (deps.length === 0) || ctx != this.ctx;
		var refresh = false;
		var changes = xforms.changes;

		for (var i0 = 0, len0 = depsN.length; !build && i0 < len0; i0++) {
			build = depsN[i0].nodeName == "";
		}
		for (var i = 0, len = deps.length; !build && i < len; i++) {
			var el = deps[i];

			for (var j = 0, len1 = changes.length; !build && j < len1; j++) {
				if (el == changes[j]) {
					if (el.instances) { //model
						if (el.rebuilded || el.newRebuilded) {
							build = true;
						} else {
							/*
							var t = el.nodesChanged.concat(depsN);
							t.sort();
							for (var k = 0, len2 = t.length-1; !build && k < len2; k++) {
								build = t[k] == t[k+1];
							}
							*/
							for (var k = 0, len2 = depsN.length; !build && k < len2; k++) {
								build = inArray(depsN[k], el.nodesChanged);
							}

							if (!build) {
								/*
								t = el.nodesChanged.concat(depsR);
								t.sort();
								for (var k = 0, len2 = t.length-1; !refresh && k < len2; k++) {
									refresh = t[k] == t[k+1];
								}
								*/
								for (var n = 0, len3 = depsR.length; n < len3; n++) {
									refresh = inArray(depsR[n], el.nodesChanged);
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
			for (var i = 0, len = depsN.length; i < len; i++) {
				Core.setFalseBoolMeta(depsN[i], "depfor_"+this.depsIdB);
			}
			depsN.length = 0;
			for (var i = 0, len = depsR.length; i < len; i++) {
				Core.setFalseBoolMeta(depsR[i], "depfor_"+this.depsIdR);
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

		

XFElement.prototype.evaluateBinding = function(binding, ctx) {
	var nodes = null;
	var errmsg = null;
	
	if (binding) {
		nodes = binding.evaluate(ctx, this.depsNodesBuild, this.depsIdB, this.depsElements);
		if (nodes != null) {
			return nodes;
		}
			
		// A 'null' binding means bind-ID was not found.
		errmsg = "non-existent bind-ID("+ binding.bind + ") on element(" + this.element.id + ")!";
	} else {
		errmsg = "no binding defined for element("+ this.element.id + ")!";
	}
	
	assert(errmsg);
	
	if (xforms.building && DebugMode) {
		//
		// Do not fail here, to keep on searching for more errors.
		
		xforms.bindErrMsgs.push(errmsg);
		XMLEvents.dispatch(this.element, "xforms-binding-exception");
		nodes = [];
	} else {
		xforms.error(this.element, "xforms-binding-exception", errmsg);
	}

	return nodes;
};

	
		
		
		
function XFControl() {
	this.isControl = true;
}

XFControl.prototype = new XFElement();


		

XFControl.prototype.initFocus = function(element, principal) {
	if (principal) {
		this.focusControl = element;
	}

	XSLTFormsEvent.attach(element, "focus", XFControl.focusHandler);
	XSLTFormsEvent.attach(element, "blur", XFControl.blurHandler);
};


		

XFControl.prototype.dispose = function() {
	this.focusControl = null;
	XFElement.prototype.dispose.call(this);
};


		

XFControl.prototype.focus = function(focusEvent) {
	if (this.isOutput) {
		return;
	}

	if (xforms.focus != this) {
		xforms.openAction();
		xforms.blur(true);
		xforms.focus = this;
		Core.setClass(this.element, "xforms-focus", true);
		Core.setClass(this.element, "xforms-disabled", false);
		var parent = this.element.parentNode;
	
		while (parent.nodeType == NodeType.ELEMENT) {
			if (typeof parent.node != "undefined"
				&& Core.hasClass(parent, "xforms-repeat-item")) {
				XFRepeat.selectItem(parent);
			}
	
			parent = parent.parentNode;
		}
		
		XMLEvents.dispatch(xforms.focus, "DOMFocusIn");
		xforms.closeAction();
		
		if (this.full && !focusEvent) { // select full
			this.focusFirst();
		}
	}

	var fcontrol = this.focusControl;
	xforms.posibleBlur = false;
	
	if (fcontrol && !focusEvent) {
		var control = this.focusControl;
		var name = control.nodeName.toLowerCase();
		control.focus();
		control.focus();

		if (name == "input" || name == "textarea") {
			control.select();
		}
	}
};


		

XFControl.prototype.build_ = function(ctx) {
	var result = this.evaluateBinding(this.binding, ctx);

	if (typeof result == "object") {
		var node = result[0];
		var element = this.element;
		var old = element.node;

		if (old != node || !xforms.ready) {
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


		

XFControl.prototype.refresh = function() {
	var element = this.element;
	var node = element.node;

	if (node) {
		var value = getValue(node, true, this.complex);
		xforms.openAction();
		var changed = value != this.currentValue || this.nodeChanged;
		
		if (this.relevant) {
			Core.setClass(element, "xforms-disabled", false);
		}

		this.changeProp(node, "required", "xforms-required", "xforms-optional", changed, value);
		this.changeProp(node, "notrelevant", "xforms-disabled", "xforms-enabled", changed, value);
		this.changeProp(node, "readonly", "xforms-readonly", "xforms-readwrite", changed, value);
		this.changeProp(node, "notvalid", "xforms-invalid", "xforms-valid", changed, value);

		if (changed) {
			this.currentValue = value;
			this.setValue(value);

			if (!this.nodeChanged && !this.isTrigger) {
				XMLEvents.dispatch(element, "xforms-value-changed");
			}
		}

		xforms.closeAction();
	} else if (this.outputValue != null) {
		this.setValue(this.outputValue);
		Core.setClass(element, "xforms-disabled", false);
    } else {
	Core.setClass(element, "xforms-disabled", !this.hasValue);
  }    
  this.nodeChanged = false;
};


		

XFControl.prototype.changeProp = function(node, prop, onTrue, onFalse, changed, nvalue) {
	var value = Core.getMeta(node, prop);

	if (changed || value != this[prop]) {
		if (!this.nodeChanged && !this.isTrigger) {
			XMLEvents.dispatch(this.element, (value? onTrue : onFalse));
		}

		this[prop] = value;
		if (prop == "notvalid" && nvalue == "") {
			value = false;
		}
		Core.setClass(this.element, onTrue, value);
		Core.setClass(this.element, onFalse, !value);
		
		if(prop == "readonly" && this.changeReadonly) {
			this.changeReadonly();
		}
	}	
};


		

XFControl.prototype.valueChanged = function(value) {
	var node = this.element.node;
	var model = document.getElementById(Core.getMeta(node.ownerDocument.documentElement, "model")).xfElement;
	var schtyp = Schema.getType(Core.getType(node) || "xsd_:string");

	if (value != null && value.length > 0 && schtyp.parse) {
		try { value = schtyp.parse(value); } catch(e) { }
	}
	if (value != getValue(node)) {
		xforms.openAction();
		setValue(node, value);
		model.addChange(node);
		//XMLEvents.dispatch(model, "xforms-recalculate");
		//xforms.refresh();
		xforms.closeAction();
	}
};


		

XFControl.getXFElement = function(element) {
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


		

XFControl.focusHandler = function() {
	var xf = XFControl.getXFElement(this);

	if (xforms.focus != xf) {
		xf.focus(true);
	} else {
		xforms.posibleBlur = false;
	}
};


		

XFControl.blurHandler = function() {
	if (XFControl.getXFElement(this) == xforms.focus) {
		xforms.posibleBlur = true;
		setTimeout("xforms.blur()", 200);
	}
};
    
	
		
		
		
function XFGroup(id, binding) {
	this.init(id);

	if (binding) {
		this.hasBinding = true;
		this.binding = binding;
	} else {
		Core.setClass(this.element, "xforms-disabled", false);
	}
}

XFGroup.prototype = new XFElement();


		

XFGroup.prototype.clone = function(id) { 
	return new XFGroup(id, this.binding);
};


		

XFGroup.prototype.build_ = function(ctx) {
	var nodes = this.evaluateBinding(this.binding, ctx);
	this.element.node = nodes[0];
	this.depsNodesRefresh.push(nodes[0]);
};


		

XFGroup.prototype.refresh = function() {
	var element = this.element;
	var disabled = !element.node || Core.getBoolMeta(element.node, "notrelevant");
	Core.setClass(element, "xforms-disabled", disabled);

	/** Tabs */
	var ul = element.parentNode.firstChild;
	
	if (ul.nodeName.toLowerCase() == "ul") {
		var childs = element.parentNode.childNodes;
		var tab;

		for (var i = 1, len = childs.length; i < len; i++) {
			if (childs[i] == element) {
				tab = ul.childNodes[i - 1];
			}
		}

		Core.setClass(tab, "xforms-disabled", disabled);
	}
};
    
	
		
		
		
function XFInput(id, itype, binding, inputmode, incremental, delay, aidButton, clone) {
	this.init(id);
	this.binding = binding;
	this.inputmode = typeof inputmode == "string"? XFInput.InputMode[inputmode] : inputmode;
	this.incremental = incremental;
	this.delay = delay;
	this.timer = null;
	var cells = this.element.firstChild.firstChild.childNodes;
	this.cell = cells[cells.length - 2];
	this.isClone = clone;
	this.hasBinding = true;
	this.type;  // ???
	this.itype = itype;
	this.bolAidButton = aidButton;
	for (; this.cell.firstChild.nodeType == NodeType.TEXT; this.cell.removeChild(this.cell.firstChild)) {}

	this.initFocus(this.cell.firstChild, true);

	if (aidButton) {
		this.aidButton = cells[cells.length - 1].firstChild;
		this.initFocus(this.aidButton);
	}
}

XFInput.prototype = new XFControl();


		

XFInput.prototype.clone = function(id) { 
	return new XFInput(id, this.itype, this.binding, this.inputmode, this.incremental, this.delay, this.bolAidButton, true);
};


		

XFInput.prototype.dispose = function() {
	this.cell = null;
	this.calendarButton = null;
	XFControl.prototype.dispose.call(this);
};


		

XFInput.prototype.initInput = function(type) {
	var cell = this.cell;
	var input = cell.firstChild;
	var clase = type["class"];

	if (input.type == "password") {
		this.type = Schema.getType("xsd_:string");
		this.initEvents(input, true);
	} else if (input.nodeName.toLowerCase() == "textarea") {
		this.type = Schema.getType("xsd_:string");
		this.initEvents(input, false);
	} else if (type != this.type) {
		this.type = type;

		if (clase == "boolean" || this.itype != input.type) {
			for (; cell.firstChild; cell.removeChild(cell.firstChild)) {}
		} else {
			for (var i = cell.childNodes.length - 1; i >= 1; i--) {
				cell.removeChild(cell.childNodes[i]);
			}
		}

		if (clase == "boolean") {
			input = Core.createElement("input");
			input.type = "checkbox";
			cell.appendChild(input);
		} else {
			if(this.itype != input.type) {
				input = Core.createElement("input", cell, null, "xforms-value");
			}
			this.initEvents(input, (this.itype=="text"));

			if (clase == "date" || clase == "datetime") {
				this.calendarButton = Core.createElement("button", cell, "...", "aid-button");
				this.initFocus(this.calendarButton);
			} else if (clase == "number") {
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


		

XFInput.prototype.setValue = function(value) {
	var node = this.element.node;
	var type = node ? Schema.getType(Core.getType(node) || "xsd_:string") : Schema.getType("xsd_:string");
	if (!this.input || type != this.type) {
		this.initInput(type);
		this.changeReadonly();
	}

	if (type["class"] == "boolean") {
		this.input.checked = value == "true";
	} else if (this.input.value != value) { // && this != xforms.focus) {
		this.input.value = value || "";
	}
};


		

XFInput.prototype.changeReadonly = function() {
	if (this.input) {
		this.input.readOnly = this.readonly;

		if (this.calendarButton) {
			this.calendarButton.style.display = this.readonly? "none" : "";
		}
	}
};


		

XFInput.prototype.initEvents = function(input, canActivate) {
	if (this.inputmode) {
		XSLTFormsEvent.attach(input, "keyup", XFInput.keyUpInputMode);
	}
	if (canActivate) {
		if (this.incremental) {
			XSLTFormsEvent.attach(input, "keyup", XFInput.keyUpIncrementalActivate);
		} else {
			XSLTFormsEvent.attach(input, "keyup", XFInput.keyUpActivate);
		}
	} else {
		if (this.incremental) {
			XSLTFormsEvent.attach(input, "keyup", XFInput.keyUpIncremental);
		}
	}
};


		

XFInput.prototype.blur = function(target) {
	xforms.focus = null;
	var input = this.input;
	if (!this.incremental) {
		assert(input, this.element.id);
		var value = input.type == "checkbox"? (input.checked? "true" : "false") : input.value;
		this.valueChanged(value);
	} else {
		var node = this.element.node;
		var value = input.value;
		if (value != null && value.length > 0 && Schema.getType(Core.getType(node) || "xsd_:string").format) {
			try { input.value = getValue(node, true); } catch(e) { }
		}
		if (this.timer) {
			window.clearTimeout(this.timer);
			this.timer = null;
		}
	}
};


		

XFInput.prototype.click = function(target) {
	if (target == this.aidButton) {
		xforms.openAction();
		XMLEvents.dispatch(this, "ajx-aid");
		xforms.closeAction();
	} else if (target == this.input && this.type["class"] == "boolean") {
		xforms.openAction();
		this.valueChanged(target.checked? "true" : "false");
		XMLEvents.dispatch(this, "DOMActivate");
		xforms.closeAction();
	} else if (target == this.calendarButton) {
		Calendar.show(target.previousSibling, this.type["class"] == "datetime"? Calendar.SECONDS : Calendar.ONLY_DATE);
	}
};


		

XFInput.keyUpInputMode = function() {
	var xf = XFControl.getXFElement(this);
	this.value = xf.inputmode(this.value);
};


		

XFInput.keyUpActivate = function(a) {
	var xf = XFControl.getXFElement(this);
	if (a.keyCode == 13) {
		xforms.openAction();
		xf.valueChanged(this.value || "");
		XMLEvents.dispatch(xf, "DOMActivate");
		xforms.closeAction();
	}
};


		

XFInput.keyUpIncrementalActivate = function(a) {
	var xf = XFControl.getXFElement(this);
	if (a.keyCode == 13) {
		xforms.openAction();
		xf.valueChanged(this.value || "");
		XMLEvents.dispatch(xf, "DOMActivate");
		xforms.closeAction();
	} else {
		if (xf.delay && xf.delay > 0) {
			if (xf.timer) {
				window.clearTimeout(xf.timer);
			}
			xf.timer = window.setTimeout("xforms.openAction();document.getElementById('" + xf.element.id + "').xfElement.valueChanged('" + (this.value.addslashes() || "") + "');xforms.closeAction();", xf.delay);
		} else {
			xforms.openAction();
			xf.valueChanged(this.value || "");
			xforms.closeAction();
		}
	}
};


		

XFInput.keyUpIncremental = function() {
	var xf = XFControl.getXFElement(this);
	if (xf.delay && xf.delay > 0) {
		if (xf.timer) {
			window.clearTimeout(xf.timer);
		}
		xf.timer = window.setTimeout("xforms.openAction();document.getElementById('" + xf.element.id + "').xfElement.valueChanged('" + (this.value.addslashes() || "") + "');xforms.closeAction();", xf.delay);
	} else {
		xforms.openAction();
		xf.valueChanged(this.value || "");
		xforms.closeAction();
	}
};


		

XFInput.InputMode = {
	lowerCase : function(value) { return value.toLowerCase(); },
	upperCase : function(value) { return value.toUpperCase(); },
	titleCase : function(value) {
		return value.charAt(0).toUpperCase() + value.substring(1).toLowerCase();
	},
	digits : function(value) {
		if (/^[0-9]*$/.exec(value) != null) {
			return value;
		} else {
			alert("Character not valid");
			var digits = "1234567890";
			var newValue = "";

			for (var i = 0, len = value.length; i < len; i++) {
				if (digits.indexOf(value.charAt(i)) != -1) {
					newValue += value.charAt(i);
				}
			}

			return newValue;
		}
	}
};
    
	
		
		
		
function XFItem(id, bindingL, bindingV) {
	this.init(id);

	if (bindingL || bindingV) {
		this.hasBinding = true;
		this.bindingL = bindingL;
		this.bindingV = bindingV;
	} else {
		Core.setClass(this.element, "xforms-disabled", false);
	}

	var element = this.element;

	if (element.nodeName.toLowerCase() != "option") {
		this.input = Core.isXhtml ? element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input")[0] : element.getElementsByTagName("input")[0];
		this.input.name = XFControl.getXFElement(this.element).element.id;
		XSLTFormsEvent.attach(this.input, "focus", XFControl.focusHandler);
		XSLTFormsEvent.attach(this.input, "blur", XFControl.blurHandler);
		this.label = Core.isXhtml ? element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "span")[0] : element.getElementsByTagName("span")[0];
	}
}

XFItem.prototype = new XFElement();


		

XFItem.prototype.clone = function(id) { 
	return new XFItem(id, this.bindingL, this.bindingV);
};


		

XFItem.prototype.dispose = function() {
	this.input = null;
	this.label = null;
	XFElement.prototype.dispose.call(this);
};


		

XFItem.prototype.build_ = function(ctx) {
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


		

XFItem.prototype.refresh = function() {
	var element = this.element;

	if (element.nodeName.toLowerCase() == "option") {
		if (element.nodeL) {
			try { element.text = getValue(element.nodeL, true); } catch(e) { }
		}

		if (element.nodeV) {
			try { element.value = getValue(element.nodeV); } catch(e2) { }
		}
	} else {
		if (element.nodeL) {
			setValue(this.label, getValue(element.nodeL, true));
		}

		if (element.nodeV) {
			this.input.value = getValue(element.nodeV);
		}
	}
};


		

XFItem.prototype.click = function (target) {
	var input = this.input;

	if (input) {
		var xf = XFControl.getXFElement(this.element);
		
		if (!xf.element.node.readonly && target == input) {
/*		
			if (target != input) {
				if (input.type != "radio" || !input.checked) {
					input.checked = !input.checked;
					input.focus();
				}
			}
*/

			xf.itemClick(input.value);
		}
	}
};
    
	
		
		
		
function XFItemset(id, nodesetBinding, labelBinding, valueBinding) {
	this.init(id);
	this.nodesetBinding = nodesetBinding;
	this.labelBinding = labelBinding;
	this.valueBinding = valueBinding;
	this.hasBinding = true;
}

XFItemset.prototype = new XFElement();


		

XFItemset.prototype.build_ = function(ctx) {
	if (this.element.getAttribute("cloned")) { return; }

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
				IdManager.cloneId(node);
				node.node = this.nodes[i];
				parentNode.appendChild(node);
				this.refresh_(node, i);
			
				if (listeners && !Core.isIE) {
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


		

XFItemset.prototype.refresh = function() {
	var parent = this.element.parentNode;
	var i;
	for (i = 0; parent.childNodes[i] != this.element; i++);
	for (var j = 0, len = this.nodes.length; j < len || j == 0; j++) {
		Core.setClass(parent.childNodes[i+j], "xforms-disabled", this.nodes.length === 0);
	}
};


		

XFItemset.prototype.clone = function(id) {
	return new XFItemset(id, this.nodesetBinding, this.labelBinding, this.valueBinding);
};


		

XFItemset.prototype.refresh_ = function(element, cont) {
	var ctx = this.nodes[cont];
	var nodeLabel = this.evaluateBinding(this.labelBinding, ctx)[0];
	var nodeValue = this.evaluateBinding(this.valueBinding, ctx)[0];

	if (nodeLabel) {
		this.depsNodesRefresh.push(nodeLabel);

	    try { element.text = getValue(nodeLabel, true); } catch(e) { }
	}

	if (nodeValue) {
		this.depsNodesRefresh.push(nodeValue);
	    try { element.value = getValue(nodeValue); } catch(e2) { }
	}
};
    
	
		
		
		
function XFLabel(id, binding) {
	this.init(id);

	if (binding) {
		this.hasBinding = true;
		this.binding = binding;
	}
}

XFLabel.prototype = new XFElement();


		

XFLabel.prototype.clone = function(id) { 
	return new XFLabel(id, this.binding);
};


		

XFLabel.prototype.build_ = function(ctx) {
	var nodes = this.evaluateBinding(this.binding, ctx);
	this.element.node = nodes[0];
	this.depsNodesRefresh.push(nodes[0]);
};


		

XFLabel.prototype.refresh = function() {
	var node = this.element.node;
	var value = node? getValue(node, true) : "";
   setValue(this.element, value);
};
    
	
		
		
		
function XFOutput(id, binding, mediatype) {
	this.init(id);

	if (this.element.firstChild.firstChild) {
		var cells = this.element.firstChild.firstChild.childNodes;
		this.valueElement = cells[cells.length - 2];
	} else {
		this.valueElement = this.element;
	}
	for (var i=0, len = this.valueElement.childNodes.length; i<len; i++) {
		if( this.valueElement.childNodes[i].nodeType != NodeType.TEXT ) {
			this.valueElement = this.valueElement.childNodes[i];
			break;
		}
	}
	
	this.hasBinding = true;
	this.binding = binding;
	this.mediatype = mediatype;
	this.complex = mediatype == "application/xhtml+xml";
	this.isOutput = true;
	if (this.binding && this.binding.isvalue) {
		Core.setClass(this.element, "xforms-disabled", false);
	}
}

XFOutput.prototype = new XFControl();


		

XFOutput.prototype.clone = function(id) { 
	return new XFOutput(id, this.binding, this.mediatype);
};


		

XFOutput.prototype.dispose = function() {
	this.valueElement = null;
	XFControl.prototype.dispose.call(this);
};


		

XFOutput.prototype.setValue = function(value) {
	var node = this.element.node;
	var element = this.valueElement;

    if (element.nodeName.toLowerCase() == "span") {
			if (this.mediatype == "application/xhtml+xml") {
				while (element.firstChild) {
					element.removeChild(element.firstChild);
				}
				if (value != null) {
					element.innerHTML = value;
				}
			} else if (this.mediatype == "image/svg+xml") {
				while (element.firstChild) {
					element.removeChild(element.firstChild);
				}
				if (Core.isIE) {
					var xamlScript = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "script") : document.createElement("script");
					xamlScript.setAttribute("type", "text/xaml");
					xamlScript.setAttribute("id", this.element.id+"-xaml");
					xamlScript.text = Core.transformText(value, Core.ROOT + "svg2xaml.xsl", false, "width", element.currentStyle.width, "height", element.currentStyle.height);
					element.appendChild(xamlScript);
					var xamlObject = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "object") : document.createElement("object");
					xamlObject.setAttribute("width", element.currentStyle.width+"px");
					xamlObject.setAttribute("height", element.currentStyle.height+"px");
					xamlObject.setAttribute("type", "application/x-silverlight");
					xamlObject.setAttribute("style", "min-width: " + element.currentStyle.width+"px");
					//xamlObject.setAttribute("style", "min-width: " + xamlScript.text.substring(xamlScript.text.indexOf('<Canvas Width="')+15,xamlScript.text.indexOf('" Height="')) + "px");
					var xamlParamSource = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "param") : document.createElement("param");
					xamlParamSource.setAttribute("name", "source");
					xamlParamSource.setAttribute("value", "#"+this.element.id+"-xaml");
					xamlObject.appendChild(xamlParamSource);
					var xamlParamOnload = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "param") : document.createElement("param");
					xamlParamOnload.setAttribute("name", "onload");
					xamlParamOnload.setAttribute("value", "onLoaded");
					xamlObject.appendChild(xamlParamOnload);
					var xamlParamIswindowless = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "param") : document.createElement("param");
					xamlParamIswindowless.setAttribute("name", "iswindowless");
					xamlParamIswindowless.setAttribute("value", "true");
					xamlObject.appendChild(xamlParamIswindowless);
					element.appendChild(xamlObject);
				} else if (Core.isXhtml) {
					var cs = window.getComputedStyle(element, null);
					XDocument.parse(value, element);
					element.firstChild.setAttribute("width", cs.getPropertyValue("min-width"));
					element.firstChild.setAttribute("height", cs.getPropertyValue("min-height"));
				} else {
					var svgObject = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", "object") : document.createElement("object");
					svgObject.setAttribute("type", "image/svg+xml");
					svgObject.setAttribute("data", "data:image/svg+xml,"+ value);
					//svgObject.setAttribute("height", "400px");
					element.appendChild(svgObject);
				}
			} else {
        setValue(element, value);
			}
    } else {
        element.src = value;
    }
};

		

XFOutput.prototype.getValue = function(format) {
	var node = this.element.node;
	var element = this.valueElement;

    if (element.nodeName.toLowerCase() == "span") {
        return getValue(element, format);
    } else {
			value = element.src;
			if (value && format && element.type.format) {
				try { value = element.type.format(value); } catch(e) { }
			}
			return value;
    }
};
    
	
		
		
		
function XFRepeat(id, binding, clone) {
	this.init(id);
	this.binding = binding;
	this.index = 1;
	var el = this.element;
	this.isRepeat = true;
	this.hasBinding = true;
	this.root = Core.hasClass(el, "xforms-control")? el.lastChild : el;
	this.isItemset = Core.hasClass(el, "xforms-itemset");
}

XFRepeat.prototype = new XFElement();


		

XFRepeat.prototype.dispose = function() {
	this.root = null;
	XFElement.prototype.dispose.call(this);
};


		

XFRepeat.prototype.setIndex = function(index) {
	if (this.index != index) {
		var node = this.nodes[index - 1];
        
    if (node) {    
			xforms.openAction();
			this.index = index;
			this.element.node = node;
			xforms.addChange(this);
			xforms.addChange(document.getElementById(Core.getMeta(node.ownerDocument.documentElement, "model")).xfElement);
			xforms.closeAction();
		}
	}
};


		

XFRepeat.prototype.deleteNode = function(node) {
	var newNodes = [];
	var nodes = this.nodes;
	
	for (var i = 0, len = nodes.length; i < len; i++) {
		if (node != nodes[i]) {
			newNodes.push(nodes[i]);
		}
	}

	this.nodes = newNodes;
	this.setIndex(this.index == nodes.length? this.index - 1 : this.index);
};


		

XFRepeat.prototype.insertNode = function(node, nodeAfter) {
	var nodes = this.nodes;

	if (nodeAfter) {
		var newNodes = [];
		var index = 1;

		for (var i = 0, len = nodes.length; i < len; i++) {
			if (nodeAfter == nodes[i]) {
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


		

XFRepeat.prototype.build_ = function(ctx) {
	var nodes = this.evaluateBinding(this.binding, ctx);
	var r = this.root;
	var l = r.childNodes.length;
	this.nodes = nodes;
	var n = nodes.length;

	XFRepeat.forceOldId(r.firstChild);
	for (var i = l; i < n; i++) {
		var child = r.firstChild.cloneNode(true);
		r.appendChild(child);
		XFRepeat.initClone(child);
	}

	for (var j = n; j < l && r.childNodes.length > 1; j++) {
		xforms.dispose(r.lastChild);
		r.removeChild(r.lastChild);
	}

	for (var k = 0; k < n; k++) {
		Core.setMeta(nodes[k], "repeat", this.element.id);
		r.childNodes[k].node = nodes[k];
	}

	if (this.index > n) {
		this.index = 1;
	}
    
	this.element.node = nodes[this.index - 1];
};


		

XFRepeat.prototype.refresh = function(selected) {
	var empty = this.nodes.length === 0;
	Core.setClass(this.element, "xforms-disabled", empty);

	if (!empty && !this.isItemset) {
		var childs = this.root.childNodes;

		for (var i = 0, len = childs.length; i < len; i++) {
			var sel = selected && (this.index == i + 1);
			childs[i].selected = sel;
			Core.setClass(childs[i], "xforms-repeat-item-selected", sel);
		}
	}
};


		

XFRepeat.prototype.clone = function(id) { 
	return new XFRepeat(id, this.binding, true);
};


		

XFRepeat.initClone = function(element) {
	var id = element.id;

	if (id) {
		IdManager.cloneId(element);
		var oldid = element.getAttribute("oldid");
		var original = document.getElementById(oldid);
		var xf = original.xfElement;

		if (xf) {
			xf.clone(element.id);
		}
		
		var listeners = original.listeners;
	
		if (listeners && !Core.isIE) {
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
			XFRepeat.initClone(child);
		}
	}
};


		

XFRepeat.forceOldId = function(element) {
	var id = element.id;

	if (id) {
		if (id.substr(0, 8) == "clonedId") {
			return;
		}
		element.setAttribute("oldid", id);
	}

	var next = element.firstChild;
	
	while (next) {
		var child = next;
		next = next.nextSibling;
		XFRepeat.forceOldId(child);
	}
};


		

XFRepeat.selectItem = function(element) {
	var par = element.parentNode;

	if (par) {
		var repeat = par.xfElement? par : par.parentNode;
		var childs = par.childNodes;
		assert(repeat.xfElement, element.nodeName +  " - " + repeat.nodeName);
		
		for (var i = 0, len = childs.length; i < len; i++) {
			if (childs[i] == element) {
				repeat.xfElement.setIndex(i + 1);
				break;
			}
		}
	}
};
    
	
		
		
		
function XFSelect(id, multiple, full, binding, incremental, clone) {
	this.init(id);
	this.binding = binding;
	this.multiple = multiple;
	this.full = full;
	this.incremental = incremental;
	this.isClone = clone;
	this.hasBinding = true;
    
	if (!this.full) {
		this.select = Core.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "select")[0] : this.element.getElementsByTagName("select")[0];
		this.initFocus(this.select);

		XSLTFormsEvent.attach(this.select, "change",
			incremental? XFSelect.incrementalChange : XFSelect.normalChange);
	}
}

XFSelect.prototype = new XFControl();


		

XFSelect.prototype.clone = function(id) { 
	return new XFSelect(id, this.multiple, this.full, this.binding, this.incremental, true);
};


		

XFSelect.prototype.dispose = function() {
	this.select = null;
	this.selectedOptions = null;
	XFControl.prototype.dispose.call(this);
};


		

XFSelect.prototype.focusFirst = function() {
	var input = Core.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input")[0] : this.element.getElementsByTagName("input")[0];
	input.focus();

	if (Core.isOpera) {
		input.focus();
	}
};


		

XFSelect.prototype.setValue = function(value) {
	if (!this.full && (!value || value == "")) {
		this.selectedOptions = [];
		if (this.select.firstChild.value != "\xA0") {
			var empty = this.select.options[0].cloneNode(true);
			empty.value = "\xA0";
			empty.text = "\xA0";
			empty.id = "";
			this.select.insertBefore(empty, this.select.options[0]);
			this.select.selectedIndex = 0;
		}
	} else {
		if (!this.full && this.select.firstChild.value == "\xA0") {
			this.select.removeChild(this.select.firstChild);
		}
		var vals = value? (this.multiple? value.split(" ") : [value]) : [""];
		var list = this.full? (Core.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input") : this.element.getElementsByTagName("input")) : this.select.options;
		var well = true;
			
		for (var i = 0, len = vals.length; well && i < len; i++) {
			var val = vals[i];
			var finded = false;
							
			for (var j = 0, len1 = list.length; !finded && j < len1; j++) {
				if (list[j].value == val) {
					finded = true;
				}
			}

			well = finded;
		}

		if (well || (this.multiple && !value)) {
			if (this.outRange) {
				this.outRange = false;
				XMLEvents.dispatch(this, "xforms-in-range");
			}
		} else if ((!this.multiple || value) && !this.outRange) {
			this.outRange = true;
			XMLEvents.dispatch(this, "xforms-out-of-range");
		}

		vals = this.multiple? vals : [vals[0]];
		var readonly = this.element.node.readonly;

		if (this.full) {
			for (var n = 0, len2 = list.length; n < len2; n++) {
				var item = list[n];
				item.checked = inArray(item.value, vals);
			}
		} else {
			this.selectedOptions = [];
			for (var k = 0, len3 = list.length; k < len3; k++) {
				var item = list[k];
				var b = inArray(item.value, vals);
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


		

XFSelect.prototype.changeReadonly = function() {
	if (this.full) {
		var list = Core.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input") : this.element.getElementsByTagName("input");

		for (var i = 0, len = list.length; i < len; i++) {
			list[i].disabled = this.readonly;
		}
	} else {
		if (!Dialog.knownSelect(this.select)) {
			this.select.disabled = this.readonly;
		}
	}
};


		

XFSelect.prototype.itemClick = function(value) {
	var inputs = Core.isXhtml ? this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "input") : this.element.getElementsByTagName("input");
	xforms.openAction();

	if (this.multiple) {
		var newValue = null;
		
		for (var i = 0, len = inputs.length; i < len; i++) {
			var input = inputs[i];

			if (input.value == value) {
				XMLEvents.dispatch(input.parentNode, input.checked? "xforms-select" : "xforms-deselect");
			}
			
            if (input.checked) {
                newValue = (newValue? newValue + " " : "") + input.value;
            }
		}

		value = newValue;
	} else {
		var old = this.value || getValue(this.element.node);
		var inputSelected = null;

		if (old == value) {
			xforms.closeAction();
			return;
		}

		for (var j = 0, len1 = inputs.length; j < len1; j++) {
			var input = inputs[j];
			input.checked = input.value == value;
			
			if (input.value == old) {
				XMLEvents.dispatch(input.parentNode, "xforms-deselect");
			} else if (input.value == value) {
				var inputSelected = input;
			}
		}
		
		XMLEvents.dispatch(inputSelected.parentNode, "xforms-select");
	}

	if (this.incremental) {
		this.valueChanged(value || "");
	} else {
		this.value = value || "";
	}
	
	xforms.closeAction();
};


		

XFSelect.prototype.blur = function(evt) {
	if (this.value != null) {
		xforms.openAction();
		this.valueChanged(this.value);
		xforms.closeAction();
		this.value = null;
	}
};


		

XFSelect.normalChange = function(evt) {
	var xf = XFControl.getXFElement(this);
	var news = [];
	var value = "";
	var old = xf.getSelected();
	var opts = this.options;
	xforms.openAction();
	
	for (var i = 0, len = old.length; i < len; i++) {
		if (old[i].selected) {
			news.push(old[i]);
		} else {
			XMLEvents.dispatch(old[i], "xforms-deselect");
		}
	}
	
	for (var j = 0, len1 = opts.length; j < len1; j++) {
		var opt = opts[j];
		if (opt.selected) {
			value = value? value + " " + opt.value : opt.value;
		}
	}
	
	for (var j = 0, len1 = opts.length; j < len1; j++) {
		var opt = opts[j];	    
		if (opt.selected) {
			if (!inArray(opt, news)) {
				news.push(opt);
				XMLEvents.dispatch(opt, "xforms-select");
			}
		}
	}

	xf.value = value;
	xf.selectedOptions = news;
	xforms.closeAction();
};


		

XFSelect.incrementalChange = function(evt) {
	var xf = XFControl.getXFElement(this);
	xforms.openAction();
	XFSelect.normalChange.call(this, evt);
	xf.valueChanged(xf.value);
	xforms.closeAction();
};


		

XFSelect.prototype.getSelected = function() {
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
    
	
		
		
		
function XFTrigger(id, binding, clone) {
	this.init(id);
	this.binding = binding;
	this.hasBinding = !!binding;
	if(!this.hasBinding) {
		Core.setClass(this.element, "xforms-disabled", false);
	}
	this.isTrigger = true;
	var button = Core.isXhtml ? (this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "a")[0] || this.element.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "button")[0]) : (this.element.getElementsByTagName("a")[0] || this.element.getElementsByTagName("button")[0]);
	this.initFocus(button);
}

XFTrigger.prototype = new XFControl();


		

XFTrigger.prototype.setValue = function () { };


		

XFTrigger.prototype.clone = function (id) {
	return new XFTrigger(id, this.binding, true);
};


		

XFTrigger.prototype.click = function () {
	xforms.openAction();
	XMLEvents.dispatch(this, "DOMActivate");
	xforms.closeAction();
};


		

XFTrigger.prototype.blur = function () { };
    
	
		
		
		
function Calendar() {
    var calendar = this;
    var body = Core.isXhtml ? document.getElementsByTagNameNS("http://www.w3.org/1999/xhtml", "body")[0] : document.getElementsByTagName("body")[0];
    this.element = Core.createElement("table", body, null, "calendar");

    var tHead = Core.createElement("thead", this.element);
    var trTitle = Core.createElement("tr", tHead);
    var title = Core.createElement("td", trTitle, null, "title");
    title.colSpan = 7;

    this.selectMonth = Core.createElement("select", title);
    XSLTFormsEvent.attach(this.selectMonth, "change", function() {
        Calendar.INSTANCE.refresh();
    } );

    for (var i = 0; i < 12; i++) {
			var o = Core.createElement("option", this.selectMonth, I8N.get("calendar.month" + i));
			o.setAttribute("value", i);
    }

    this.inputYear = Core.createElement("input", title);
    this.inputYear.readOnly = true;
    XSLTFormsEvent.attach(this.inputYear, "mouseup", function() {
        var cal = Calendar.INSTANCE;
        cal.yearList.show();
    } );
    XSLTFormsEvent.attach(this.inputYear, "change", function() {
        Calendar.INSTANCE.refresh();
    } );

    var close = Core.createElement("button", title, "X");
    close.setAttribute("title", "Close");

    XSLTFormsEvent.attach(close, "click", function() {
        Calendar.close();
    } );

    var trDays = Core.createElement("tr", tHead, null, "names");
    var ini = parseInt(I8N.get("calendar.initDay"), 10);

    for (var j = 0; j < 7; j++) {
    	var ind = (j + ini) % 7;
        this.createElement(trDays, "name", I8N.get("calendar.day" + ind));
    }

    this.tBody = Core.createElement("tbody", this.element);

    var handler = function(event) {
        var value = XSLTFormsEvent.getTarget(event).childNodes[0].nodeValue;
        var cal = Calendar.INSTANCE;

        if (value != "") {
	        cal.day = value;
    	    var date = new Date();
        	date.setYear(cal.inputYear.value);
	        date.setMonth(cal.selectMonth.value);
    	    date.setDate(cal.day);
    	    
            if (cal.isTimestamp) {
							date.setSeconds(cal.inputSec.value);
							date.setMinutes(cal.inputMin.value);
							date.setHours(cal.inputHour.value);
							cal.input.value = I8N.format(date, null, true);
            } else {
              cal.input.value = I8N.formatDate(date);
            }

	        Calendar.close();
    	    XSLTFormsEvent.dispatch(cal.input, "keyup");
    	    cal.input.focus();
    	}
    };

    for (var dtr = 0; dtr < 6; dtr++) {
        var trLine = Core.createElement("tr", this.tBody);

        for (var day = 0; day < 7; day++) {
            this.createElement(trLine, "day", " ", 1, handler);
        }
    }
    
    var tFoot = Core.createElement("tfoot", this.element);
    var trFoot = Core.createElement("tr", tFoot);
    var tdFoot = Core.createElement("td", trFoot);
   	tdFoot.colSpan = 7;
   	
    this.inputHour = Core.createElement("input", tdFoot);
    this.inputHour.readOnly = true;
    XSLTFormsEvent.attach(this.inputHour, "mouseup", function() {
        Calendar.INSTANCE.hourList.show();
    } );
    
    tdFoot.appendChild(document.createTextNode(":"));
    this.inputMin = Core.createElement("input", tdFoot);
    this.inputMin.readOnly = true;
    XSLTFormsEvent.attach(this.inputMin, "mouseup", function() {
        Calendar.INSTANCE.minList.show();
    } );

    tdFoot.appendChild(document.createTextNode(":"));
    this.inputSec = Core.createElement("input", tdFoot);
    this.inputSec.readOnly = true;
    XSLTFormsEvent.attach(this.inputSec, "mouseup", function() {
    	if (Calendar.INSTANCE.type >= Calendar.SECONDS) {
	        Calendar.INSTANCE.secList.show();
	    }
    } );

    this.yearList = new NumberList(title, "calendarList", this.inputYear, 1900, 2050);
    this.hourList = new NumberList(tdFoot, "calendarList", this.inputHour, 0, 23, 2);
    this.minList = new NumberList(tdFoot, "calendarList", this.inputMin, 0, 59, 2);
    this.secList = new NumberList(tdFoot, "calendarList", this.inputSec, 0, 59, 2);
}


		

Calendar.prototype.today = function() {
	this.refreshControls(new Date());
};


		

Calendar.prototype.refreshControls = function(date) {
    this.day = date.getDate();
    this.selectMonth.value = date.getMonth();
    this.inputYear.value = date.getYear() < 1000? 1900 + date.getYear() : date.getYear();

    if (this.isTimestamp) {
	    this.inputHour.value = zeros(date.getHours(), 2);
    	this.inputMin.value = this.type >= Calendar.MINUTES? zeros(date.getMinutes(), 2) : "00";
    	this.inputSec.value = this.type >= Calendar.SECONDS? zeros(date.getSeconds(), 2) : "00";
   	}
   	
   	this.refresh();
};


		

Calendar.prototype.refresh = function() {
    var firstDay = this.getFirstDay();
    var daysOfMonth = this.getDaysOfMonth();
		var ini = parseInt(I8N.get("calendar.initDay"), 10);
    var cont = 0;
    var day = 1;
    var currentMonthYear = this.selectMonth.value == this.currentMonth
        && this.inputYear.value == this.currentYear;

    for (var i = 0; i < 6; i++) {
        var trLine = this.tBody.childNodes[i];

        for (var j = 0; j < 7; j++, cont++) {
            var cell = trLine.childNodes[j];
						var dayInMonth = (cont >= firstDay && cont < firstDay + daysOfMonth);
            Core.setClass(cell, "hover", false);
            Core.setClass(cell, "today", currentMonthYear && day == this.currentDay);
            Core.setClass(cell, "selected", dayInMonth && day == this.day);
            Core.setClass(cell, "weekend", (j+ini)%7 > 4);

            cell.firstChild.nodeValue = dayInMonth ? day++ : "";
        }
    }
};


		

Calendar.prototype.getFirstDay = function() {
   var date = new Date();
   date.setDate(1);
   date.setMonth(this.selectMonth.value);
   date.setYear(this.inputYear.value);
	 var ini = parseInt(I8N.get("calendar.initDay"), 10);
	 var d = date.getDay();
	 return (d + (6 - ini)) % 7;
};


		

Calendar.prototype.getDaysOfMonth = function() {
	var year = this.inputYear.value;
	var month = this.selectMonth.value;

	if (month == 1 && ((0 === (year % 4)) && (   (0 !== (year % 100))
	                                          || (0 === (year % 400))))) {
		return 29;
	}

    return Calendar.daysOfMonth[this.selectMonth.value];
};


		

Calendar.prototype.createElement = function(parent, className, text, colspan, handler) {
    var element = Core.createElement("td", parent, text, className);

    if (colspan > 1) {
        element.colSpan = colspan;
    }
    
    if (handler) {
        XSLTFormsEvent.attach(element, "click", handler);
        Core.initHover(element);
    }

    return element;
};

Calendar.daysOfMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

Calendar.ONLY_DATE = 0;
Calendar.HOURS = 1;
Calendar.MINUTES = 2;
Calendar.SECONDS = 3;


		

Calendar.show = function(input, type) {
    var cal = Calendar.INSTANCE;

    if (!cal) {
        cal = new Calendar();
        Calendar.INSTANCE = cal;
    }

	if (!type) {
		type = Calendar.ONLY_DATE;
	}

    cal.input = input;
    cal.type = type;
    cal.isTimestamp = type != Calendar.ONLY_DATE;
    Core.setClass(cal.element, "date", !cal.isTimestamp);
    var date;
    
    try {
        date = cal.isTimestamp? I8N.parse(input.value) : I8N.parseDate(input.value);
    } catch (e) { date = new Date(); }

    if (date != null) {
	    cal.refreshControls(date);
    } else {
        cal.today();
    }
    
    Dialog.show(cal.element, input, false);
};


		

Calendar.close = function() {
    var cal = Calendar.INSTANCE;
    cal.yearList.close();
    Dialog.hide(cal.element, false);
};
    
	
	
		
		
		
		
		
function Type() {
}


		

Type.prototype.setSchema = function(schema) {
	this.schema = schema;
	return this;
};


		

Type.prototype.setName = function(name) {
	this.name = name;
	this.nsuri = this.schema.ns;
	this.schema.types[name] = this;
	return this;
};


		

Type.prototype.canonicalValue = function(value) {
	value = value.toString();

	switch (this.whiteSpace) {
		case "replace": value = value.replace(/[\t\r\n]/g, " "); break;
		case "collapse": value = value.replace(/[\t\r\n ]+/g, " ").replace(/^\s+|\s+$/g, ""); break;
	}

	return value;
};


		

Type.prototype.getMaxLength = function() {
	return this.maxLength != null? this.maxLength 
		: (this.length != null? this.length
			: (this.totalDigits != null? this.totalDigits + 1 : null));
};


		

Type.prototype.getDisplayLength = function() {
	return this.displayLength;
};
    
	
		
		
		
function Schema(ns, name, prefixes) {
	assert(ns && !Schema.all[ns], "Needed schema name or exists one schema with that namespace");
	if(Schema.all[ns]) {
		xforms.error(this, "xforms-link-exception", "More than one schema with the same namespace declaration");
		return;
	}
	this.name = name;
	this.ns = ns;
	this.types = {};
	this.prefixes = prefixes || {};
	Schema.all[ns] = this;
}


		

Schema.all = {};


		

Schema.prototype.getType = function(name) {
	if (name.indexOf(":") != -1) {
		var res = name.split(":");
		var prefix = res[0];
		var ns = this.prefixes[prefix];

		if (ns) {
			return Schema.getTypeNS(ns, res[1]);
		}

		return Schema.getType(name);
	}

	var type = this.types[name];

	if (!type) {
		alert("Type " + name + " not defined");
		throw "Error";
	}

	return type;
};


		

Schema.getType = function(name) {
	var res = name.split(":");
	if (typeof(res[1]) == "undefined") {
		return Schema.getTypeNS(Schema.prefixes["xforms"], res[0]);
	} else {
		return Schema.getTypeNS(Schema.prefixes[res[0]], res[1]);
	}
};


		

Schema.getTypeNS = function(ns, name) {
	var schema = Schema.all[ns];
	
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


		

Schema.get = function(ns) {
	var schema = Schema.all[ns];

	if (!schema) {
		schema = new Schema(ns);
	}
	
	return schema;
};


		

Schema.prefixes = {
	"xsd_" : "http://www.w3.org/2001/XMLSchema",
	"xsd" : "http://www.w3.org/2001/XMLSchema",
	"xforms" : "http://www.w3.org/2002/xforms",
	"xsltforms" : "http://www.agencexml.com/xsltforms"
};


		

Schema.registerPrefix = function(prefix, namespace) {
	this.prefixes[prefix] = namespace;
};
    
	
		
		
		
function AtomicType() {
	this.patterns = [];
}

AtomicType.prototype = new Type();


		

AtomicType.prototype.setBase = function(base) {
	var baseType = typeof base == "string"? this.schema.getType(base) : base;

	for (var id in baseType)  {
		var value = baseType[id];

		if (id == "patterns") {
			copyArray(value, this.patterns);
		} else if (id != "name" && id != "nsuri") {
			this[id] = value;
		}
	}
	
	return this;
};


		

AtomicType.prototype.put = function(name, value) {
	if (name == "base") {
		this.setBase(value);
	} else if (name == "pattern") {
		copyArray([value], this.patterns);
	} else {
		this[name] = value;
	}
	
	return this;
};


		

/** If valid return canonicalValue else null*/
AtomicType.prototype.validate = function (value) {
	value = this.canonicalValue(value);

	for (var i = 0, len = this.patterns.length; i < len; i++) {
		if (!value.match(this.patterns[i])) {
			return false;
		}
	}
	
	if (this.enumeration != null) {
		var matched = false;

		for (var j = 0, len1 = this.enumeration.length; j < len1; j++) {
			if (value == this.canonicalValue(this.enumeration[j])) {
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

	if (   (this.length != null && this.length != l)
		|| (this.minLength != null && l < this.minLength)
		|| (this.maxLength != null && l > this.maxLength)
		|| (this.maxInclusive != null && value_i > this.maxInclusive)
		|| (this.maxExclusive != null && value_i >= this.maxExclusive)
		|| (this.minInclusive != null && value_i < this.minInclusive)
		|| (this.minExclusive != null && value_i <= this.minExclusive) ) {
		return false;
	}
	
	if (this.totalDigits != null || this.fractionDigits != null) {
		var index = value.indexOf(".");
		var integer = parseInt(index != -1? value.substring(0, index) : value, 10);
		var decimal = index != -1? value.substring(index + 1) : "";
		
		if (index != -1) {
			if (this.fractionDigits == 0) {
				return false;
			}
			var dl = decimal.length - 1;
			for (; dl >= 0 && decimal.charAt(dl) == 0; dl--) {}
			decimal = decimal.substring(0, dl + 1);
		}

		if (   (this.totalDigits != null && integer.length + decimal.length > this.totalDigits)
			|| (this.fractionDigits != null && decimal.length > this.fractionDigits)) {
			return false;
		}
	}
	
	return true;
};


		

AtomicType.prototype.normalize = function (value) {
	if (this.fractionDigits != null) {
		var number = parseFloat(value);
		var num;

		if (isNaN(number)) {
			return "NaN";
		}

		if (number == 0) {
			num = zeros(0, this.fractionDigits + 1, true);
		}  else {
			var mult = zeros(1, this.fractionDigits + 1, true);
			num = "" + Math.round(number * mult);
		}

		if (this.fractionDigits != 0) {
			var index = num.length - this.fractionDigits;
			return (index == 0? "0" : num.substring(0, index)) + "." + num.substring(index);
		}
		
		return num;
	}
	
	return value;
};
    
	
		
		
		
function ListType() {
	this.whiteSpace = "collapse";
}

ListType.prototype = new Type();


		

ListType.prototype.setItemType = function(itemType) {
	this.itemType = typeof itemType == "string"? this.schema.getType(itemType) : itemType;
	return this;
};


		

ListType.prototype.validate = function (value) {
	var items = this.baseType.canonicalValue.call(this, value).split(" ");
	value = "";

	if (items.length == 1 && items[0] == "") {
		items = [];
    }

	for (var i = 0, len = items.length; i < len; i++) {
		var item = itemType.validate(items[i]);

		if (!item) {
			return null;
		}
		
		value += value.length === 0? item : " " + item;
	}

	if ( (this.length != null > 0 && this.length != 1) // !!! was l (lowercase L)
		|| (this.minLength != null && 1 < this.minLength)
		|| (this.maxLength != null && 1 > this.maxLength)) {
		return null;
	}

    return null;
};


		

ListType.prototype.canonicalValue = function(value) {
	var items = this.baseType.canonicalValue(value).split(" ");
	var cvalue = "";

	for (var i = 0, len = items.length; i < len; i++) {
		var item = this.itemType.canonicalValue(items[i]);
		cvalue += (cvalue.length === 0? "" : " ") + item;
    }

    return cvalue;
};
    
	
		
		
		
function UnionType() {
	this.baseTypes = [];
}

UnionType.prototype = new Type();


		

UnionType.prototype.addType = function(type) {
	this.baseTypes.push(typeof type == "string"? this.schema.getType(type) : type);
	return this;
};


		

UnionType.prototype.validate = function (value) {
	for (var i = 0, len = this.baseTypes.length; i < len; ++i) {
		if (this.baseTypes[i].validate(value)) {
			return true;
		}
	}

	return false;
};
    
	
		
		
		
var TypeDefs = {

		

	initAll : function() {
		this.init("http://www.w3.org/2001/XMLSchema", this.Default);
		this.init("http://www.w3.org/2002/xforms", this.XForms);
		this.init("http://www.agencexml.com/xsltforms", this.XSLTForms);
	},

		

	init : function(ns, list) {
		var schema = Schema.get(ns);
	
		for (var id in list) {
			var type = new AtomicType();
			type.setSchema(schema);
			type.setName(id);
			var props = list[id];
			var base = props.base;

			if (base) {
				type.setBase(base);
			}
		
			for (var prop in props) {
				if (prop != "base") {				
					type[prop] = props[prop];
				}
			}
		}
	},
	ctes : {
		i : "A-Za-z_\\xC0-\\xD6\\xD8-\\xF6\\xF8-\\xFF",
		c : "A-Za-z_\\xC0-\\xD6\\xD8-\\xF6\\xF8-\\xFF\\-\\.0-9\\xB7"
	}
};


		

TypeDefs.Default = {

		

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
			return I8N.formatNumber(value, this.fractionDigits);
		},
		"parse" : function(value) {
			return I8N.parseNumber(value);
		}
	},

		

	"float" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^(([-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][-+]?[0-9]+)?|-?INF|NaN)$" ],
		"class" : "number"
	},

		

	"double" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^(([-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][-+]?[0-9]+)?|-?INF|NaN)$" ],
		"class" : "number"
	},

		

	"dateTime" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+-]([01][0-9]|2[0-3]):[0-5][0-9])?$" ],
		"class" : "datetime",
		"displayLength" : 20,
		"format" : function(value) {
			return I8N.format(I8N.parse(value, "yyyy-MM-ddThh:mm:ss"),null, true);
		},
		"parse" : function(value) {
			return I8N.format(I8N.parse(value), "yyyy-MM-ddThh:mm:ss", true);
		}
	},

		

	"date" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])(Z|[+-]([01][0-9]|2[0-3]):[0-5][0-9])?$" ],
		"class" : "date",
		"displayLength" : 10,
		"format" : function(value) {
			return I8N.formatDate(I8N.parse(value, "yyyy-MM-dd"));
		},
		"parse" : function(value) {
			return I8N.format(I8N.parseDate(value), "yyyy-MM-dd", true);
		}
	},

		

	"time" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+-]([01][0-9]|2[0-3]):[0-5][0-9])?$" ],
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
		"patterns" : [ "^([-+]?([0-9]{4}|[1-9][0-9]{4,}))?$" ]
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
		"patterns" : [ "^([\\-][0-9]+|0)$" ]
	},

		

	"nonNegativeInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^[+]?[0-9]+$" ]
	},

		

	"negativeInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^[\\-][0-9]+$" ]
	},

		

	"positiveInteger" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:integer",
		"patterns" : [ "^[+]?0*[1-9][0-9]*$" ]
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
		"patterns" : [ "^(([^:\\/?#]+):)?(\\/\\/([^\\/\\?#]*))?([^\\?#]*)(\\?([^#]*))?(#([^\\:#\\[\\]\\@\\!\\$\\&\\\\'\(\\)\\*\\+\\,\\;\\=]*))?$" ]
	},

		

	"Name" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^[" + TypeDefs.ctes.i + ":][" + TypeDefs.ctes.c + ":]*$" ]
	},

		

	"NCName" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^[" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*$" ]
	},

		

	"QName" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"base" : "xsd_:token",
		"patterns" : [ "^(([a-zA-Z][0-9a-zA-Z+\\-\\.]*:)?/{0,2}[0-9a-zA-Z;/?:@&=+$\\.\\>> -_!~*'()%]+)?(>> #[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?:[" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*$" ]
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
		"patterns" : [ "^[" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*( +[" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*)*$" ]
	},

		

	"NMTOKEN" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[" + TypeDefs.ctes.c + "]+$" ]
	},

		

	"NMTOKENS" : {
		"nsuri" : "http://www.w3.org/2001/XMLSchema",
		"patterns" : [ "^[" + TypeDefs.ctes.c + "]+( +[" + TypeDefs.ctes.c + "]+)*$" ]
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


		

TypeDefs.XForms = {

		

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
			return I8N.formatNumber(value, this.fractionDigits);
		},
		"parse" : function(value) {
			return I8N.parseNumber(value);
		}
	},

		

	"float" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^((([-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][-+]?[0-9]+)?|-?INF|NaN))?$" ],
		"class" : "number"
	},

		

	"double" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^((([-+]?([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][-+]?[0-9]+)?|-?INF|NaN))?$" ],
		"class" : "number"
	},

		

	"dateTime" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+-]([01][0-9]|2[0-3]):[0-5][0-9])?)?$" ],
		"class" : "datetime",
		"displayLength" : 20,
		"format" : function(value) {
			return I8N.format(I8N.parse(value, "yyyy-MM-ddThh:mm:ss"), null, true);
		},
		"parse" : function(value) {
			return I8N.format(I8N.parse(value), "yyyy-MM-ddThh:mm:ss", true);
		}
	},

		

	"date" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])(Z|[+-]([01][0-9]|2[0-3]):[0-5][0-9])?)?$" ],
		"class" : "date",
		"displayLength" : 10,
		"format" : function(value) {
			return I8N.formatDate(I8N.parse(value, "yyyy-MM-dd"));
		},
		"parse" : function(value) {
			return I8N.format(I8N.parseDate(value), "yyyy-MM-dd");
		}
	},

		

	"time" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?(Z|[+-]([01][0-9]|2[0-3]):[0-5][0-9])?)?$" ],
		"displayLength" : 8
	},

		

	"duration" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^(-?P(?!$)([0-9]+Y)?([0-9]+M)?([0-9]+D)?(T(?!$)([0-9]+H)?([0-9]+M)?([0-9]+(\\.[0-9]+)?S)?)?)?$" ]
	},

		

	"dayTimeDuration" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([\-]?P([0-9]+D(T([0-9]+(H([0-9]+(M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|(\.[0-9]*)?S)?|M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|\.[0-9]+S))?|T([0-9]+(H([0-9]+(M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|(\.[0-9]*)?S)?|M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|\.[0-9]+S)))?$" ]
	},

		

	"yearMonthDuration" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([\-]?P[0-9]+(Y([0-9]+M)?|M))?$" ]
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
		"patterns" : [ "^([-+]?([0-9]{4}|[1-9][0-9]{4,}))?$" ]
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
		"patterns" : [ "^(([\\-][0-9]+|0))?$" ]
	},

		

	"nonNegativeInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^([+]?[0-9]+)?$" ]
	},

		

	"negativeInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^([\\-][0-9]+)?$" ]
	},

		

	"positiveInteger" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:integer",
		"patterns" : [ "^[+]?0*[1-9][0-9]*$" ]
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
		"patterns" : [ "^([" + TypeDefs.ctes.i + ":][" + TypeDefs.ctes.c + ":]*)?$" ]
	},

		

	"NCName" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^([" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*)?$" ]
	},

		

	"QName" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"base" : "xforms:token",
		"patterns" : [ "^((([a-zA-Z][0-9a-zA-Z+\\-\\.]*:)?/{0,2}[0-9a-zA-Z;/?:@&=+$\\.\\>> -_!~*'()%]+)?(>> #[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?:[" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*)?$" ]
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
		"patterns" : [ "^([" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]+( +[" + TypeDefs.ctes.i + "][" + TypeDefs.ctes.c + "]*)*)?$" ]
	},

		

	"NMTOKEN" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^[" + TypeDefs.ctes.c + "]*$" ]
	},

		

	"NMTOKENS" : {
		"nsuri" : "http://www.w3.org/2002/xforms",
		"patterns" : [ "^([" + TypeDefs.ctes.c + "]+( +[" + TypeDefs.ctes.c + "]+)*)?$" ]
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
		"patterns" : [ "^([A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+)*@[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+(\.[A-Za-z0-9!#-'\*\+\-/=\?\^_`\{-~]+)*)?$" ]
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
			return I8N.formatNumber(value, 2);
		}
	}
};

		

TypeDefs.XSLTForms = {

		

	"decimal" : {
		"nsuri" : "http://www.agencexml.com/xsltforms",
		"patterns" : [ "^[-+]?\\(*[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)(([+-/]|\\*)\\(*([0-9]+(\\.[0-9]*)?|\\.[0-9]+)\\)*)*$" ],
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

TypeDefs.initAll();

	
	
		
		
		
		
		
function Listener(observer, name, phase, handler) {
    phase = phase || "default";
    if (phase != "default" && phase != "capture") {
        xforms.error(xforms.defaultModel, "xforms-compute-exception", 
                "Unknown event-phase(" + phase +") for event(" 
                + name + ")"+(observer? " on element(" + observer.id + ")":"") + "!");
        return;
    }
    this.observer = observer;
    this.name = name;
    this.evtName = document.addEventListener? name : "errorupdate";
    this.phase = phase;
    this.handler = handler;
    assert(observer);
    
    if (!observer.listeners) {
        observer.listeners = [];
    }
    
    observer.listeners.push(this);
    
    this.callback = function(event) {
		
	    if (!document.addEventListener) {
	        event = event || window.event;
	        event.target = event.srcElement;
					event.currentTarget = observer;

	        if (event.trueName && event.trueName != name) {
	            return;
	        }
	  
	        if (!event.phase) {
	            if (phase == "capture") {
	                return;
	            }
	        } else if (event.phase != phase) {
	            return;
	        }
	  
	        if (phase == "capture") {
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
			if(event.currentTarget && event.type == "DOMActivate" && event.target.nodeName == "BUTTON" && !Core.isFF2) {
				effectiveTarget = false;
			}
			if(event.eventPhase == 3 && !event.target.xfElement && !Core.isFF2) {
				effectiveTarget = false;
			}
			if(event.eventPhase == 3 && event.target.xfElement && event.target == event.currentTarget && !Core.isFF2) {
				effectiveTarget = false;
			}
			if (effectiveTarget) {
	
		    if (event.target != null && event.target.nodeType == 3) {
		        event.target = event.target.parentNode;
		    }
	    
				handler.call(event.target, event);
			}
	
	    if (!document.addEventListener) {
	        try {
						event.preventDefault = null;
						event.stopPropagation = null;
					} catch (e) {};
	    }
	};

    this.attach();
}


		

Listener.prototype.attach = function() {
    XSLTFormsEvent.attach(this.observer, this.evtName, this.callback, this.phase == "capture");
};


		

Listener.prototype.detach = function() {
    XSLTFormsEvent.detach(this.observer, this.evtName, this.callback, this.phase == "capture");
};


		

Listener.prototype.clone = function(element) {
    var unused = new Listener(element, this.name, this.phase, this.handler);
};
    
	
		
		
		
XMLEvents = {

		

    REGISTRY : [],

		

	EventContexts : [],

		

	define : function(name, bubbles, cancelable, defaultAction) {
			XMLEvents.REGISTRY[name] = {
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


		

XMLEvents.dispatchList = function(list, name) {
    for (var id = 0, len = list.length; id < len; id++) {
        XMLEvents.dispatch(list[id], name);
    }
};


		

XMLEvents.dispatch = function(target, name, type, bubbles, cancelable, defaultAction, evcontext) {
	target = target.element || target;
	assert(target != null && typeof(target.nodeName) != "undefined");
	DebugConsole.write("Dispatching event " + name + " on <" + target.nodeName
      + (target.className? " class=\"" + target.className + "\"" : "")
      + (target.id? " id=\"" + target.id + "\"" : "") + "/>");
	var reg = XMLEvents.REGISTRY[name];

	if (reg != null) {
		bubbles = reg.bubbles;
		cancelable = reg.cancelable;
		defaultAction = reg.defaultAction;
	}

	if (!defaultAction) {
		defaultAction = function() { };
	}

	evcontext = XMLEvents.makeEventContext(evcontext, name, target.id, bubbles, cancelable);
	XMLEvents.EventContexts.push(evcontext);
	try {
    if (target.dispatchEvent) {
			var event = document.createEvent("Event");
			event.initEvent(name, bubbles, cancelable);
			var res = target.dispatchEvent(event);

			if ((res && !event.stopped) || !cancelable) {
				defaultAction.call(target.xfElement, event);
			}
    } else {
			var fauxName = "errorupdate";
			var canceler = null;
			// Capture phase.
			var ancestors = [];

			for (var a = target.parentNode; a != null; a = a.parentNode) {
				ancestors.unshift(a);
			}

			for (var i = 0, len = ancestors.length; i < len; i++) {
				var event = document.createEventObject();
				event.trueName = name;
				event.phase = "capture";
				ancestors[i].fireEvent("onerrorupdate", event);

				if (event.stopped) {
					return;
				}
			}

			var event = document.createEventObject();
			event.trueName = name;
			event.phase = "capture";
			event.target = target;
			target.fireEvent("onerrorupdate" , event);

			// Bubble phase.
			if (!bubbles) {
				canceler = new Listener(target, name, "default",
					function(event) { event.cancelBubble = true; });
			}

			var event = document.createEventObject();
			event.trueName = name;
			event.phase = "default";
			event.target = target;
        
			var res = target.fireEvent("onerrorupdate", event);

			try {
				if ((res && !event.stopped) || !cancelable) {
					defaultAction.call(target.xfElement, event);
				}

				if (!bubbles) {
					canceler.detach();
				}
			} catch (e) {
				alert("XSLTForms Exception\n--------------------------\n\nError initializing :\n\n"+(typeof(e.stack)=="undefined"?"":e.stack)+"\n\n"+(e.name?e.name+(e.message?"\n\n"+e.message:""):e));
			}
		}
	} catch (e) {
		alert("XSLTForms Exception\n--------------------------\n\nError initializing :\n\n"+(typeof(e.stack)=="undefined"?"":e.stack)+"\n\n"+(e.name?e.name+(e.message?"\n\n"+e.message:""):e));
	} finally {
		if (XMLEvents.EventContexts[XMLEvents.EventContexts.length - 1].rheadsdoc) {
			XMLEvents.EventContexts[XMLEvents.EventContexts.length - 1].rheadsdoc = null;
		}
		if (XMLEvents.EventContexts[XMLEvents.EventContexts.length - 1]["response-body"]) {
			XMLEvents.EventContexts[XMLEvents.EventContexts.length - 1]["response-body"] = null;
		}
		XMLEvents.EventContexts.pop();
	}
};


		

XMLEvents.define("xforms-model-construct",      true, false, function(event) { this.construct(); });

		

XMLEvents.define("xforms-model-construct-done", true, false);

		

XMLEvents.define("xforms-ready",                true, false);

		

XMLEvents.define("xforms-model-destruct",       true, false);

		

XMLEvents.define("xforms-rebuild",              true, true, function(event) { this.rebuild(); });

		

XMLEvents.define("xforms-recalculate",          true, true, function(event) { this.recalculate(); });

		

XMLEvents.define("xforms-revalidate",           true, true, function(event) { this.revalidate(); });

		

XMLEvents.define("xforms-reset",                true, true, function(event) { this.reset(); });

		

XMLEvents.define("xforms-submit",               true, true, function(event) { this.submit(); });

		

XMLEvents.define("xforms-submit-serialize",               true, false);

		

XMLEvents.define("xforms-refresh",              true, true, function(event) { this.refresh(); });

		

XMLEvents.define("xforms-focus",                true, true, function(event) { this.focus(); } );


		

XMLEvents.define("DOMActivate",          true,  true);

		

XMLEvents.define("DOMFocusIn",           true,  false);

		

XMLEvents.define("DOMFocusOut",          true,  false);

		

XMLEvents.define("xforms-select",        true,  false);

		

XMLEvents.define("xforms-deselect",      true,  false);

		

XMLEvents.define("xforms-value-changed", true,  false);


		

XMLEvents.define("xforms-insert",        true,  false);

		

XMLEvents.define("xforms-delete",        true,  false);

		

XMLEvents.define("xforms-valid",         true,  false);

		

XMLEvents.define("xforms-invalid",       true,  false);

		

XMLEvents.define("xforms-enabled",       true,  false);

		

XMLEvents.define("xforms-disabled",      true,  false);

		

XMLEvents.define("xforms-optional",      true,  false);

		

XMLEvents.define("xforms-required",      true,  false);

		

XMLEvents.define("xforms-readonly",      true,  false);

		

XMLEvents.define("xforms-readwrite",     true,  false);

		

XMLEvents.define("xforms-in-range",      true,  false);

		

XMLEvents.define("xforms-out-of-range",  true,  false);

		

XMLEvents.define("xforms-submit-done",   true,  false);

		

XMLEvents.define("xforms-submit-error",  true,  false);


		

XMLEvents.define("xforms-compute-exception",     true, false);

		

XMLEvents.define("xforms-binding-exception",     true, false);

XMLEvents.define("ajx-start", true,  true, function(evt) { evt.target.xfElement.start(); });
XMLEvents.define("ajx-stop",  true,  true, function(evt) { evt.target.xfElement.stop(); });
XMLEvents.define("ajx-time",  true,  true);

		

XMLEvents.define("xforms-dialog-open",  true,  true, function(evt) { Dialog.show(evt.target, null, true); });

		

XMLEvents.define("xforms-dialog-close",  true,  true, function(evt) { Dialog.hide(evt.target, true); });
    
	
	
		
		
		
		
		
var XPathAxis = {
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


		

var NodeType = {
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

	
		
		
		
function BinaryExpr(expr1, op, expr2) {
    this.expr1 = expr1;
    this.expr2 = expr2;
    this.op = op.replace("&gt;", ">").replace("&lt;", "<");
}


		

BinaryExpr.prototype.evaluate = function(ctx) {
    var v1 = this.expr1.evaluate(ctx);
    var v2 = this.expr2.evaluate(ctx);
		var n1;
		var n2;
		if ((((typeof v1) == "object" && v1.length > 1) || ((typeof v2) == "object" && v2.length > 1)) && 
		    (this.op == "=" || this.op == "!=" || this.op == "<" || this.op == "<=" || this.op == ">" || this.op == ">=")) {
			for (var i = 0, len = v1.length; i < len; i++) {
				n1 = numberValue([v1[i]]);
				if (isNaN(n1)) {
						n1 = stringValue([v1[i]]);
				}
				for (var j = 0, len1 = v2.length; j < len1; j++) {
					n2 = numberValue([v2[j]]);
					if (isNaN(n2)) {
							n2 = stringValue([v2[j]]);
					}
					switch (this.op) {
						case '='   : if (n1 == n2) return true; break;
						case '!='  : if (n1 != n2) return true; break;
						case '<'   : if (n1 < n2) return true; break;
						case '<='  : if (n1 <= n2) return true; break;
						case '>'   : if (n1 > n2) return true; break;
						case '>='  : if (n1 >= n2) return true; break;
					}
				}
			}
			return false;
		}
    n1 = numberValue(v1);
    n2 = numberValue(v2);
    
    if (isNaN(n1) || isNaN(n2)) {
        n1 = stringValue(v1);
        n2 = stringValue(v2);
    }

		var res = 0;
    switch (this.op) {
        case 'or'  : res = booleanValue(v1) || booleanValue(v2); break;
        case 'and' : res = booleanValue(v1) && booleanValue(v2); break;
        case '+'   : res = n1 + n2; break;
        case '-'   : res = n1 - n2; break;
        case '*'   : res = n1 * n2; break;
        case 'mod' : res = n1 % n2; break;
        case 'div' : res = n1 / n2; break;
        case '='   : res = n1 == n2; break;
        case '!='  : res = n1 != n2; break;
        case '<'   : res = n1 < n2; break;
        case '<='  : res = n1 <= n2; break;
        case '>'   : res = n1 > n2; break;
        case '>='  : res = n1 >= n2; break;
    }
		return typeof res == "number" ? Math.round(res*1000000)/1000000 : res;
};
    
	
		
		
		
function ExprContext(node, position, nodelist, parent, nsresolver, current,
		depsNodes, depsId, depsElements) {
/* 
if(depsNodes) {
var s = depsNodes.length > 0 ? "ExprContext depsNodes=" : "";
for(var i = 0; i < depsNodes.length; i++) s += depsNodes[i].nodeName + " ";
if(s!="")alert(s);
}
*/
    this.node = node;
    this.current = current || node;
		if(position == null) {
			var repeat = node ? Core.getMeta(node, "repeat") : null;
			if(repeat) {
				var xrepeat = document.getElementById(repeat).xfElement;
				for(position = 1, len = xrepeat.nodes.length; position <= len; position++) {
					if(node == xrepeat.nodes[position-1]) {
						break;
					}
				}
			}
		}
    this.position = position || 1;
    this.nodelist = nodelist || [ node ];
    this.parent = parent;
    this.root = parent? parent.root : node ? node.ownerDocument : null;
    this.nsresolver = nsresolver;
		this.depsId = depsId;
    this.initDeps(depsNodes, depsElements);
}


		

ExprContext.prototype.clone = function(node, position, nodelist) {
    return new ExprContext(node || this.node, 
           typeof position == "undefined"? this.position : position,
           nodelist || this.nodelist, this, this.nsresolver, this.current,
           this.depsNodes, this.depsId, this.depsElements);
};


		

ExprContext.prototype.setNode = function(node, position) {
    this.node = node;
    this.position = position;
};


		

ExprContext.prototype.initDeps = function(depsNodes, depsElements) {
	this.depsNodes = depsNodes;
	this.depsElements = depsElements;
};


		

ExprContext.prototype.addDepNode = function(node) {
	var deps = this.depsNodes;
//alert("Binding:"+this.depsId+" "+deps+" addDepNode "+node.nodeName);

	if (deps && !Core.getBoolMeta(node, "depfor_"+this.depsId)) { // !inArray(node, deps)) {
		Core.setTrueBoolMeta(node, "depfor_"+this.depsId);
		deps.push(node);
	}
};


		

ExprContext.prototype.addDepElement = function(element) {
	var deps = this.depsElements;

	if (deps && !inArray(element, deps)) {
		deps.push(element);
	}
};
    
	
		
		
		
function TokenExpr(m) {
    this.value = m;
}


		

TokenExpr.prototype.evaluate = function() {
    return stringValue(this.value);
};


		

function UnaryMinusExpr(expr) {
    this.expr = expr;
}


		

UnaryMinusExpr.prototype.evaluate = function(ctx) {
    return -numberValue(this.expr.evaluate(ctx));
};


		

function CteExpr(value) {
    this.value = value;
}


		

CteExpr.prototype.evaluate = function() {
    return this.value;
};
    
	
		
		
		
function FilterExpr(expr, predicate) {
    this.expr = expr;
    this.predicate = predicate;
}


		

FilterExpr.prototype.evaluate = function(ctx) {
    var nodes = nodeSetValue(this.expr.evaluate(ctx));

    for (var i = 0, len = this.predicate.length; i < len; ++i) {
        var nodes0 = nodes;
        nodes = [];

        for (var j = 0, len1 = nodes0.length; j < len1; ++j) {
            var n = nodes0[j];
            var newCtx = ctx.clone(n, j, nodes0);

            if (booleanValue(this.predicate[i].evaluate(newCtx))) {
                nodes.push(n);
            }
        }
    }

    return nodes;
};
    
	
		
		
		
function FunctionCallExpr(name) {
	this.name = name;
	this.func = XPathCoreFunctions[name];
	this.args = [];
    
	if (!this.func) {
		throw {name: "Function " + name + "() not found"};
	}

	for (var i = 1, len = arguments.length; i < len; i++) {
		this.args.push(arguments[i]);
	}
}


		

FunctionCallExpr.prototype.evaluate = function(ctx) {
	var arguments_ = [];

	for (var i = 0, len = this.args.length; i < len; i++) {
		arguments_[i] = this.args[i].evaluate(ctx);
	}

	return this.func.call(ctx, arguments_);
};
    
	
		
		
		
function LocationExpr(absolute) {
    this.absolute = absolute;
    this.steps = [];

    for (var i = 1, len = arguments.length; i < len; i++) {
        this.steps.push(arguments[i]);
    }
}


		

LocationExpr.prototype.evaluate = function(ctx) {
	var start = this.absolute? ctx.root : ctx.node;
	ctx.addDepElement(document.getElementById(Core.getMeta((start.documentElement ? start.documentElement : start.ownerDocument.documentElement), "model")).xfElement);

	var nodes = [];
	this.xPathStep(nodes, this.steps, 0, start, ctx);
	return nodes;
};

LocationExpr.prototype.xPathStep = function(nodes, steps, step, input, ctx) {
    var s = steps[step];
    var nodelist = s.evaluate(ctx.clone(input));

    for (var i = 0, len = nodelist.length; i < len; ++i) {
        var node = nodelist[i];

        if (step == steps.length - 1) {
						if (!inArray(node, nodes)) {
							nodes.push(node);
						}
            ctx.addDepNode(node);
        } else {
            this.xPathStep(nodes, steps, step + 1, node, ctx);
        }
    }
};
    
	
		
		
		
function NodeTestAny() {
}


		

NodeTestAny.prototype.evaluate = function(node) {
    return true;
};
    
	
		
		

function NodeTestName(prefix, name) {
    this.prefix = prefix;
    this.name = name;
}


		

NodeTestName.prototype.evaluate = function(node, nsresolver) {
    var pre = this.prefix;

    if (this.name == "*") {
        return pre && pre != "*" ? node.namespaceURI == nsresolver.lookupNamespaceURI(pre) : true;
    }
    
    var ns = node.namespaceURI;

    return (node.localName || node.baseName) == this.name
       && (pre && pre != "*" ? ns == nsresolver.lookupNamespaceURI(pre)
               : (pre != "*" ? ns == null || ns == "" || ns == nsresolver.lookupNamespaceURI("") : true));
};
    
	
		
		
		
function NodeTestPI(target) {
    this.target = target;
}


		

NodeTestPI.prototype.evaluate = function(node) {
    return node.nodeType == NodeType.PROCESSING_INSTRUCTION &&
         (!this.target || node.nodeName == this.target);
};
    
	
		
		
		
function NodeTestType(type) {
    this.type = type;
}


		

NodeTestType.prototype.evaluate = function(node) {
    return node.nodeType == this.type;
};
    
	
		
		
		
function NSResolver() {
    this.map = {};
		this.notfound = false;
}


		

NSResolver.prototype.registerAll = function(resolver) {
    for (var prefix in resolver.map) {
        this.map[prefix] = resolver.map[prefix];
    }
};


		

NSResolver.prototype.register = function(prefix, uri) {
    this.map[prefix] = uri;
		if( uri == "notfound" ) {
			this.notfound = true;
		}
};


		

NSResolver.prototype.registerNotFound = function(prefix, uri) {
		if( this.map[prefix] == "notfound" ) {
			this.map[prefix] = uri;
			for(p in this.map) {
				if( this.map[p] == "notfound" ) {
					this.notfound = true;
				}
			}
		}
};


		

NSResolver.prototype.lookupNamespaceURI = function(prefix) {
    return this.map[prefix];
};
    
	
		
		
		
function PathExpr(filter, rel) {
    this.filter = filter;
    this.rel = rel;
}


		

PathExpr.prototype.evaluate = function(ctx) {
    var nodes = nodeSetValue(this.filter.evaluate(ctx));
    var nodes1 = [];

    for (var i = 0, len = nodes.length; i < len; i++) {
        var newCtx = ctx.clone(nodes[i], i, nodes);
        var nodes0 = nodeSetValue(this.rel.evaluate(newCtx));

        for (var j = 0, len1 = nodes0.length; j < len1; j++) {
            nodes1.push(nodes0[j]);
        }
    }

    return nodes1;
};
    
	
		
		
		
function PredicateExpr(expr) {
    this.expr = expr;
}


		

PredicateExpr.prototype.evaluate = function(ctx) {
    var v = this.expr.evaluate(ctx);
    return typeof v == "number" ? ctx.position == v : booleanValue(v);
};
    
	
		
		
		
function StepExpr(axis, nodetest) {
	this.axis = axis;
	this.nodetest = nodetest;
	this.predicates = [];

	for (var i = 2, len = arguments.length; i < len; i++) {
		this.predicates.push(arguments[i]);
	}
}


		

StepExpr.prototype.evaluate = function(ctx) {
	var input = ctx.node;
	var list = [];

	switch(this.axis) {
		case XPathAxis.ANCESTOR_OR_SELF :
			_push(ctx, list, input, this.nodetest);
			// explicit no break here -- fallthrough
		case XPathAxis.ANCESTOR :
			if (input.nodeType == NodeType.ATTRIBUTE) {
				input = input.ownerElement ? input.ownerElement : input.selectSingleNode("..");
				_push(ctx, list, input, this.nodetest);
			}
			for (var pn = input.parentNode; pn.parentNode; pn = pn.parentNode) {
				_push(ctx, list, pn, this.nodetest);
			}
			break;
		case XPathAxis.ATTRIBUTE :
			_pushList(ctx, list, input.attributes, this.nodetest);
			break;
		case XPathAxis.CHILD :
			_pushList(ctx, list, input.childNodes, this.nodetest);
			break;
		case XPathAxis.DESCENDANT_OR_SELF :
			_push(ctx, list, input, this.nodetest);
			// explicit no break here -- fallthrough
		case XPathAxis.DESCENDANT :
			_pushDescendants(ctx, list, input, this.nodetest);
			break;
		case XPathAxis.FOLLOWING :
			var n = input.nodeType == NodeType.ATTRIBUTE ? input.ownerElement ? input.ownerElement : input.selectSingleNode("..") : input;
			while (n.nodeType != NodeType.DOCUMENT) {
				for (var nn = n.nextSibling; nn; nn = nn.nextSibling) {
					_push(ctx, list, nn, this.nodetest);
					_pushDescendants(ctx, list, nn, this.nodetest);
				}
				n = n.parentNode;
			}
			break;
		case XPathAxis.FOLLOWING_SIBLING :
			for (var ns = input.nextSibling; ns; ns = ns.nextSibling) {
				_push(ctx, list, ns, this.nodetest);
			}
			break;
		case XPathAxis.NAMESPACE : 
			alert('not implemented: axis namespace');
			break;
		case XPathAxis.PARENT :
			if (input.parentNode) {
				_push(ctx, list, input.parentNode, this.nodetest);
			} else {
				if (input.nodeType == NodeType.ATTRIBUTE) {
					_push(ctx, list, input.ownerElement ? input.ownerElement : input.selectSingleNode(".."), this.nodetest);
				}
			}
			break;
		case XPathAxis.PRECEDING :
			var p = input.nodeType == NodeType.ATTRIBUTE ? input.ownerElement ? input.ownerElement : input.selectSingleNode("..") : input;
			while (p.nodeType != NodeType.DOCUMENT) {
				for (var ps = p.previousSibling; ps; ps = ps.previousSibling) {
					_pushDescendantsRev(ctx, list, ps, this.nodetest);
					_push(ctx, list, ps, this.nodetest);
				}
				p = p.parentNode;
			}
			break;
		case XPathAxis.PRECEDING_SIBLING :
			for (var ps = input.previousSibling; ps; ps = ps.previousSibling) {
				_push(ctx, list, ps, this.nodetest);
			}
			break;
		case XPathAxis.SELF :
			_push(ctx, list, input, this.nodetest);
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

			if (booleanValue(pred.evaluate(newCtx))) {
				newList.push(x);
			}
		}

		list = newList;
    }

    return list;
};

function _push(ctx, list, node, test) {
    if (test.evaluate(node, ctx.nsresolver) && !inArray(node, list)) {
			list[list.length] = node;
    }
}

function _pushList(ctx, list, l, test) {
    for (var i = 0, len = l ? l.length : 0; i < len; i++) {
        _push(ctx, list, l[i], test);
    }
}

function _pushDescendants(ctx, list, node, test) {
    for (var n = node.firstChild; n; n = n.nextSibling) {
        _push(ctx, list, n, test);
        arguments.callee(ctx, list, n, test);
    }
}

function _pushDescendantsRev(ctx, list, node, test) {
    for (var n = node.lastChild; n; n = n.previousSibling) {
        _push(ctx, list, n, test);
        arguments.callee(ctx, list, n, test);
    }
}
    
	
		
		
		
function UnionExpr(expr1, expr2) {
    this.expr1 = expr1;
    this.expr2 = expr2;
}


		

UnionExpr.prototype.evaluate = function(ctx) {
    var nodes1 = nodeSetValue(this.expr1.evaluate(ctx));
    var nodes2 = nodeSetValue(this.expr2.evaluate(ctx));

    var I1 = nodes1.length;

    for (var i2 = 0, len = nodes2.length; i2 < len; ++i2) {
			var found = false;
      for (var i1 = 0; i1 < I1; ++i1) {
        found = nodes1[i1] == nodes2[i2];
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
    
	
		
		
		
function stringValue(value) {
    return typeof value != "object"? "" + value
        : (value.length === 0? "" : xmlValue(value[0]));
}


		

function booleanValue(value) {
    return typeof value == "undefined"? false
    	: (typeof value.length != "undefined"? value.length > 0 : !!value);
}


		

var nbvalcount = 0;
function numberValue(value) {
	if (typeof value == "boolean") {
    return 'A' - 0;
	} else {
		var v = typeof value == "object"?  stringValue(value) : value;
		return v === '' ? NaN : v - 0;
	}
}


		

function nodeSetValue(value) {
    if (typeof value != "object") {
        throw {name: this, message: Error().stack};
    }

    return value;
}


		

if (Core.isIE) {
	xmlValue = function(node) {
		var ret = node.text;
		var schtyp = Schema.getType(Core.getType(node) || "xsd_:string");
		if (schtyp.eval) {
			try {
				ret = ret == "" ? 0 : eval(ret);
			} catch (e) {}
		}
		return ret;
	};
} else {
	xmlValue = function(node) {
		var ret = node.textContent;
		var schtyp = Schema.getType(Core.getType(node) || "xsd_:string");
		if (schtyp.eval) {
			try {
				ret = ret == "" ? 0 : eval(ret);
			} catch (e) {}
		}
		return ret;
	};
}


		

function xmlResolveEntities(s) {
    var parts = stringSplit(s, '&');
    var ret = parts[0];

    for (var i = 1, len = parts.length; i < len; ++i) {
        var p = parts[i];
        var index = p.indexOf(";");
        
        if (index == -1) {
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
            case 'nbsp': ch = String.fromCharCode(160);  break;
            default:
                var span = Core.isXhtml ? document.createElementNS("http://www.w3.org/1999/xhtml", 'span') : document.createElement('span');
                span.innerHTML = '&' + rp + '; ';
                ch = span.childNodes[0].nodeValue.charAt(0);
        }

        ret += ch + p.substring(index + 1);
    }

    return ret;
}


		

function stringSplit(s, c) {
    var a = s.indexOf(c);

    if (a == -1) {
        return [ s ];
    }
  
    var parts = [];
    parts.push(s.substr(0,a));

    while (a != -1) {
        var a1 = s.indexOf(c, a + 1);

        if (a1 != -1) {
            parts.push(s.substr(a + 1, a1 - a - 1));
        } else {
            parts.push(s.substr(a + 1));
        } 

        a = a1;
    }

    return parts;
}
    
	
		
		
		
function XPath(expression, compiled, ns) {
	this.expression = expression;
	if (typeof compiled == "string") {
		alert("XSLTForms Exception\n--------------------------\n\nError parsing the following XPath expression :\n\n"+expression+"\n\n"+compiled);
		return;
	}
	this.compiled = compiled;
	this.compiled.isRoot = true;
	this.nsresolver = new NSResolver();
	XPath.expressions[expression] = this;

	if (ns.length > 0)  {
		for (var i = 0, len = ns.length; i < len; i += 2) {
			this.nsresolver.register(ns[i], ns[i + 1]);
		}
	} else {
		this.nsresolver.register("", "http://www.w3.org/1999/xhtml");
	}
	if (this.nsresolver.notfound) {
		XPath.notfound = true;
	}
	this.evaltime = 0;
}


		

XPath.prototype.evaluate = function(ctx, current) {
	var d1 = new Date();
	assert(ctx);

//	alert("XPath evaluate \""+this.expression+"\"");
	if (!ctx.node) {
		ctx = new ExprContext(ctx, 1, null, null, this.nsresolver, current);
	} else if (!ctx.nsresolver) {
		ctx.nsresolver = this.nsresolver;
	}

	try {
		var res = this.compiled.evaluate(ctx);
		if ((res instanceof Array) && res.length > 1) {
			var posres = [];
			for (var i = 0, len = res.length; i < len; i++) {
				posres.push({count: Core.selectNodesLength("preceding::* | ancestor::*", res[i]), node: res[i]});
			}
			posres.sort(function(a,b){return a.count - b.count;});
			for (var i = 0, len = posres.length; i < len; i++) {
				res[i] = posres[i].node;
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


		

XPath.expressions = {};
XPath.notfound = false;


		

XPath.get = function(str) {
	return XPath.expressions[str];
};

		

XPath.create = function(expression, compiled) {
	if (XPath.get(expression) != null) {
		delete compiled;
	} else {
		var ns = [];
		for (var i = 2, len = arguments.length; i < len; i += 2) {
			ns[i-2] = arguments[i];
			ns[i-1] = arguments[i+1];
		}
		new XPath(expression, compiled, ns);
	}
};

		

XPath.registerNS = function(prefix, uri) {
	if (XPath.notfound) {
		XPath.notfound = false;
		for( e in XPath.expressions ) {
			XPath.expressions[e].nsresolver.registerNotFound(prefix, uri);
			if (XPath.expressions[e].nsresolver.notfound) {
				XPath.notfound = true;
			}
		}
	}
};
    
	
		
		
		
function XPathFunction(acceptContext, defaultTo, returnNodes, body) {
	this.evaluate = body;
	this.defaultTo = defaultTo;
	this.acceptContext = acceptContext;
	this.returnNodes = returnNodes;
}

XPathFunction.DEFAULT_NONE = null;
XPathFunction.DEFAULT_NODE = 0;
XPathFunction.DEFAULT_NODESET = 1;
XPathFunction.DEFAULT_STRING = 2;


		

XPathFunction.prototype.call = function(context, arguments_) {
	if (arguments_.length === 0) {
		switch (this.defaultTo) {
		case XPathFunction.DEFAULT_NODE:
			if (context.node != null) {
				arguments_ = [context.node];
			}

			break;
		case XPathFunction.DEFAULT_NODESET:
			if (context.node != null) {
				arguments_ = [[context.node]];
			}

			break;
		case XPathFunction.DEFAULT_STRING:
			arguments_ = [XPathCoreFunctions.string.evaluate([context.node])];
			break;
		}
	}
  
	if (this.acceptContext) {
		arguments_.unshift(context);
	}

	return this.evaluate.apply(null, arguments_);
};
    
	
		
		
		
MathConstants = {
	"PI":      "3.14159265358979323846264338327950288419716939937510582",
	"E":       "2.71828182845904523536028747135266249775724709369995958",
	"SQRT2":   "1.41421356237309504880168872420969807856967187537694807",
	"LN2":     "0.693147180559945309417232121458176568075500134360255254",
	"LN10":    "2.30258509299404568401799145468436420760110148862877298",
	"LOG2E":   "1.44269504088896340735992468100189213742664595415298594",
	"SQRT1_2": "0.707106781186547524400844362104849039284835937688474038"
};
		
XPathCoreFunctions = {

		

	"http://www.w3.org/2005/xpath-functions last" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, false,
		function(ctx) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.lastInvalidArgumentsNumber;
			}
			return ctx.nodelist.length;
		} ),

		

	"http://www.w3.org/2005/xpath-functions position" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, false,
		function(ctx) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.positionInvalidArgumentsNumber;
			}
			return ctx.position;
		} ),

		

	"http://www.w3.org/2002/xforms context" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, false,
		function(ctx) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.positionInvalidArgumentsNumber;
			}
			return [ctx.current];
		} ),

		

	"http://www.w3.org/2005/xpath-functions count" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet) { 
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.countInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.countInvalidArgumentType;
			}
			return nodeSet.length;
		} ),

		

	"http://www.w3.org/2005/xpath-functions id" : new XPathFunction(true, XPathFunction.DEFAULT_NODE, false,
		function(context, object) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.idInvalidArgumentsNumber;
			}
			if (typeof object != "object" && typeof object != "string") {
				throw XPathCoreFunctionsExceptions.idInvalidArgumentType;
			}
			var result = [];

			if (typeof(object.length) != "undefined") {
				for (var i = 0, len = object.length; i < len; ++i) {
					var res = XPathCoreFunctions['http://www.w3.org/2005/xpath-functions id'].evaluate(context, object[i]);

					for (var j = 0, len1 = res.length; j < len1; j++) {
						result.push(res[j]);
					}
				}
			} else if (context.node != null) {
				var ids = stringValue(object).split(/\s+/);
      
				for (var j in ids) {
					result.add(context.node.ownerDocument.getElementById(ids[j]));
				}
			}
    
			return result;
		} ),

		

	"http://www.w3.org/2005/xpath-functions local-name" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.localNameInvalidArgumentsNumber;
			}
			if (arguments.length == 1 && typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.localNameInvalidArgumentType;
			}
			if (arguments.length == 0) {
				throw XPathCoreFunctionsExceptions.localNameNoContext;
			}
			return nodeSet.length === 0? "" : nodeSet[0].nodeName.replace(/^.*:/, "");
		} ),

		

	"http://www.w3.org/2005/xpath-functions namespace-uri" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.namespaceUriInvalidArgumentsNumber;
			}
			if (arguments.length == 1 && typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.namespaceUriInvalidArgumentType;
			}
			return nodeSet.length === 0? "" : nodeSet[0].namespaceURI || "";
		} ),

		

	"http://www.w3.org/2005/xpath-functions name" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.nameInvalidArgumentsNumber;
			}
			if (arguments.length == 1 && typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.nameInvalidArgumentType;
			}
			return nodeSet.length === 0? "" : nodeSet[0].nodeName;
		} ),

		

	"http://www.w3.org/2005/xpath-functions string" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(object) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.stringInvalidArgumentsNumber;
			}
			return stringValue(object);
		} ),

		

	"http://www.w3.org/2005/xpath-functions concat" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length <2) {
				throw XPathCoreFunctionsExceptions.concatInvalidArgumentsNumber;
			}
			var string = "";

			for (var i = 0, len = arguments.length; i < len; ++i) {
				string += stringValue(arguments[i]);
			}
    
			return string;
		} ),

		

	"http://www.w3.org/2005/xpath-functions starts-with" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, prefix) {   
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.startsWithInvalidArgumentsNumber;
			}
			return stringValue(string).indexOf(stringValue(prefix)) === 0;
		} ),

		

	"http://www.w3.org/2005/xpath-functions contains" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, substring) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.containsInvalidArgumentsNumber;
			}
			return stringValue(string).indexOf(stringValue(substring)) != -1;
		} ),

		

	"http://www.w3.org/2005/xpath-functions substring-before" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, substring) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.substringBeforeInvalidArgumentsNumber;
			}
			string = stringValue(string);
			return string.substring(0, string.indexOf(stringValue(substring)));
		} ),

		

	"http://www.w3.org/2005/xpath-functions substring-after" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, substring) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.substringAfterInvalidArgumentsNumber;
			}
			string = stringValue(string);
			substring = stringValue(substring);
			var index = string.indexOf(substring);
			return index == -1? "" : string.substring(index + substring.length);
		} ),

		

	"http://www.w3.org/2005/xpath-functions substring" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, index, length) {
			if (arguments.length != 2 && arguments.length != 3) {
				throw XPathCoreFunctionsExceptions.substringInvalidArgumentsNumber;
			}
			string = stringValue(string);
			index  = Math.round(numberValue(index));
			if (isNaN(index)) {
				return "";
			}
			
			if (length != null) {
				length = Math.round(numberValue(length));
				if (index <= 0) {
					return string.substr(0, index + length - 1);
				}
				return string.substr(index - 1, length);
			}
			return string.substr(Math.max(index - 1, 0));
		} ),

		

	"http://www.w3.org/2005/xpath-functions compare" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string1, string2) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.compareInvalidArgumentsNumber;
			}
			string1 = stringValue(string1);
			string2 = stringValue(string2);
			return (string1 == string2 ? 0 : (string1 > string2 ? 1 : -1));
		} ),

		

	"http://www.w3.org/2005/xpath-functions string-length" : new XPathFunction(false, XPathFunction.DEFAULT_STRING, false,
		function(string) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.stringLengthInvalidArgumentsNumber;
			}
			return stringValue(string).length;
		} ),

		

	"http://www.w3.org/2005/xpath-functions normalize-space" : new XPathFunction(false, XPathFunction.DEFAULT_STRING, false,
		function(string) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.normalizeSpaceLengthInvalidArgumentsNumber;
			}
			return stringValue(string).replace(/^\s+|\s+$/g, "")
				.replace(/\s+/, " ");
		} ),

		

	"http://www.w3.org/2005/xpath-functions translate" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, from, to) {
			if (arguments.length != 3) {
				throw XPathCoreFunctionsExceptions.translateInvalidArgumentsNumber;
			}
			string =  stringValue(string);
			from = stringValue(from);
			to = stringValue(to);
			
			var result = "";
			
			for (var i = 0, len = string.length; i < len; ++i) {
			    var index = from.indexOf(string.charAt(i));
			    result += index == -1? string.charAt(i) : to.charAt(index);
			}
			
			return result;
		} ),

		

	"http://www.w3.org/2005/xpath-functions boolean" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(object) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.booleanInvalidArgumentsNumber;
			}
			return booleanValue(object);
		} ),

		

	"http://www.w3.org/2005/xpath-functions not" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(condition) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.notInvalidArgumentsNumber;
			}
			return !booleanValue(condition);
		} ),

		

	"http://www.w3.org/2005/xpath-functions true" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length != 0) {
				throw XPathCoreFunctionsExceptions.trueInvalidArgumentsNumber;
			}
			return true;
		} ),

		

	"http://www.w3.org/2005/xpath-functions false" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length != 0) {
				throw XPathCoreFunctionsExceptions.falseInvalidArgumentsNumber;
			}
			return false;
		} ),

		

	"http://www.w3.org/2005/xpath-functions lang" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, false,
		function(context, language) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.langInvalidArgumentsNumber;
			}
			language = stringValue(language);

			for (var node = context.node; node != null; node = node.parentNode) {
				if (typeof(node.attributes) == "undefined") {
					continue;
				}
  
				var xmlLang = node.attributes.getNamedItemNS(XML.Namespaces.XML, "lang");
  
				if (xmlLang != null) {
					xmlLang  = xmlLang.value.toLowerCase();
					language = language.toLowerCase();
    
					return xmlLang.indexOf(language) === 0
						&& (language.length == xmlLang.length || language.charAt(xmlLang.length) == '-');
				}
			}

			return false;
		} ),

		

	"http://www.w3.org/2005/xpath-functions number" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(object) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.numberInvalidArgumentsNumber;
			}
			return numberValue(object);
		} ),

		

	"http://www.w3.org/2005/xpath-functions sum" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.sumInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.sumInvalidArgumentType;
			}
			var sum = 0;

			for (var i = 0, len = nodeSet.length; i < len; ++i) {
				sum += numberValue(xmlValue(nodeSet[i]));
			}

			return sum;
		} ),

		

	"http://www.w3.org/2005/xpath-functions floor" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.floorInvalidArgumentsNumber;
			}
			return Math.floor(numberValue(number));
		} ),

		

	"http://www.w3.org/2005/xpath-functions ceiling" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.ceilingInvalidArgumentsNumber;
			}
			return Math.ceil(numberValue(number));
		} ),

		

	"http://www.w3.org/2005/xpath-functions round" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.roundInvalidArgumentsNumber;
			}
			return Math.round(numberValue(number));
		} ),

		

	"http://www.w3.org/2002/xforms power" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(x, y) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.powerInvalidArgumentsNumber;
			}
			return Math.pow(numberValue(x), numberValue(y));
		} ),

		

	"http://www.w3.org/2002/xforms random" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.randomInvalidArgumentsNumber;
			}
			return Math.random();
		} ),

		

	"http://www.w3.org/2002/xforms boolean-from-string" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.booleanFromStringInvalidArgumentsNumber;
			}
			string = stringValue(string);

			switch (string.toLowerCase()) {
				case "true":  case "1": return true;
				case "false": case "0": return false;
				default: return false;
			}
		} ),

		

	"http://www.w3.org/2002/xforms if" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, true,
		function(condition, onTrue, onFalse) {
			if (arguments.length != 3) {
				throw XPathCoreFunctionsExceptions.ifInvalidArgumentsNumber;
			}
			return booleanValue(condition)? onTrue : onFalse;
		} ),

		

	"http://www.w3.org/2002/xforms choose" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, true,
		function(condition, onTrue, onFalse) {
			if (arguments.length != 3) {
				throw XPathCoreFunctionsExceptions.chooseInvalidArgumentsNumber;
			}
			return booleanValue(condition)? onTrue : onFalse;
		} ),

		

	"http://www.w3.org/2005/xpath-functions avg" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.avgInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.avgInvalidArgumentType;
			}
			var sum = XPathCoreFunctions['http://www.w3.org/2005/xpath-functions sum'].evaluate(nodeSet);
			var quant = XPathCoreFunctions['http://www.w3.org/2005/xpath-functions count'].evaluate(nodeSet);
			return sum / quant;
		} ),

		

	"http://www.w3.org/2005/xpath-functions min" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function (nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.minInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.minInvalidArgumentType;
			}
			if (nodeSet.length === 0) {
				return Number.NaN;
			}
    
			var minimum = numberValue(xmlValue(nodeSet[0]));
    
			for (var i = 1, len = nodeSet.length; i < len; ++i) {
				var value = numberValue(xmlValue(nodeSet[i]));
      
				if (isNaN(value)) {
					return Number.NaN;
				}
      
				if (value < minimum) {
					minimum = value;
				}
			}
    
			return minimum;
		} ),

		

	"http://www.w3.org/2005/xpath-functions max" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function (nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.maxInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.maxInvalidArgumentType;
			}
			if (nodeSet.length === 0) {
				return Number.NaN;
			}
    
			var maximum = numberValue(xmlValue(nodeSet[0]));
    
			for (var i = 1, len = nodeSet.length; i < len; ++i) {
				var value = numberValue(xmlValue(nodeSet[i]));
      
				if (isNaN(value)) {
					return Number.NaN;
				}
      
				if (value > maximum) {
					maximum = value;
				}
			}
    
			return maximum;
		} ),

		

	"http://www.w3.org/2002/xforms count-non-empty" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.countNonEmptyInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.countNonEmptyInvalidArgumentType;
			}
			var count = 0;
			
			for (var i = 0, len = nodeSet.length; i < len; ++i) {
				if (xmlValue(nodeSet[i]).length > 0) {
					count++;
				}
			}
			
			return count;
		} ),

		

	"http://www.w3.org/2002/xforms index" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, false,
		function(ctx, id) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.indexInvalidArgumentsNumber;
			}
			var xf = IdManager.find(stringValue(id)).xfElement;
			ctx.addDepElement(xf);
			return xf.index;
		} ),

		

	"http://www.w3.org/2002/xforms nodeindex" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, false,
		function(ctx, id) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.nodeIndexInvalidArgumentsNumber;
			}
			var control = IdManager.find(stringValue(id));
			var node = control.node;
			ctx.addDepElement(control.xfElement);
			
			if (node) {
				ctx.addDepNode(node);
				ctx.addDepElement(document.getElementById(Core.getMeta(node.documentElement ? node.documentElement : node.ownerDocument.documentElement, "model")).xfElement);
			}

			return node? [ node ] : [];
		} ),

		

	"http://www.w3.org/2002/xforms property" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(name) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.propertyInvalidArgumentsNumber;
			}
			name = stringValue(name);

			switch (name) {
				case "version" : return "1.1";
				case "conformance-level" : return "full";
			}
			return "";
		} ),

		

	"http://www.w3.org/2002/xforms instance" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, true,
		function(ctx, idRef) {
			if (arguments.length > 2) {
				throw XPathCoreFunctionsExceptions.instanceInvalidArgumentsNumber;
			}
			var name = idRef ? stringValue(idRef) : "";
			if (name != "") {
				var instance = document.getElementById(name);
				if (!instance) { throw {name: "instance " + name + " not found"}; }
				return [instance.xfElement.doc.documentElement];
			} else {
				return [ctx.node.ownerDocument.documentElement];
			}
		} ),

		

	"http://www.w3.org/2002/xforms now" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length != 0) {
				throw XPathCoreFunctionsExceptions.nowInvalidArgumentsNumber;
			}
			return I8N.format(new Date(), "yyyy-MM-ddThh:mm:ssz", false);
		} ),

		

	"http://www.w3.org/2002/xforms local-date" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length != 0) {
				throw XPathCoreFunctionsExceptions.localDateInvalidArgumentsNumber;
			}
			return I8N.format(new Date(), "yyyy-MM-ddz", true);
		} ),

		

	"http://www.w3.org/2002/xforms local-dateTime" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function() {
			if (arguments.length != 0) {
				throw XPathCoreFunctionsExceptions.localDateTimeInvalidArgumentsNumber;
			}
			return I8N.format(new Date(), "yyyy-MM-ddThh:mm:ssz", true);
		} ),

		

	"http://www.w3.org/2002/xforms days-from-date" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.daysFromDateInvalidArgumentsNumber;
			}
			string = stringValue(string);
			if( !Schema.getType("xsd_:date").validate(string) && !Schema.getType("xsd_:dateTime").validate(string)) {
				return "NaN";
			}
			p = /^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])/;
			c = p.exec(string);
			d = new Date(Date.UTC(c[1], c[2]-1, c[3]));
			return Math.floor(d.getTime()/ 86400000 + 0.000001);
		} ),

		

	"http://www.w3.org/2002/xforms days-to-date" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.daysToDateInvalidArgumentsNumber;
			}
			number = numberValue(number);
			if( isNaN(number) ) {
				return "";
			}
			d = new Date();
			d.setTime(Math.floor(number + 0.000001) * 86400000);
			return I8N.format(d, "yyyy-MM-dd", false);
		} ),

		

	"http://www.w3.org/2002/xforms seconds-from-dateTime" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.secondsFromDateTimeInvalidArgumentsNumber;
			}
			string = stringValue(string);
			if( !Schema.getType("xsd_:dateTime").validate(string)) {
				return "NaN";
			}
			p = /^([12][0-9]{3})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])(\.[0-9]+)?(Z|[+-])?([01][0-9]|2[0-3])?:?([0-5][0-9])?/;
			c = p.exec(string);
			d = new Date(Date.UTC(c[1], c[2]-1, c[3], c[4], c[5], c[6]));
			if(c[8] && c[8] != "Z") {
				d.setUTCMinutes(d.getUTCMinutes() + (c[8] == "+" ? 1 : -1)*(c[9]*60 + c[10]));
			}
			return Math.floor(d.getTime() / 1000 + 0.000001) + (c[7]?c[7]:0);
		} ),

		

	"http://www.w3.org/2002/xforms seconds-to-dateTime" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.secondsToDateTimeInvalidArgumentsNumber;
			}
			number = numberValue(number);
			if( isNaN(number) ) {
				return "";
			}
			d = new Date();
			d.setTime(Math.floor(number + 0.000001) * 1000);
			return I8N.format(d, "yyyy-MM-ddThh:mm:ssz", false);
		} ),

		

	"http://www.w3.org/2002/xforms current" : new XPathFunction(true, XPathFunction.DEFAULT_NONE, true,
		function(ctx) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.currentInvalidArgumentsNumber;
			}
			ctx.addDepNode(ctx.node);
			ctx.addDepElement(document.getElementById(Core.getMeta(ctx.node.documentElement ? ctx.node.documentElement : ctx.node.ownerDocument.documentElement, "model")).xfElement);
			return [ctx.current];
		} ),

		

	"http://www.w3.org/2002/xforms is-valid" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.isValidInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.isValidInvalidArgumentType;
			}
			var valid = true;
        
			for (var i = 0, len = nodeSet.length; valid && i < len; i++) {
				valid = valid && validate_(nodeSet[i]);
			}

			return valid;
		} ),

		

	"http://www.w3.org/2002/xforms is-card-number" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(string) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.isCardNumberInvalidArgumentsNumber;
			}
			string = stringValue(string).trim();
	    var sum = 0;
			var tab = new Array(string.length);
			for (var i = 0, l = string.length; i < l; i++) {
				tab[i] = string.charAt(i) - '0';
				if( tab[i] < 0 || tab[i] > 9 ) {
					return false;
				}
			}
			for (var i = tab.length-2; i >= 0; i -= 2) {
				tab[i] *= 2;
				if( tab[i] > 9 ) {
					tab[i] -= 9;
				}
			}
			for (var i = 0, l = tab.length; i < l; i++) {
				sum += tab[i];
			}
			return sum % 10 == 0;
		} ),

		

	"http://www.w3.org/2002/xforms digest" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(str, algo, enco) {
			if (arguments.length != 2 && arguments.length != 3) {
				throw XPathCoreFunctionsExceptions.digestInvalidArgumentsNumber;
			}
			str = stringValue(str);
			algo = stringValue(algo);
			enco = enco ? stringValue(enco) : "base64";
			switch(algo) {
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
					for(var i = 0; i<l; i++){
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
					for(var i = 0; i<l; i += 16){
						a = H0;
						b = H1;
						c = H2;
						d = H3;
						e = H4;
						for(var t = 0; t<20; t++){
							T = add32(add32(add32(add32(rotl(a,5),(b & c)^(~b & d)),e),0x5a827999),W[t] = t<16 ? msg[t+i] : rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d, d = c, c = rotl(b,30), b = a, a = T;
						}
						for(var t = 20; t<40; t++){
							T = add32(add32(add32(add32(rotl(a,5),b^c^d),e),0x6ed9eba1),W[t] = rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d, d = c, c = rotl(b,30), b = a, a = T;
						}
						for(var t = 40; t<60; t++){
							T = add32(add32(add32(add32(rotl(a,5),(b & c)^(b & d)^(c & d)),e),0x8f1bbcdc),W[t] = rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d, d = c, c = rotl(b,30), b = a, a = T;
						}
						for(var t = 60; t<80; t++){
							T = add32(add32(add32(add32(rotl(a,5),b^c^d),e),0xca62c1d6),W[t] = rotl(W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16],1));
							e = d, d = c, c = rotl(b,30), b = a, a = T;
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
					var b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
					var b12 = function(v) {
						return b64.charAt((v >>> 6) & 0x3F)+b64.charAt(v & 0x3F);
					}
					var b30 = function(v) {
						return b64.charAt(v >>> 24)+b64.charAt((v >>> 18) & 0x3F)+b64.charAt((v >>> 12) & 0x3F)+b64.charAt((v >>> 6) & 0x3F)+b64.charAt(v & 0x3F);
					}
					switch(enco) {
						case "hex" :
							return hex32(H0)+hex32(H1)+hex32(H2)+hex32(H3)+hex32(H4);
							break;
						case "base64" :
							return b30(H0 >>> 2)+b30(((H0 & 0x3) << 28) | (H1 >>> 4))+b30(((H1 & 0xF) << 26) | (H2 >>> 6))+b30(((H2 & 0x3F) << 24) | (H3 >>> 8))+b30(((H3 & 0xFF) << 22) | (H4 >>> 10))+b12((H4 & 0x3FF)<<2)+"=";
							break;
						default :
							break;
					}
					break;
				case 'BASE64':
					var b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
					str = str.replace(/\r\n/g,"\n");
					var l = str.length;
					var str2 = "";
					for (var i = 0; i < l; i++) {
						var c = str.charCodeAt(i);
						str2 += c < 128 ? str.charAt(i) : c > 127 && c < 2048 ? String.fromCharCode(c >> 6 | 192, c & 63 | 128) : String.fromCharCode(c >> 12 | 224, c >> 6 & 63 | 128, c & 63 | 128);
					}
					l = str2.length;
					var res = "";
					for (var i = 0; i < l; i += 3) {
						var c1 = str2.charCodeAt(i);
						var c2 = i + 1 < l ? str2.charCodeAt(i + 1) : 0;
						var c3 = i + 2 < l ? str2.charCodeAt(i + 2) : 0;
						res += b64.charAt(c1 >> 2) + b64.charAt((c1 & 3) << 4 | c2 >> 4) + (i + 1 < l ? b64.charAt((c2 & 15) << 2 | c3 >> 6) : "=") + (i + 2 < l ? b64.charAt(c3 & 63) : "=");
					}
					return res;
					break;
			}
			return "unsupported";
		} ),

		

	"http://www.w3.org/2005/xpath-functions upper-case" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(str) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.upperCaseInvalidArgumentsNumber;
			}
			str = stringValue(str);
			return str.toUpperCase();
		} ),

		

	"http://www.w3.org/2005/xpath-functions lower-case" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(str) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.lowerCaseInvalidArgumentsNumber;
			}
			str = stringValue(str);
			return str.toLowerCase();
		} ),

		

	"http://www.w3.org/2005/xpath-functions distinct-values" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.distinctValuesInvalidArgumentsNumber;
			}
			var nodeSet2 = [];
			var values = {};
			for (var i = 0, len = nodeSet.length; i < len; ++i) {
				var xvalue = xmlValue(nodeSet[i]);
				if (!values[xvalue]) {
					nodeSet2.push(nodeSet[i]);
					values[xvalue] = true;
				}
			}
			return nodeSet2;
		} ),

		

	"http://www.w3.org/2002/xforms transform" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet, xslhref) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.transformInvalidArgumentsNumber;
			}
			xslhref = stringValue(xslhref);
			return Core.transformText(Core.saveXML(nodeSet[0]), xslhref, false);
;
		} ),

		

	"http://www.w3.org/2002/xforms serialize" : new XPathFunction(false, XPathFunction.DEFAULT_NODE, false,
		function(nodeSet) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.serializeInvalidArgumentsNumber;
			}
			if (arguments.length == 1 && typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.serializeInvalidArgumentType;
			}
			if (arguments.length == 0) {
				throw XPathCoreFunctionsExceptions.serializeNoContext;
			}
			return Core.saveXML(nodeSet[0]);
		} ),

		

	"http://www.w3.org/2002/xforms event" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(attribute) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.eventInvalidArgumentsNumber;
			}
			var context = XMLEvents.EventContexts[XMLEvents.EventContexts.length - 1];
			if (context) {
				return context[attribute];
			} else {
				return null;
			}
		} ),

		

	"http://www.w3.org/2005/xpath-functions is-non-empty-array" : new XPathFunction(false, XPathFunction.DEFAULT_NODESET, false,
		function(nodeset) {
			if (arguments.length > 1) {
				throw XPathCoreFunctionsExceptions.isNonEmptyArrayInvalidArgumentsNumber;
			}
			if (typeof nodeset[0] != "object") {
				throw XPathCoreFunctionsExceptions.isNonEmptyArrayInvalidArgumentType;
			}
			return nodeset[0].getAttribute("exsi:maxOccurs") && nodeset[0].getAttribute("xsi:nil") != "true";
		} ),

		

	"http://exslt.org/math abs" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.abs(numberValue(number));
		} ),

		

	"http://exslt.org/math acos" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.acos(numberValue(number));
		} ),

		

	"http://exslt.org/math asin" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.asin(numberValue(number));
		} ),

		

	"http://exslt.org/math atan" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.atan(numberValue(number));
		} ),

		

	"http://exslt.org/math atan2" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number1, number2) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.math2InvalidArgumentsNumber;
			}
			return Math.atan2(numberValue(number1), numberValue(number2));
		} ),

		

	"http://exslt.org/math constant" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(string, number) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.math2InvalidArgumentsNumber;
			}
			var val = MathConstants[stringValue(string)] || "0";
			return parseFloat(val.substr(0, numberValue(number)+2));
		} ),

		

	"http://exslt.org/math cos" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.cos(numberValue(number));
		} ),

		

	"http://exslt.org/math exp" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.exp(numberValue(number));
		} ),

		

	"http://exslt.org/math log" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.log(numberValue(number));
		} ),

		

	"http://exslt.org/math power" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number1, number2) {
			if (arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.math2InvalidArgumentsNumber;
			}
			return Math.pow(numberValue(number1), numberValue(number2));
		} ),

		

	"http://exslt.org/math sin" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.sin(numberValue(number));
		} ),

		

	"http://exslt.org/math sqrt" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.sqrt(numberValue(number));
		} ),

		

	"http://exslt.org/math tan" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(number) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.math1InvalidArgumentsNumber;
			}
			return Math.tan(numberValue(number));
		} ),

		

	"http://www.w3.org/2005/xpath-functions alert" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(arg) {
			if (arguments.length != 1) {
				throw XPathCoreFunctionsExceptions.alertInvalidArgumentsNumber;
			}
			alert(stringValue(arg));
			return arg;
		} ),

		

	"http://www.w3.org/2005/xpath-functions string-join" : new XPathFunction(false, XPathFunction.DEFAULT_NONE, false,
		function(nodeSet, joinString) { 
			if (arguments.length != 1 && arguments.length != 2) {
				throw XPathCoreFunctionsExceptions.stringJoinInvalidArgumentsNumber;
			}
			if (typeof nodeSet != "object") {
				throw XPathCoreFunctionsExceptions.stringJoinInvalidArgumentType;
			}
			var strings = [];
			joinString = joinString || "";
			for (var i = 0, len = nodeSet.length; i < len; i++) {
				strings.push(xmlValue(nodeSet[i]));
			}
			return strings.join(joinString);
		} )
};

XPathCoreFunctionsExceptions = {
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
	stringJoinInvalidArgumentsNumber : {
		name : "string-join() : Invalid number of arguments",
		message : "string-join() function must have one or two arguments"
	},
	stringJoinInvalidArgumentType : {
		name : "string-join() : Invalid type of argument",
		message : "string-join() function must have a nodeset argument"
	}
};

function validate_(node) {
	if (Core.getBoolMeta(node, "notvalid")) {
		return false;
	}
	var atts = node.attributes || [];
	for (var i = 0, len = atts.length; i < len; i++) {
		if (atts[i].nodeName.substr(0,10) != "xsltforms_" && !this.validate_(atts[i])) {
			return false;
		}
	}
	var childs = node.childNodes || [];
	for (var i = 0, len = childs.length; i < len; i++) {
		if (!this.validate_(childs[i])) {
			return false;
		}
	}
	return true;
}
    
	
	
		
		
		
var MAX_DUMP_DEPTH = 30;
 function dumpObj(obj, name, indent, depth) {
	if (depth > MAX_DUMP_DEPTH) {
	 return indent + name + ": <Maximum Depth Reached>\n";
	}
	if (typeof obj == "object") {
	 var child = null;
	 var output = indent + name + "\n";
	 indent += "\t";
	 for (var item in obj) {
		 try {
						child = obj[item];
		 } catch (e) {
						child = "<Unable to Evaluate>";
		 }
		 if (typeof child == "object") {
						output += dumpObj(child, item, indent, depth + 1);
		 } else {
						output += indent + item + ": " + child + "\n";
		 }
	 }
	 return output;
	} else {
				 return obj;
	}
 }
     
	
		
		
function var_dump(data,addwhitespace,safety,level) {
	var rtrn = '';
	var dt,it,spaces = '';
	if(!level) {level = 1;}
	for(var i=0; i<level; i++) {
		 spaces += '   ';
	}//end for i<level
	if(typeof(data) != 'object') {
		 dt = data;
		 if(typeof(data) == 'string') {
				if(addwhitespace == 'html') {
					 dt = dt.replace(/&/g,'&amp;');
					 dt = dt.replace(/>/g,'&gt;');
					 dt = dt.replace(/</g,'&lt;');
				}//end if addwhitespace == html
				dt = dt.replace(/\"/g,'\"');
				dt = '"' + dt + '"';
		 }//end if typeof == string
		 if(typeof(data) == 'function' && addwhitespace) {
				dt = new String(dt).replace(/\n/g,"\n"+spaces);
				if(addwhitespace == 'html') {
					 dt = dt.replace(/&/g,'&amp;');
					 dt = dt.replace(/>/g,'&gt;');
					 dt = dt.replace(/</g,'&lt;');
				}//end if addwhitespace == html
		 }//end if typeof == function
		 if(typeof(data) == 'undefined') {
				dt = 'undefined';
		 }//end if typeof == undefined
		 if(addwhitespace == 'html') {
				if(typeof(dt) != 'string') {
					 dt = new String(dt);
				}//end typeof != string
				dt = dt.replace(/ /g,"&nbsp;").replace(/\n/g,"<br>");
		 }//end if addwhitespace == html
		 return dt;
	}//end if typeof != object && != array
	for (var x in data) {
		 if(safety && (level > safety)) {
				dt = '*RECURSION*';
		 } else {
				try {
					 dt = var_dump(data[x],addwhitespace,safety,level+1);
				} catch (e) {continue;}
		 }//end if-else level > safety
		 it = var_dump(x,addwhitespace,safety,level+1);
		 rtrn += it + ':' + dt + ',';
		 if(addwhitespace) {
				rtrn += '\n'+spaces;
		 }//end if addwhitespace
	}//end for...in
	if(addwhitespace) {
		 rtrn = '{\n' + spaces + rtrn.substr(0,rtrn.length-(2+(level*3))) + '\n' + spaces.substr(0,spaces.length-3) + '}';
	} else {
		 rtrn = '{' + rtrn.substr(0,rtrn.length-1) + '}';
	}//end if-else addwhitespace
	if(addwhitespace == 'html') {
		 rtrn = rtrn.replace(/ /g,"&nbsp;").replace(/\n/g,"<br>");
	}//end if addwhitespace == html
	return rtrn;
}
    
	
	
	