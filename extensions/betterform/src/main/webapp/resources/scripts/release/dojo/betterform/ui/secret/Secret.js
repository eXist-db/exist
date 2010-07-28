/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.secret.Secret"]){dojo._hasResource["betterform.ui.secret.Secret"]=true;dojo.provide("betterform.ui.secret.Secret");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("dijit.form.TextBox");dojo.require("betterform.ui.ControlValue");dojo.require("betterform.ui.input.TextField");dojo.declare("betterform.ui.secret.Secret",[betterform.ui.ControlValue,betterform.ui.input.TextField],{type:"password"});}