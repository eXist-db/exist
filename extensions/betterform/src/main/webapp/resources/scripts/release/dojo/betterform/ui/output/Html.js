/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.output.Html"]){dojo._hasResource["betterform.ui.output.Html"]=true;dojo.provide("betterform.ui.output.Html");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("betterform.ui.ControlValue");dojo.declare("betterform.ui.output.Html",betterform.ui.ControlValue,{templateString:"<span id=\"${id}\" dojoAttachPoint=\"containerNode\"></span>",postMixInProperties:function $DA3B_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},postCreate:function $DA3C_(){this.containerNode.innerHTML=this.value;},_onFocus:function $DA3D_(){this.inherited(arguments);this.handleOnFocus();},_onBlur:function $DA3E_(){this.inherited(arguments);this.handleOnBlur();},getControlValue:function $DA3F_(){return this.containerNode.innerHTML;},applyState:function $DA3G_(){},_handleSetControlValue:function $DA3H_(_1){this.containerNode.innerHTML=_1;}});}