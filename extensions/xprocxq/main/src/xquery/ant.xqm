xquery version "1.0" encoding "UTF-8";
module namespace a = "http://xproc.net/xproc/a";
(: ------------------------------------------------------------------------------------- 
 
	ant.xqm - An exp module which uses Apache Ant.

---------------------------------------------------------------------------------------- :)


declare variable $a:test := util:function(xs:QName("a:test"), 0);

declare function a:test(){
	<test/>
};


