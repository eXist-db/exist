/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.textarea.DojoEditor"]){dojo._hasResource["betterform.ui.textarea.DojoEditor"]=true;dojo.provide("betterform.ui.textarea.DojoEditor");dojo.require("betterform.ui.ControlValue");dojo.require("dijit.form.Textarea");dojo.declare("betterform.ui.textarea.DojoEditor",[betterform.ui.ControlValue,dijit.form.Textarea],{postMixInProperties:function $DA44_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},postCreate:function $DA45_(){this.inherited(arguments);this.setCurrentValue();},_onFocus:function $DA46_(){this.inherited(arguments);},_onBlur:function $DA47_(){this.inherited(arguments);this.handleOnBlur();},getControlValue:function $DA48_(){return this._getValueAttr();},_onInput:function $DA49_(){this.inherited(arguments);if(this.incremental){this.setControlValue();}},_handleSetControlValue:function $DA5A_(_1){this._setValueAttr(_1);}});}