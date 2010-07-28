/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.output.Image"]){dojo._hasResource["betterform.ui.output.Image"]=true;dojo.provide("betterform.ui.output.Image");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("betterform.ui.ControlValue");dojo.declare("betterform.ui.output.Image",betterform.ui.ControlValue,{src:"",alt:"",templateString:"<img src=\"${src}\" alt=\"${alt}\" class=\"xfValue\"/>",postMixInProperties:function $DA3I_(){this.inherited(arguments);this.applyProperties(dijit.byId(this.xfControlId),this.srcNodeRef);},_onFocus:function $DA3J_(){this.inherited(arguments);this.handleOnFocus();},_onBlur:function $DA3K_(){this.inherited(arguments);this.handleOnBlur();},getControlValue:function $DA3L_(){return dojo.attr(this.domNode,"src");},_handleSetControlValue:function $DA3M_(_1){dojo.attr(this.domNode,"src",_1);},applyState:function $DA3N_(){}});}