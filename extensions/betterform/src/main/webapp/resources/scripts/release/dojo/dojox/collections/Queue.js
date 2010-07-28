/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.collections.Queue"]){dojo._hasResource["dojox.collections.Queue"]=true;dojo.provide("dojox.collections.Queue");dojo.require("dojox.collections._base");dojox.collections.Queue=function $Dcd_(_1){var q=[];if(_1){q=q.concat(_1);}this.count=q.length;this.clear=function $DcT_(){q=[];this.count=q.length;};this.clone=function $DcU_(){return new dojox.collections.Queue(q);};this.contains=function $DcV_(o){for(var i=0;i<q.length;i++){if(q[i]==o){return true;}}return false;};this.copyTo=function $DcW_(_5,i){_5.splice(i,0,q);};this.dequeue=function $DcX_(){var r=q.shift();this.count=q.length;return r;};this.enqueue=function $DcY_(o){this.count=q.push(o);};this.forEach=function $DcZ_(fn,_a){dojo.forEach(q,fn,_a);};this.getIterator=function $Dca_(){return new dojox.collections.Iterator(q);};this.peek=function $Dcb_(){return q[0];};this.toArray=function $Dcc_(){return [].concat(q);};};}