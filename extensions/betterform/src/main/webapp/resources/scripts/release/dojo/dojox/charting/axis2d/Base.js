/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.charting.axis2d.Base"]){dojo._hasResource["dojox.charting.axis2d.Base"]=true;dojo.provide("dojox.charting.axis2d.Base");dojo.require("dojox.charting.Element");dojo.declare("dojox.charting.axis2d.Base",dojox.charting.Element,{constructor:function $DYT_(_1,_2){this.vertical=_2&&_2.vertical;},clear:function $DYU_(){return this;},initialized:function $DYV_(){return false;},calculate:function $DYW_(_3,_4,_5){return this;},getScaler:function $DYX_(){return null;},getTicks:function $DYY_(){return null;},getOffsets:function $DYZ_(){return {l:0,r:0,t:0,b:0};},render:function $DYa_(_6,_7){return this;}});}