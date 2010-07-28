/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.container.Switch"]){dojo._hasResource["betterform.ui.container.Switch"]=true;dojo.provide("betterform.ui.container.Switch");dojo.require("dijit._Widget");dojo.require("betterform.ui.container.Container");dojo.declare("betterform.ui.container.Switch",betterform.ui.container.Container,{handleStateChanged:function $DA1r_(_1){},toggleCase:function $DA1s_(_2){if(_2.deselected!=undefined){var _3=dojo.byId(_2.deselected);if(dojo.hasClass(_3,"xfSelectedCase")){dojo.removeClass(_3,"xfSelectedCase");}dojo.addClass(_3,"xfDeselectedCase");}if(_2.selected){var _4=dojo.byId(_2.selected);if(dojo.hasClass(_4,"xfDeselectedCase")){dojo.removeClass(_4,"xfDeselectedCase");}dojo.addClass(_4,"xfSelectedCase");}}});}