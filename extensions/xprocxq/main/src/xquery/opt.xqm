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
import module namespace u = "http://xproc.net/xproc/util";
import module namespace const = "http://xproc.net/xproc/const";
(: import module namespace xslfo = "http://exist-db.org/xquery/xslfo"; (: for p:xsl-formatter :) :)

(: -------------------------------------------------------------------------- :)

(: Module Vars :)
declare variable $opt:exec := util:function(xs:QName("opt:exec"), 3);
declare variable $opt:hash := util:function(xs:QName("opt:hash"), 3);
declare variable $opt:uuid := util:function(xs:QName("opt:uuid"), 3);
declare variable $opt:www-form-urldecode := util:function(xs:QName("opt:www-form-urldecode"), 3);
declare variable $opt:www-form-urlencode := util:function(xs:QName("opt:www-form-urlencode"), 3);
declare variable $opt:validate-with-xml-schema := util:function(xs:QName("opt:validate"), 3);
declare variable $opt:validate-with-schematron := util:function(xs:QName("opt:validate"), 3);
declare variable $opt:validate-with-relax-ng := util:function(xs:QName("opt:validate"), 3);
declare variable $opt:xquery := util:function(xs:QName("opt:xquery"), 3);
declare variable $opt:xsl-formatter :=util:function(xs:QName("opt:xsl-formatter"), 3);



(: -------------------------------------------------------------------------- :)
declare function opt:exec($primary,$secondary,$options) {
(: -------------------------------------------------------------------------- :)
    u:outputResultElement(<test3/>)
};

(: -------------------------------------------------------------------------- :)
declare function opt:hash($primary,$secondary,$options) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $algorithm := u:get-option('algorithm',$options,$v)
let $query := if (contains($match,'/')) then
				$match
			  else
				concat('//',$match)
let $matchresult := u:evalXPATH($query, $v)
let $replacement := u:hash($matchresult,$algorithm)
return
	u:replace-matching-elements($v/*,$matchresult,$replacement)
};


(: -------------------------------------------------------------------------- :)
declare function opt:uuid($primary,$secondary,$options) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $query := if (contains($match,'/')) then
				$match
			  else
				concat('//',$match)
let $matchresult := u:evalXPATH($query, $v)
let $replacement := u:uuid()
return
	u:replace-matching-elements($v/*,$matchresult,$replacement)
};


(: -------------------------------------------------------------------------- :)
declare function opt:www-form-urldecode($primary,$secondary,$options) {
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
declare function opt:www-form-urlencode($primary,$secondary,$options) {
(: -------------------------------------------------------------------------- :)
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function opt:validate($primary,$secondary,$options) {
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
declare function opt:xsl-formatter($primary,$secondary,$options) {
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
declare function opt:xquery($primary,$secondary,$options) {
(: -------------------------------------------------------------------------- :)
u:assert(exists(u:get-secondary('query',$secondary)/c:query),'p:input query is required'),
(:TODO: need to sort out multiple c:query elements and implied cdata sections :)
	let $v := u:get-primary($primary)
    let $xquery := u:get-secondary('query',$secondary)/c:query
	let $query := if ($xquery/@xproc:escape = 'true') then
			u:serialize($xquery/node(),$const:TRACE_SERIALIZE)
		else
			$xquery/node()

    (: TODO - change to u:xquery :)
    let $result := u:evalXPATH($query,$v)
        return
            $result
            (:
            u:outputResultElement($result)
            :)
};


(: -------------------------------------------------------------------------- :)
