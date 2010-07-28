/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.textarea.HtmlEditor"]){dojo._hasResource["betterform.ui.textarea.HtmlEditor"]=true;dojo.provide("betterform.ui.textarea.HtmlEditor");dojo.require("betterform.ui.ControlValue");dojo.require("dijit.Editor");dojo.declare("betterform.ui.textarea.HtmlEditor",[betterform.ui.ControlValue,dijit.Editor],{buildRendering:function $DA5B_(){this.domNode=this.srcNodeRef;this.setCurrentValue(this.srcNodeRef.innerHTML);this._attachTemplateNodes(this.domNode);},postMixInProperties:function $DA5C_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},postCreate:function $DA5D_(){this.inherited(arguments);},_onFocus:function $DA5E_(){this.inherited(arguments);this.handleOnFocus();},_onBlur:function $DA5F_(){this.inherited(arguments);this.handleOnBlur();},onDisplayChanged:function $DA5G_(e){this.inherited(arguments);if(this.incremental){this.setControlValue();}},getControlValue:function $DA5H_(){return this.getValue();},_handleSetControlValue:function $DA5I_(_2){this.setValue(_2);}});}