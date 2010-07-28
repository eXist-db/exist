/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.fx.ext-dojo.NodeList"]){dojo._hasResource["dojox.fx.ext-dojo.NodeList"]=true;dojo.provide("dojox.fx.ext-dojo.NodeList");dojo.experimental("dojox.fx.ext-dojo.NodeList");dojo.require("dojo.NodeList-fx");dojo.require("dojox.fx");dojo.extend(dojo.NodeList,{sizeTo:function $D3H_(_1){return this._anim(dojox.fx,"sizeTo",_1);},slideBy:function $D3I_(_2){return this._anim(dojox.fx,"slideBy",_2);},highlight:function $D3J_(_3){return this._anim(dojox.fx,"highlight",_3);},fadeTo:function $D3K_(_4){return this._anim(dojo,"_fade",_4);},wipeTo:function $D3L_(_5){return this._anim(dojox.fx,"wipeTo",_5);}});}