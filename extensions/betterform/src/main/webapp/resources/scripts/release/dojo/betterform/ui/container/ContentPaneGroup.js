/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.container.ContentPaneGroup"]){dojo._hasResource["betterform.ui.container.ContentPaneGroup"]=true;dojo.provide("betterform.ui.container.ContentPaneGroup");dojo.require("dijit._Widget");dojo.require("betterform.ui.container.Container");dojo.require("dijit.layout.ContentPane");dojo.declare("betterform.ui.container.ContentPaneGroup",[betterform.ui.container.Container,dijit.layout.ContentPane],{buildRendering:function $DA1M_(){this.inherited(arguments);betterform.ui.util.setDefaultClasses(this.domNode);},handleStateChanged:function $DA1N_(_1){this.inherited(arguments);}});}