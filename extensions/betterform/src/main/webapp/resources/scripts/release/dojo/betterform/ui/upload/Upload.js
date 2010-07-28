/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.upload.Upload"]){dojo._hasResource["betterform.ui.upload.Upload"]=true;dojo.provide("betterform.ui.upload.Upload");dojo.require("betterform.ui.ControlValue");dojo.require("dijit.form.Button");dojo.declare("betterform.ui.upload.Upload",betterform.ui.ControlValue,{postMixInProperties:function $DA6B_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);dojo.removeClass(this.domNode,"xfValue");},_onFocus:function $DA6C_(){this.inherited(arguments);this.handleOnFocus();},_onBlur:function $DA6D_(){this.inherited(arguments);},getControlValue:function $DA6E_(){console.warn("betterform.ui.upload.Upload.getControlValue: Value: ");},_handleSetControlValue:function $DA6F_(_1){console.warn("betterform.ui.upload.Upload._handleSetControlValue: Value: ");}});}