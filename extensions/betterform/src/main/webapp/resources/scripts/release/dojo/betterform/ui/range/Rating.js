/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.range.Rating"]){dojo._hasResource["betterform.ui.range.Rating"]=true;dojo.provide("betterform.ui.range.Rating");dojo.require("dojox.form.Rating");dojo.require("betterform.ui.ControlValue");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.declare("betterform.ui.range.Rating",[betterform.ui.ControlValue,dojox.form.Rating],{postMixInProperties:function $DA3h_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},postCreate:function $DA3i_(){this.inherited(arguments);this.setCurrentValue();},_onFocus:function $DA3j_(){this.inherited(arguments);this.handleOnFocus();},_onBlur:function $DA3k_(){this.inherited(arguments);this.handleOnBlur();},getControlValue:function $DA3l_(){return this.attr("value");},onStarClick:function $DA3m_(_1){this.inherited(arguments);if(this.incremental){this.setControlValue();}},_handleSetControlValue:function $DA3n_(_2){this.setAttribute("value",_2);}});}