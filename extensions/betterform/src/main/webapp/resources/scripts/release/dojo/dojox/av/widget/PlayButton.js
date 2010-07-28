/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.av.widget.PlayButton"]){dojo._hasResource["dojox.av.widget.PlayButton"]=true;dojo.provide("dojox.av.widget.PlayButton");dojo.require("dijit._Widget");dojo.require("dijit._Templated");dojo.require("dijit.form.Button");dojo.declare("dojox.av.widget.PlayButton",[dijit._Widget,dijit._Templated],{templateString:"<div class=\"PlayPauseToggle Pause\" dojoAttachEvent=\"click:onClick\">\n    <div class=\"icon\"></div>\n</div>\n",postCreate:function $DXO_(){this.showPlay();},setMedia:function $DXP_(_1){this.media=_1;dojo.connect(this.media,"onEnd",this,"showPlay");dojo.connect(this.media,"onStart",this,"showPause");},onClick:function $DXQ_(){if(this._mode=="play"){this.onPlay();}else{this.onPause();}},onPlay:function $DXR_(){if(this.media){this.media.play();}this.showPause();},onPause:function $DXS_(){if(this.media){this.media.pause();}this.showPlay();},showPlay:function $DXT_(){this._mode="play";dojo.removeClass(this.domNode,"Pause");dojo.addClass(this.domNode,"Play");},showPause:function $DXU_(){this._mode="pause";dojo.addClass(this.domNode,"Pause");dojo.removeClass(this.domNode,"Play");}});}