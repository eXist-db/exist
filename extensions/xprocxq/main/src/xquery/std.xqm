xquery version "1.0" encoding "UTF-8";
module namespace std = "http://xproc.net/xproc/std";
(: ------------------------------------------------------------------------------------- 

	std.xqm - Implements all standard xproc steps.
	
---------------------------------------------------------------------------------------- :)


(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";
declare namespace xproc = "http://xproc.net/xproc";

declare namespace t = "http://xproc.org/ns/testsuite";


(: Module Imports :)
import module namespace u = "http://xproc.net/xproc/util" at "resource:net/xproc/xprocxq/src/xquery/util.xqm";
import module namespace const = "http://xproc.net/xproc/const" at "resource:net/xproc/xprocxq/src/xquery/const.xqm";
import module namespace http = "http://www.expath.org/mod/http-client";


(: -------------------------------------------------------------------------- :)

(: Module Vars :)
declare variable $std:add-attribute :=util:function(xs:QName("std:add-attribute"), 4);
declare variable $std:add-xml-base :=util:function(xs:QName("std:add-xml-base"), 4);
declare variable $std:count :=util:function(xs:QName("std:count"), 4);
declare variable $std:compare :=util:function(xs:QName("std:compare"),4);
declare variable $std:delete :=util:function(xs:QName("std:delete"),4);
declare variable $std:error :=util:function(xs:QName("std:error"), 4);
declare variable $std:filter :=util:function(xs:QName("std:filter"), 4);
declare variable $std:directory-list :=util:function(xs:QName("std:directory-list"), 4);
declare variable $std:escape-markup :=util:function(xs:QName("std:escape-markup"), 4);
declare variable $std:http-request :=util:function(xs:QName("std:http-request"), 4);
declare variable $std:identity :=util:function(xs:QName("std:identity"), 4);
declare variable $std:insert :=util:function(xs:QName("std:insert"), 4);
declare variable $std:label-elements :=util:function(xs:QName("std:label-elements"), 4);
declare variable $std:load :=util:function(xs:QName("std:load"), 4);
declare variable $std:make-absolute-uris :=util:function(xs:QName("std:make-absolute-uris"), 4);
declare variable $std:namespace-rename :=util:function(xs:QName("std:namespace-rename"), 4);
declare variable $std:pack :=util:function(xs:QName("std:pack"), 4);
declare variable $std:parameters :=util:function(xs:QName("std:parameters"), 4);
declare variable $std:rename :=util:function(xs:QName("std:rename"), 4);
declare variable $std:replace :=util:function(xs:QName("std:replace"), 4);
declare variable $std:set-attributes :=util:function(xs:QName("std:set-attributes"), 4);
declare variable $std:sink :=util:function(xs:QName("std:sink"), 4);
declare variable $std:split-sequence :=util:function(xs:QName("std:split-sequence"), 4);
declare variable $std:store :=util:function(xs:QName("std:store"), 4);
declare variable $std:string-replace :=util:function(xs:QName("std:string-replace"), 4);
declare variable $std:unescape-markup :=util:function(xs:QName("std:unescape-markup"), 4);
declare variable $std:xinclude :=util:function(xs:QName("std:xinclude"), 4);
declare variable $std:wrap :=util:function(xs:QName("std:wrap"), 4);
declare variable $std:wrap-sequence :=util:function(xs:QName("std:wrap-sequence"), 4);
declare variable $std:unwrap :=util:function(xs:QName("std:unwrap"), 4);
declare variable $std:xslt :=util:function(xs:QName("std:xslt"), 4);


(: -------------------------------------------------------------------------- :)
declare function std:add-attribute($primary,$secondary,$options,$variables) {

let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $attribute-name := u:get-option('attribute-name',$options,$v)
let $checkname := u:asserterror('err:XC0059', boolean($attribute-name ne 'xmlns'), 'This step cannot be used to add namespace declarations')
let $attribute-value := u:get-option('attribute-value',$options,$v)
let $attribute-prefix := u:get-option('attribute-prefix',$options,$v)
let $attribute-namespace := u:get-option('attribute-namespace',$options,$v)
let $ifnamespacedeclared := if ($attribute-namespace ne '') then
util:declare-namespace($attribute-prefix, $attribute-namespace)
else
 ()

let $attrname  := if ($attribute-namespace ne '') then
fn:QName($attribute-namespace,$attribute-name)
else
 $attribute-name

return

	u:add-attribute-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$attrname,$attribute-value)
};


