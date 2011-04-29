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
var eXide = eXide || {};

/**
 * Namespace function. Required by all other classes.
 */
eXide.namespace = function (ns_string) {
	var parts = ns_string.split('.'),
		parent = eXide,
		i;
	if (parts[0] == "eXide") {
		parts = parts.slice(1);
	}
	
	for (i = 0; i < parts.length; i++) {
		// create a property if it doesn't exist
		if (typeof parent[parts[i]] == "undefined") {
			parent[parts[i]] = {};
		}
		parent = parent[parts[i]];
	}
	return parent;
	
}

eXide.namespace("eXide.util");

/**
 * Static utility methods.
 */
eXide.util = (function () {

	var stack_bottomright = {"dir1": "up", "dir2": "left", "firstpos1": 15, "firstpos2": 15};
	
	return {
		
		/**
		 * Display popup window for selecting an entry from a HTML ul list.
		 * The user can cycle through the entries using the up/down keys.
		 * Pressing return selects an item and passes it to the onSelect
		 * callback function. Pressing any other key closes the popup and
		 * calls onSelect with a null argument.
		 */
		popup: function (div, tooltipDiv, data, onSelect) {
			var container = $(div);
			var tooltips = tooltipDiv ? $(tooltipDiv) : null;
			
			function updateTooltip(node) {
				if (tooltips) {
					tooltips.empty();
					node.find(".tooltip").each(function () {
						tooltips.html($(this).html());
					});
				}
			}
			
			function compareLabels (a, b) {
				return (a.label == b.label) ? 0 : (a.label > b.label) ? 1 : -1;
			}
			
			data.sort(compareLabels);
			
			container.css("display", "block").focus();
			var ul = document.createElement("ul");
			for (var i = 0; i < data.length; i++) {
				var li = document.createElement("li");
				li.appendChild(document.createTextNode(data[i].label));
				if (data[i].tooltip) {
					var help = document.createElement("span");
					help.className = "tooltip";
					help.appendChild(document.createTextNode(data[i].tooltip));
					
					li.appendChild(help);
				}
				ul.appendChild(li);
			}
			container.html(ul);
			
			if (tooltips) {
				tooltips.css("display", "block");
			}
			
			var selection = container.find("ul li:first").addClass('selection');
			updateTooltip(selection);
			
			var ch = container.height();

			container.keydown(function (ev) {
				if (ev.which == 40) {
					var next = selection.next();
					if (next.length > 0) {
						selection.removeClass("selection");
						next.addClass("selection");
						selection = next;

						if (next.position().top > ch)
							next.get(0).scrollIntoView();
						
						updateTooltip(next);
					}
				} else if (ev.which == 38) {
					var prev = selection.prev();
					if (prev.length > 0) {
						selection.removeClass("selection");
						prev.addClass("selection");
						selection = prev;

						if (prev.position().top < 0)
							prev.get(0).scrollIntoView();
						
						updateTooltip(prev);
					}
				} else if (ev.which == 13) {
					var pos = container.find("li").index(selection);
					
					// close container and unbind event
					container.css("display", "none");
					if (tooltips)
						tooltips.css("display", "none");
					container.unbind(ev);
					
					// pass content to callback function
					onSelect.call(null, data[pos]);
					
				} else {
					// other key pressed: close container and unbind event
					container.css("display", "none");
					if (tooltips)
						tooltips.css("display", "none");
					container.unbind(ev);
					
					// apply callback with null argument 
					onSelect.call(null, null);
					return true;
				}
				return false;
			});
		},
		
		/**
		 * Check if browser supports HTML5 local storage
		 */
		supportsHtml5Storage: function () {
			try {
				return 'localStorage' in window && window['localStorage'] !== null;
			} catch (e) {
				return false;
			}
		},
		
		/**
		 * Normalize a collection path. Remove xmldb: part, resolve ..
		 */
		normalizePath: function (path) {
			path = path.replace(/^xmldb:exist:\/\//, "");
			var newComponents = [];
			var components = path.split("/");
			for (var i = components.length - 1; i > -1; i--) {
				if (components[i] == "..") {
					i--;
				} else {
					newComponents.push(components[i]);
				}
			}
			return newComponents.reverse().join("/");
		},
		
		/**
		 * Parse a function signature and transform it into function call.
		 * Removes type declarations.
		 */
		parseSignature: function (signature) { 
			var p = signature.indexOf("(");
			if (p > -1) {
				var parsed = signature.substring(0, p + 1);
				signature = signature.substring(p);
				var vars = signature.match(/\$[\w:-_]+/g);
				if (vars) {
					for (var i = 0; i < vars.length; i++) {
						if (i > 0)
							parsed += ", ";
						parsed += vars[i];
					}
				}
				parsed += ")";
				return parsed;
			}
			return signature;
		},
		
		/**
		 * Display a message using pnotify.
		 */
		message: function(message) {
			$.pnotify({
				pnotify_text: message,
				pnotify_shadow: true,
				pnotify_hide: true,
				pnotify_closer: true,
				pnotify_opacity: .75,
				pnotify_addclass: "stack-bottomright",
				pnotify_stack: stack_bottomright
			});
		},
		
		error: function(message, title) {
			var opts = {
				pnotify_text: message,
				pnotify_type: 'error',
				pnotify_shadow: true,
				pnotify_hide: true,
				pnotify_addclass: "stack-bottomright",
				pnotify_stack: stack_bottomright
			};
			if (title) {
				opts.pnotify_title = title;
			}
			$.pnotify(opts);
		}
	};
	
}());

eXide.namespace("eXide.util.Dialog");

/**
 * Singleton object: message, confirm and error dialogs.
 * 
 * @param name
 * @param path
 * @param mimeType
 */
eXide.util.Dialog = (function () {
	
	var messageDialog;
	var warnIcon = "images/error.png";
	var infoIcon = "images/information.png";
	
	$(document).ready(function() {
		$(document.body).append(
				"<div id=\"eXide-dialog-message\">" +
				"	<img id=\"eXide-dialog-message-icon\" src=\"images/error.png\"/>" +
				"	<div id=\"eXide-dialog-message-body\"></div>" +
				"</div>"
		);
		messageDialog = $("#eXide-dialog-message");
		
		messageDialog.dialog({
			modal: true,
			autoOpen: false,
			buttons: {
				"OK": function () { $(this).dialog("close"); }
			}
		});
	});
	
	return {
		
		message: function (title, msg) {
			messageDialog.dialog("option", "title", title);
			$("#eXide-dialog-message-body").html(msg);
			$("#eXide-dialog-message-icon").attr("src", infoIcon);
			messageDialog.dialog("open");
		},
		
		warning: function (title, msg) {
			messageDialog.dialog("option", "title", title);
			$("#eXide-dialog-message-body").html(msg);
			$("#eXide-dialog-message-icon").attr("src", warnIcon);
			messageDialog.dialog("open");
		}
	}
}());

eXide.namespace("eXide.util.mimeTypes");

/**
 * Singleton object: maintains a mapping of mime-types
 * to languages for sytnax highlighting.
 * 
 * @param name
 * @param path
 * @param mimeType
 */
eXide.util.mimeTypes = (function () {
	
    var TYPES = {
        'xml': ['text/xml', 'application/xml', 'application/xhtml+xml'],
        'xquery': ['application/xquery'],
        'css': ['text/css'],
        'html': ['text/html'],
        'javascript': ['application/x-javascript']
    };

    return {
    
    	getMime: function (mimeType) {
	    	var p = mimeType.indexOf(";");
	    	if (p > -1)
	    		mimeType = mimeType.substring(0, p);
	    	return mimeType;
	    },
    
	    getLangFromMime: function(mimeType) {
	        for (var lang in TYPES) {
	            var syn = TYPES[lang];
	            for (var i = 0; i < syn.length; i++) {
	                if (mimeType == syn[i])
	                    return lang;
	            }
	        }
	        return 'xquery';
	    },

	    getMimeFromLang: function (lang) {
	        var types = TYPES[lang];
	        if (types)
	            return types[0];
	        else
	            return 'application/xquery';
	    }
    }
}());

/* Debug and logging functions */
(function($) {
    $.log = function() {
//    	if (typeof console == "undefined" || typeof console.log == "undefined") {
//    		console.log( Array.prototype.slice.call(arguments) );
        if(window.console && window.console.log) {
            console.log.apply(window.console,arguments)
        }
    };
    $.fn.log = function() {
        $.log(this);
        return this;
    }
})(jQuery);