/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.FisheyeLite"]){dojo._hasResource["betterform.ui.FisheyeLite"]=true;dojo.provide("betterform.ui.FisheyeLite");dojo.require("dijit._Widget");dojo.require("dojox.widget.FisheyeLite");dojo.declare("betterform.ui.FisheyeLite",dojox.widget.FisheyeLite,{durationIn:350,postCreate:function $DA2Z_(){this.inherited(arguments);this._target=dojo.query(".xfControl",this.srcNodeRef)[0];this._makeAnims();this.connect(this.domNode,"onmouseover","show");this.connect(this.domNode,"onmouseout","hide");this.connect(this._target,"onclick","onClick");}});}