(: -------------------------------------------------------------------------- :)
declare function std:add-xml-base($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $all := u:get-option('all',$options,$v)
let $relative := u:get-option('relative',$options,$v)
let $matchresult := u:evalXPATH('//*', $v, $primary)
let $attribute-name := "xml:base"
let $attribute-value := base-uri($v)
return
	u:add-attribute-matching-elements($v,x,$attribute-name,$attribute-value)
};


(: -------------------------------------------------------------------------- :)
declare function std:compare($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $alternate : = u:get-secondary('alternate',$secondary)
let $strict := xs:boolean(u:get-option('xproc:strict',$options,$v)) (: ext attribute xproc:strict:)
let $fail-if-not-equal := xs:boolean(u:get-option('fail-if-not-equal',$options,$v))

let $result :=  if ($strict and count($v/*) eq 1) then
		deep-equal($v,$alternate)

else if ($strict and count($v/*) gt 1) then
	u:deep-equal-seq($v,$alternate,'1')

else if (count($v/*) gt 1) then
	u:deep-equal-seq($v,$alternate,'0')

else 
	deep-equal(u:treewalker($v/*),u:treewalker($alternate/*))

    return

       if($fail-if-not-equal) then
            if ($result) then          
      			u:outputResultElement('true')
            else
                u:stepError('err:XC0019','p:compare fail-if-not-equal option is enabled and documents were not equal')
        else
            u:outputResultElement($result)
};


(: -------------------------------------------------------------------------- :)
declare function std:count($primary,$secondary,$options,$variables){
let $v := u:get-primary($primary)
let $limit := xs:integer(u:get-option('limit',$options,$v))
let $count := count($v/*)
return
    if (empty($limit) or $limit eq 0 or $count lt $limit ) then
		u:outputResultElement($count)
    else
   		u:outputResultElement($limit)
};


(: -------------------------------------------------------------------------- :)
declare function std:delete($primary,$secondary,$options,$variables){
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
return
   	u:delete-matching-elements($v/*,  u:xsltmatchpattern($match,$v,$variables) )
};


(: -------------------------------------------------------------------------- :)
declare function std:directory-list($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $path := u:get-option('path',$options,$v)
let $include-filter := u:get-option('include-filter',$options,$v)
let $exclude-filter := u:get-option('exclude-filter',$options,$v)

let $segment := substring-after($path,'file://')
let $dirname := tokenize($path, '/')[last()]
let $result := if (starts-with($path,'file://')) then
                let $query := concat("file:directory-list('",$segment,"','",$include-filter,"')")
                let $files := u:eval($query)
                return
                    <c:directory name="{$dirname}">
					{for $file in $files//*:file
					return
						<c:file name="{$file/@name}"/>
					}
				    </c:directory>
              else
                let $files := collection($path)/util:document-name(.)
                return
                    <c:directory name="{$dirname}">
					{for $file in $files
					return
						<c:file name="{$file}"/>
					}
				    </c:directory>
				    (: TODO: throw err:XC0017 if no directory exists :)
return
		u:outputResultElement($result)
};


(: -------------------------------------------------------------------------- :)
declare function std:escape-markup($primary,$secondary,$options,$variables) {
(: TODO: test with sequences :)
let $v := u:get-primary($primary)
return
	element{name($v/*)}{
    	u:serialize($v/*/*,$const:ESCAPE_SERIALIZE)
	}
};


(: -------------------------------------------------------------------------- :)
declare function std:error($primary,$secondary,$options,$variables) {
(: TODO: this should be generated to the error port:)

let $v := u:get-primary($primary)
let $code := u:get-option('code',$options,$v)
let $err := <c:errors xmlns:c="http://www.w3.org/ns/xproc-step"
          xmlns:p="http://www.w3.org/ns/xproc"
          xmlns:my="http://www.example.org/error">
		<c:error href="" column="" offset=""
         	name="step-name" type="p:error"
         	code="{$code}">
    		<message>{$v}</message>
		</c:error>
</c:errors>
return
	u:dynamicError('err:XD0030',concat(": p:error throw custom error code - ",$code," ",u:serialize($err,$const:TRACE_SERIALIZE)))

};


