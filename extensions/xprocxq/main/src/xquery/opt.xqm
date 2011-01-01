xquery version "1.0" encoding "UTF-8";
module namespace opt = "http://xproc.net/xproc/opt";
(: ------------------------------------------------------------------------------------- 

	opt.xqm - Implements all xproc optional steps.
	
---------------------------------------------------------------------------------------- :)


(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace comp="http://xproc.net/xproc/comp";
declare namespace xproc = "http://xproc.net/xproc";

(: Module Imports :)
import module namespace u = "http://xproc.net/xproc/util" at "resource:net/xproc/xprocxq/src/xquery/util.xqm";
import module namespace const = "http://xproc.net/xproc/const" at "resource:net/xproc/xprocxq/src/xquery/const.xqm";
(: import module namespace xslfo = "http://exist-db.org/xquery/xslfo"; (: for p:xsl-formatter :) :)

(: -------------------------------------------------------------------------- :)

(: Module Vars :)
declare variable $opt:exec := util:function(xs:QName("opt:exec"), 4);
declare variable $opt:hash := util:function(xs:QName("opt:hash"), 4);
declare variable $opt:uuid := util:function(xs:QName("opt:uuid"), 4);
declare variable $opt:www-form-urldecode := util:function(xs:QName("opt:www-form-urldecode"), 4);
declare variable $opt:www-form-urlencode := util:function(xs:QName("opt:www-form-urlencode"), 4);
declare variable $opt:validate-with-xml-schema := util:function(xs:QName("opt:validate"), 4);
declare variable $opt:validate-with-schematron := util:function(xs:QName("opt:validate"), 4);
declare variable $opt:validate-with-relax-ng := util:function(xs:QName("opt:validate"), 4);
declare variable $opt:xquery := util:function(xs:QName("opt:xquery"), 4);
declare variable $opt:xsl-formatter :=util:function(xs:QName("opt:xsl-formatter"), 4);



(: -------------------------------------------------------------------------- :)
declare function opt:exec($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
    u:outputResultElement(<test3/>)
};

(: -------------------------------------------------------------------------- :)
declare function opt:hash($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $algorithm := u:get-option('algorithm',$options,$v)
let $matchresult :=  u:xsltmatchpattern($match,$v,$variables)
let $replacement := u:hash(string($matchresult),$algorithm)
return
	u:replace-matching-elements($v/*,$matchresult,$replacement)
};


(: -------------------------------------------------------------------------- :)
declare function opt:uuid($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $replacement := u:uuid()
return
	u:replace-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$replacement)
};


(: -------------------------------------------------------------------------- :)
declare function opt:www-form-urldecode($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $value := u:get-option('value',$options,$v)
let $params := tokenize($value,'&amp;')
return
       <c:param-set xmlns:c="http://www.w3.org/ns/xproc-step">
        {
        for $child in $params
        return
            <c:param name="{substring-before($child,'=')}" value="{substring-after($child,'=')}"/>
        }
       </c:param-set>
};


(: -------------------------------------------------------------------------- :)
declare function opt:www-form-urlencode($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function opt:validate($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $schema := u:get-secondary('schema',$secondary)
let $assert-valid := u:get-option('assert-valid',$options,$v)
let $validation-result :=  validation:jing($v,$schema/*)

return
    if($assert-valid eq 'false' ) then
     $v
    else if ($assert-valid eq 'true' and xs:boolean($validation-result)) then
     $v
    else
	    u:dynamicError('err:XC0053',concat(": invalid ",u:serialize($v,$const:TRACE_SERIALIZE)))
};


(: -------------------------------------------------------------------------- :)
declare function opt:xsl-formatter($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $href-uri := u:get-option('href',$options,$v)
let $name := tokenize($href-uri, "/")[last()]
let $path := substring-before($href-uri,$name)
let $query := concat("xslfo:render(",u:serialize($v,$const:DEFAULT_SERIALIZE),",$const:pdf-mimetype,<parameters/>)")
let $pdf := u:eval($query)
let $store := if(starts-with($path,'file://')) then
				let $query := concat("file:serialize('",
									 $pdf,
								 	 "','",
									 substring-after($href-uri,'file://'),
									 "','method=text')")
				return
				(: u:eval($query) :)
				u:dynamicError('err:XC0050',"p:xsl-formatter cannot store pdf as xprocxq does not yet support file:// protocol yet (soon!).")
				
			  else
				xmldb:store($path,$name,$pdf)
				
return
	if($store) then
		u:outputResultElement(concat($path,$name))
	else
		u:dynamicError('err:XC0050',"p:xsl-formatter cannot store pdf.")
};


(: -------------------------------------------------------------------------- :)
declare function opt:xquery($primary,$secondary,$options,$variables) {
(: -------------------------------------------------------------------------- :)
u:assert(exists(u:get-secondary('query',$secondary)/c:query),'p:input query is required'),
(:TODO: need to sort out multiple c:query elements and implied cdata sections :)
	let $v := u:get-primary($primary)
    let $xquery := u:get-secondary('query',$secondary)/c:query
	let $query := if ($xquery/@xproc:escape = 'true') then
			u:serialize($xquery/node(),$const:TRACE_SERIALIZE)
		else
			$xquery/node()
    let $preserve-context := u:get-option('xproc:preserve-context',$options,$v)
    return
        if ($preserve-context eq 'true') then
            u:xquery-with-context($query,$v,$variables)
        else
            u:xquery($query,$v,$variables)
};


(: -------------------------------------------------------------------------- :)
