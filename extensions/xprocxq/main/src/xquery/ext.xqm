xquery version "1.0" encoding "UTF-8";
module namespace ext = "http://xproc.net/xproc/ext";
(: ------------------------------------------------------------------------------------- 

	ext.xqm - implements all xprocxq specific extension steps.
	
---------------------------------------------------------------------------------------- :)


(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace xproc = "http://xproc.net/xproc";

declare namespace t = "http://xproc.org/ns/testsuite";

(: Module Imports :)
import module namespace u = "http://xproc.net/xproc/util";

(: -------------------------------------------------------------------------- :)

(: Module Vars :)
declare variable $ext:pre := util:function(xs:QName("ext:pre"), 3);
declare variable $ext:post := util:function(xs:QName("ext:post"), 3);
declare variable $ext:xproc := util:function(xs:QName("ext:xproc"), 3);
declare variable $ext:xsltforms := util:function(xs:QName("ext:xsltforms"), 3);


(: -------------------------------------------------------------------------- :)
declare function ext:pre($primary,$secondary,$options){
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function ext:post($primary,$secondary,$options){
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function ext:xproc($primary,$secondary,$options){
(: -------------------------------------------------------------------------- :)
(: NOTE - this function needs to be defined here, but use-function in xproc.xqm :)
    ()
};


(:-------------------------------------------------------------------------- :)
declare function ext:xsltforms($primary,$secondary,$options){
(: TODO- unsure about the logic of this :)
(: -------------------------------------------------------------------------- :)
(
  <?xml-stylesheet href="/exist/xforms/xsltforms/xsltforms.xsl"
type="text/xsl"?>,
  document{u:get-primary($primary)}
)
};



(: -------------------------------------------------------------------------- :)