(: -------------------------------------------------------------------------- :)
declare function std:filter($primary,$secondary,$options,$variables) {
(: TODO - broken :)
u:assert(exists($options/p:with-option[@name='select']/@select),'p:with-option match is required'),
let $v := u:get-primary($primary)
let $select := string(u:get-option('select',$options,$v))
let $result := u:evalXPATH($select,document{$v},$primary)
    return
        if(exists($result)) then
        	$result
		else
            ()
};


(: -------------------------------------------------------------------------- :)
declare function std:http-request($primary,$secondary,$options,$variables) {
    let $v := u:get-primary($primary)
    let $href := $v/c:request/@href
    let $method := $v/c:request/@method
    let $content-type := $v/c:request/c:body/@content-type
    let $body := $v/c:request/c:body
    let $status-only := $v/c:request/@status-only
    let $detailed := $v/c:request/@detailed
    let $username := ''
    let $password := ''
    let $auth-method := ''
    let $send-authorization := ''
    let $override-content-type := ''
    let $follow-redirect := ''
    let $http-request := <http:request href="{$href}" method="{$method}">{
            for $header in $v/c:request/c:header
            return
                <http:header name="{$header/@name}" value="{$header/@value}"/>,

            if (empty($body)) then
                ()
            else
              <http:body content-type="{$content-type}">
                 {$body}
              </http:body>
        }
           </http:request>
    let $raw-response := http:send-request($http-request)
    let $response-headers := for $header in $raw-response//http:header
            return
                <c:header name="{$header/@name}" value="{$header/@value}"/>

    let $response-body := if ($status-only) then
            ()
         else if ($detailed) then
            <c:body>{$raw-response/*[not(name(.) eq 'http:body')][not(name(.) eq 'http:header')]}</c:body>
         else
            $raw-response/*[not(name(.) eq 'http:body')][not(name(.) eq 'http:header')]

    return

        if (not($v/c:request)) then
                u:dynamicError('err:XC0040',"source port must contain c:request element")
        else if ($detailed) then
          <c:response status="{$raw-response/@status}">
            {$response-headers}
            {$response-body}
          </c:response>
        else
            $response-body
};


(: -------------------------------------------------------------------------- :)
declare function std:identity($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function std:insert($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $position := u:get-option('position',$options,$v)
let $insertion := u:get-secondary('insertion',$secondary)
return
	u:insert-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$insertion,$position)
};


(: -------------------------------------------------------------------------- :)
declare function std:label-elements($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $attribute := u:get-option('attribute',$options,$v)
let $label := u:get-option('label',$options,$v)
let $replace := u:get-option('replace',$options,$v)
return
	u:label-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$attribute,$label,$replace)
};


(: -------------------------------------------------------------------------- :)
declare function std:load($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $href := u:get-option('href',$options,$v)
let $xproc:output-uri := u:get-option('xproc:output-uri',$options,$v)
return
if (empty($href)) then
	u:dynamicError('err:XC0026',"p:load option href is empty.")
else if(starts-with($href,'file://')) then
		let $test-exists := concat("file:exists('",
									 substring-after($href,'file://'),
									 "')")
		let $query := concat("file:read('",
									 $href,
									 "')")			
		return
			if (u:eval($test-exists)) then
				u:parse-string(u:eval($query))
			else
				u:dynamicError('err:XC0026',"p:load could not access or find the file.")			
else
	let $load := doc($href)
	return
		if ($xproc:output-uri eq 'true') then
			u:outputResultElement($href)
		else
			$load
};


(: -------------------------------------------------------------------------- :)
declare function std:make-absolute-uris($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function std:namespace-rename($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
return
	$v
};



(: -------------------------------------------------------------------------- :)
declare function std:pack($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $alternate := u:get-secondary('alternate',$secondary)
let $wrapper := u:get-option('wrapper',$options,$v)
return

    for $child at $count in $v
    return
	    element {$wrapper}{
	        $child,
	        $alternate
	    }
};


(: -------------------------------------------------------------------------- :)
declare function std:parameters($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
return
	$v
};


(: -------------------------------------------------------------------------- :)
declare function std:rename($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $new-name := u:get-option('new-name',$options,$v)
return
	u:rename-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$new-name)
};


(: -------------------------------------------------------------------------- :)
declare function std:replace($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $replacement := u:get-secondary('replacement',$secondary)
return
	u:replace-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$replacement)
};


(: -------------------------------------------------------------------------- :)
declare function std:set-attributes($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $attributes := u:get-secondary('attributes',$secondary)
let $match := u:get-option('match',$options,$v)
return
	u:add-attributes-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$attributes/*/@*)
};


