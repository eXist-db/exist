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
define("ace/mode/xquery_highlight_rules", function(require, exports, module) {

var oop = require("pilot/oop");
var lang = require("pilot/lang");
var TextHighlightRules = require("ace/mode/text_highlight_rules").TextHighlightRules;

var XQueryHighlightRules = function() {

	var keywords = lang.arrayToMap(
		("return|for|let|declare|function|xquery|version|option|namespace|import|module|" +
		 "if|then|else|as|and|or|typeswitch|case").split("|")
    );

    // regexp must not have capturing parentheses
    // regexps are ordered -> the first match is used

    this.$rules = {
        start : [ {
            token : "text",
            regex : "<\\!\\[CDATA\\[",
            next : "cdata"
        }, {
            token : "xml_pe",
            regex : "<\\?.*?\\?>"
        }, {
            token : "comment",
            regex : "<\\!--",
            next : "comment"
		}, {
			token : "comment",
			regex : "\\(:",
			next : "comment"
        }, {
            token : "text", // opening tag
            regex : "<\\/?",
            next : "tag"
        }, {
            token : "constant", // number
            regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
		}, {
            token : "variable", // variable
            regex : "\\$[a-zA-Z_][a-zA-Z0-9_\\-:]*\\b"
		}, {
			token: "string",
			regex : '".*?"'
		}, {
			token: "string",
			regex : "'.*?'"
        }, {
            token : "text",
            regex : "\\s+"
        }, {
			token : function(value) {
		        if (keywords[value])
		            return "keyword";
		        else
		            return "identifier";
			},
			regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
		} ],

        tag : [ {
            token : "text",
            regex : ">",
            next : "start"
        }, {
            token : "keyword",
            regex : "[-_a-zA-Z0-9:]+"
        }, {
            token : "text",
            regex : "\\s+"
        }, {
            token : "string",
            regex : '".*?"'
        }, {
            token : "string",
            regex : "'.*?'"
        } ],

        cdata : [ {
            token : "text",
            regex : "\\]\\]>",
            next : "start"
        }, {
            token : "text",
            regex : "\\s+"
        }, {
            token : "text",
            regex : "(?:[^\\]]|\\](?!\\]>))+"
        } ],

        comment : [ {
            token : "comment",
            regex : ".*?-->",
            next : "start"
        }, {
			token: "comment",
			regex : ".*:\\)",
			next : "start"
        }, {
            token : "comment",
            regex : ".+"
		} ]
    };
};

oop.inherits(XQueryHighlightRules, TextHighlightRules);

exports.XQueryHighlightRules = XQueryHighlightRules;
});

define("ace/mode/xquery", function(require, exports, module) {

var oop = require("pilot/oop");
var TextMode = require("ace/mode/text").Mode;
var Tokenizer = require("ace/tokenizer").Tokenizer;
var XQueryHighlightRules = require("ace/mode/xquery_highlight_rules").XQueryHighlightRules;

var Mode = function() {
    this.$tokenizer = new Tokenizer(new XQueryHighlightRules().getRules());
};

oop.inherits(Mode, TextMode);

(function() {

    this.getNextLineIndent = function(state, line, tab) {
    	var indent = this.$getIndent(line);
    	var match = line.match(/\s*(?:then|else|return)\s*$/);
    	if (match)
    		indent += tab;
        return indent;
    };

}).call(Mode.prototype);

exports.Mode = Mode;
});