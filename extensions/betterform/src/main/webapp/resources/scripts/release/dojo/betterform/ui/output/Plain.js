/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.output.Plain"]){dojo._hasResource["betterform.ui.output.Plain"]=true;dojo.provide("betterform.ui.output.Plain");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("betterform.ui.ControlValue");dojo.declare("betterform.ui.output.Plain",betterform.ui.ControlValue,{id:"",value:"",templateString:"<span id=\"${id}\" dojoAttachPoint=\"containerNode\"></span>",postMixInProperties:function $DA3W_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},postCreate:function $DA3X_(){this.inherited(arguments);this.setCurrentValue();},_onFocus:function $DA3Y_(){this.inherited(arguments);this.handleOnFocus();},_onBlur:function $DA3Z_(){this.inherited(arguments);this.handleOnBlur();},getControlValue:function $DA3a_(){return this.containerNode.innerHTML;},_handleSetControlValue:function $DA3b_(_1){this.containerNode.innerHTML=_1;},applyState:function $DA3c_(){}});}