(: -------------------------------------------------------------------------- :)
declare function std:sink($primary,$secondary,$options,$variables) {
    (string(''))
};


(: -------------------------------------------------------------------------- :)
declare function std:split-sequence($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $test := u:get-option('test',$options,$v)
let $match := u:evalXPATH($test, $v, $primary)
return
    for $child at $count in $match
    return
        if($child) then
            $v[$count]
        else
            ()
};


(: -------------------------------------------------------------------------- :)
declare function std:store($primary,$secondary,$options,$variables) {
(:TODO - check existence of collection path :)
let $v := u:get-primary($primary)
let $href-uri := u:get-option('href',$options,$v)
let $name := tokenize($href-uri, "/")[last()]
let $xproc:output-document := u:get-option('xproc:output-document',$options,$v)
let $path := substring-before($href-uri,$name)
let $serialized := u:serialize($v,$const:DEFAULT_SERIALIZE)
let $store := if(starts-with($path,'file://')) then
				let $query := concat("file:serialize(",
									 $serialized,
								 	 ",'",
									 substring-after($href-uri,'file://'),
									 "','method=xml')")
				return
					u:eval($query) 
			  else
								xmldb:store($path,$name,$v)
return
if($xproc:output-document eq 'true') then
		$v
	else
		u:outputResultElement(concat($path,$name))
};


(: -------------------------------------------------------------------------- :)
declare function std:string-replace($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $replace := string(u:get-option('replace',$options,$v))
return
    util:catch('*',u:string-replace-matching-elements($v, u:xsltmatchpattern($match,$v,$variables),$replace),u:dynamicError("err:XD0001",": p:string-replace resulted in non xml output"))
};


(: -------------------------------------------------------------------------- :)
declare function std:unescape-markup($primary,$secondary,$options,$variables){
(: TODO: test with sequences :)
let $v := u:get-primary($primary)
return
    element{name($v/*)}{
		u:parse-string($v)
		}
};


(: -------------------------------------------------------------------------- :)
declare function std:xinclude($primary,$secondary,$options,$variables){
let $v := u:get-primary($primary)
return
	u:parse-string(u:serialize($v,'expand-xincludes=yes'))
};


(: -------------------------------------------------------------------------- :)
declare function std:wrap($primary,$secondary,$options,$variables) {
let $v := u:get-primary($primary)
let $match := u:get-option('match',$options,$v)
let $wrapper := u:get-option('wrapper',$options,$v)
let $group-adjacent := u:get-option('group-adjacent',$options,$v)
return
    if ($match eq '/') then
        element {$wrapper}{
	    	$v
	    }
	else
	    u:wrap-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables),$wrapper)
};


(: -------------------------------------------------------------------------- :)
declare function std:wrap-sequence($primary,$secondary,$options,$variables){
let $v := u:get-primary($primary)
let $wrapper := u:get-option('wrapper',$options,$v)
let $group-adjacent := u:get-option('group-adjacent',$options,$v)
return
	element {$wrapper}{
		$v
	}
};


(: -------------------------------------------------------------------------- :)
declare function std:unwrap($primary,$secondary,$options,$variables) {
    let $v := u:get-primary($primary)
    let $match := u:get-option('match',$options,$v)
    return
        u:unwrap-matching-elements($v/*, u:xsltmatchpattern($match,$v,$variables))
};


(: -------------------------------------------------------------------------- :)
declare function std:xslt($primary,$secondary,$options,$variables){
	let $v := u:get-primary($primary)
    let $stylesheet := u:get-secondary('stylesheet',$secondary)
    return
        u:xslt(document{$v},document{$stylesheet})
};


(: -------------------------------------------------------------------------- :)