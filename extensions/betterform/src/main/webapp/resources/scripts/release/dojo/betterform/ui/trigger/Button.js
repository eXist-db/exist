/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.trigger.Button"]){dojo._hasResource["betterform.ui.trigger.Button"]=true;dojo.provide("betterform.ui.trigger.Button");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("dijit.form.Button");dojo.require("betterform.ui.ControlValue");dojo.declare("betterform.ui.trigger.Button",[betterform.ui.ControlValue,dijit.form.Button],{buildRendering:function $DA5x_(){this.inherited(arguments);var _1=dojo.attr(this.srcNodeRef,"source");if(_1!=undefined&&_1!=""){var _2=document.createElement("img");dojo.attr(_2,"src",_1);this.iconNode.appendChild(_2);this.showLabel=false;}},postMixInProperties:function $DA5y_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},getControlValue:function $DA5z_(){return dojo.attr(this.domNode,"value");},_handleSetControlValue:function $DA50_(_3){console.warn("TBD: betterform.ui.trigger.Button._handleSetControlValue: Value: ",_3);},onClick:function $DA51_(e){fluxProcessor.dispatchEvent(this.xfControlId);},_setLabel:function $DA52_(_5){dojo.byId(this.id+"_label").innerHTML=_5;}});}