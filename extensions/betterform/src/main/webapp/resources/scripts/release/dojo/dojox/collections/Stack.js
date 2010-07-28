/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.collections.Stack"]){dojo._hasResource["dojox.collections.Stack"]=true;dojo.provide("dojox.collections.Stack");dojo.require("dojox.collections._base");dojox.collections.Stack=function $DdD_(_1){var q=[];if(_1){q=q.concat(_1);}this.count=q.length;this.clear=function $Dc3_(){q=[];this.count=q.length;};this.clone=function $Dc4_(){return new dojox.collections.Stack(q);};this.contains=function $Dc5_(o){for(var i=0;i<q.length;i++){if(q[i]==o){return true;}}return false;};this.copyTo=function $Dc6_(_5,i){_5.splice(i,0,q);};this.forEach=function $Dc7_(fn,_8){dojo.forEach(q,fn,_8);};this.getIterator=function $Dc8_(){return new dojox.collections.Iterator(q);};this.peek=function $Dc9_(){return q[(q.length-1)];};this.pop=function $DdA_(){var r=q.pop();this.count=q.length;return r;};this.push=function $DdB_(o){this.count=q.push(o);};this.toArray=function $DdC_(){return [].concat(q);};};}