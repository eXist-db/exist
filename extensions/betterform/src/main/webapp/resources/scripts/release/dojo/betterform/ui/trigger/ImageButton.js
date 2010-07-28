/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.trigger.ImageButton"]){dojo._hasResource["betterform.ui.trigger.ImageButton"]=true;dojo.provide("betterform.ui.trigger.ImageButton");dojo.require("betterform.ui.trigger.Button");dojo.declare("betterform.ui.trigger.ImageButton",[betterform.ui.trigger.Button],{buildRendering:function $DA53_(){this.inherited(arguments);var _1=dojo.query(".xfValue",this.srcNodeRef)[0].innerHTML;if(_1!=undefined&&_1!=""){var _2=document.createElement("img");dojo.attr(_2,"src",_1);this.iconNode.appendChild(_2);this.showLabel=false;}}});}