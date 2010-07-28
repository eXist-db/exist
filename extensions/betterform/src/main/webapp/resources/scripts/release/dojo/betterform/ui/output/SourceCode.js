/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.output.SourceCode"]){dojo._hasResource["betterform.ui.output.SourceCode"]=true;dojo.provide("betterform.ui.output.SourceCode");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("dojox.highlight");dojo.require("dojox.highlight.languages.xml");dojo.require("betterform.ui.ControlValue");dojo.require("betterform.ui.output.Html");dojo.declare("betterform.ui.output.SourceCode",betterform.ui.output.Html,{postCreate:function $DA3d_(){this.containerNode.innerHTML=this.value;this.load_css("/betterform/resources/scripts/dojox/highlight/resources/highlight.css");this.load_css("/betterform/resources/scripts/dojox/highlight/resources/pygments/pastie.css");this.highlight();},_handleSetControlValue:function $DA3e_(_1){this.inherited(arguments);this.containerNode.innerHTML=_1;this.highlight();},highlight:function $DA3f_(){dojo.query("code",this.containerNode).forEach(dojox.highlight.init);},load_css:function $DA3g_(_2){var _3=document.createElement("link");_3.href=_2;_3.rel="stylesheet";_3.type="text/css";document.body.appendChild(_3);}});}