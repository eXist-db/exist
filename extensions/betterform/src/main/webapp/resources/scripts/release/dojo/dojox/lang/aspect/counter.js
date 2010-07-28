/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.lang.aspect.counter"]){dojo._hasResource["dojox.lang.aspect.counter"]=true;dojo.provide("dojox.lang.aspect.counter");(function(){var _1=dojox.lang.aspect;var _2=function(){this.reset();};dojo.extend(_2,{before:function $DAah_(){++this.calls;},afterThrowing:function $DAai_(){++this.errors;},reset:function $DAaj_(){this.calls=this.errors=0;}});_1.counter=function $DAak_(){return new _2;};})();}