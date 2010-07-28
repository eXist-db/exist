/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["betterform.ui.container.AccordionSwitchPane"]){dojo._hasResource["betterform.ui.container.AccordionSwitchPane"]=true;dojo.provide("betterform.ui.container.AccordionSwitchPane");dojo.require("betterform.ui.container.Container");dojo.declare("betterform.ui.container.AccordionSwitchPane",dijit.layout.AccordionPane,{caseId:"null",_onTitleClick:function $DA06_(){this.inherited(arguments);var _1="t-"+this.caseId;fluxProcessor.dispatchEvent(_1);}});}