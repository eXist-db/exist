xquery version "1.0" encoding "UTF-8";
module namespace u = "http://xproc.net/xproc/util";
(: ------------------------------------------------------------------------------------- 
 
	util.xqm - contains most of the XQuery processor specific functions, including all
	helper functions.
	
---------------------------------------------------------------------------------------- :)


(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";
declare namespace xproc = "http://xproc.net/xproc";
declare namespace std = "http://xproc.net/xproc/std";
declare namespace opt = "http://xproc.net/xproc/opt";
declare namespace ext = "http://xproc.net/xproc/ext";
declare namespace xxq-error = "http://xproc.net/xproc/error";

declare namespace t = "http://xproc.org/ns/testsuite";

(: Module Imports :)
import module namespace const = "http://xproc.net/xproc/const" at "resource:net/xproc/xprocxq/src/xquery/const.xqm";
import module namespace p1 = "http://xproc.net/xproc/functions" at "resource:net/xproc/xprocxq/src/xquery/functions.xqm";


(: set to 1 to enable debugging :)
declare variable $u:NDEBUG :=0;

(: -------------------------------------------------------------------------- :)
(: manage namespaces                                                          :)
(: -------------------------------------------------------------------------- :)


declare function u:declarens($element){
    u:declare-ns(u:enum-ns($element))
};


declare function u:declare-ns($namespaces){
    for $ns in $namespaces//ns
    return
        util:catch("*",util:declare-namespace($ns/@prefix,xs:anyURI($ns/@URI)),())
};


declare function u:enum-ns($element){
(
let $prefixes := in-scope-prefixes($element)
for $prefix in $prefixes
return
if ($prefix eq 'xml' or $prefix eq '' or $prefix eq 'xproc' or $prefix eq 'ext' or $prefix eq 'opt' or $prefix eq 'p' or $prefix eq 'c') then
 ()
else
<ns prefix="{$prefix}" URI="{namespace-uri-for-prefix($prefix,$element)}"/>
,
       for $child in $element/node()
            return
              if ($child instance of element() or $child instance of document-node()) then
               	 u:enum-ns($child)
                else
                  ()
)
};

(: -------------------------------------------------------------------------- :)
(: generate unique id														  :)
(: -------------------------------------------------------------------------- :)
declare function u:uniqueid($unique_id,$count) as xs:string{
    concat($unique_id,'.',$count)
};


(: -------------------------------------------------------------------------- :)
(: returns comp from comp definitions :)
(: -------------------------------------------------------------------------- :)
declare function u:get-comp($compname as xs:string) {
    $const:comp-steps//xproc:element[@type=$compname]
};


(: -------------------------------------------------------------------------- :)
(: checks to see if this component exists :)
(: -------------------------------------------------------------------------- :)
declare function u:comp-available($compname as xs:string) as xs:boolean {
        exists(u:get-comp($compname))
};


(: -------------------------------------------------------------------------- :)
(: returns step from std, opt and ext step definitions :)
(: -------------------------------------------------------------------------- :)
declare function u:get-step($stepname as xs:string,$declarestep) {
    $const:std-steps/p:declare-step[@type=$stepname],
    $const:opt-steps/p:declare-step[@type=$stepname],
    $const:ext-steps/p:declare-step[@type=$stepname],
    $const:comp-steps//xproc:element[@type=$stepname], 
    $declarestep/@type
};


(: -------------------------------------------------------------------------- :)
(: returns step type :)
(: -------------------------------------------------------------------------- :)
declare function u:type($stepname as xs:string,$is_declare-step) as xs:string {

    let $stdstep := $const:std-steps/p:declare-step[@type=$stepname]
    let $optstep := $const:opt-steps/p:declare-step[@type=$stepname]
    let $extstep := $const:ext-steps/p:declare-step[@type=$stepname]
    let $component :=$const:comp-steps//xproc:element[@type=$stepname]

    let $stdstepexists := exists($stdstep)
    let $optstepexists := exists($optstep)
    let $extstepexists := exists($extstep)
    let $compexists := exists($component)
    return
        if ($optstepexists) then
            'opt'
        else if($extstepexists) then
            'ext'
        else if($stdstepexists) then
            'std'
        else if($compexists) then
            'comp'
        else if($is_declare-step) then
          string(substring-before($is_declare-step/@type,':'))
        else
          u:staticError('err:XS0044', concat($stepname,":",$stepname,' has no visible declaration'))
};


(: -------------------------------------------------------------------------- :)
declare function u:trace($value as item()*, $what as xs:string)  {
if(boolean($u:NDEBUG)) then
    trace($value,$what)
else
    ()
};


(: -------------------------------------------------------------------------- :)
declare function u:assert($booleanexp as item(), $why as xs:string)  {
if(not($booleanexp) and boolean($u:NDEBUG)) then 
    u:dynamicError('err:XC0020',$why)
else
    ()
};


(: -------------------------------------------------------------------------- :)
declare function u:assert($booleanexp as item(), $why as xs:string,$error)  {
if(not($booleanexp) and boolean($u:NDEBUG)) then 
    error(QName('http://www.w3.org/ns/xproc-error',$error),concat("XProc Assert Error: ",$why))
else
    ()
};


(: -------------------------------------------------------------------------- :)
declare function u:boolean($test as xs:string)  {
if(contains($test,'false') ) then 
    false()
else
    true()
};


(: -------------------------------------------------------------------------- :)
declare function u:uuid()  {
	util:uuid()
};

(: -------------------------------------------------------------------------- :)
declare function u:hash($data,$algorithm)  {
	util:hash($data,$algorithm)
};

(: -------------------------------------------------------------------------- :)
declare function u:unparsed-data($uri as xs:string, $mediatype as xs:string)  {
	util:binary-to-string(util:binary-doc($uri))
};


(: -------------------------------------------------------------------------- :)
(: TODO: consider combining error throwing functions :)
(: consider adding saxon:line-number()  :)
declare function u:dynamicError($error,$string) {
    let $info := $const:error//err:error[@code=substring-after($error,':')]
    return
        error(QName('http://www.w3.org/ns/xproc-error',$error),concat($error,": XProc Dynamic Error - ",$string," ",$info/text(),'&#10;'))
};


declare function u:staticError($error,$string) {
let $info := $const:error//err:error[@code=substring-after($error,':')]
    return
        error(QName('http://www.w3.org/ns/xproc-error',$error),concat($error,": XProc Static Error - ",$string," ",$info/text(),'&#10;'))
};


declare function u:stepError($error,$string) {
let $info := $const:error//err:error[@code=substring-after($error,':')]
    return
        error(QName('http://www.w3.org/ns/xproc-error',$error),concat($error,": XProc Step Error - ",$string," ",$info/text(),'&#10;'))
};


declare function u:xprocxqError($error,$string) {
let $info := $const:xprocxq-error//xxq-error:error[@code=substring-after($error,':')]
    return
        error(QName('http://xproc.net/xproc/error',$error),concat($error,": xprocxq error - ",$string," ",$info/text(),'&#10;'))};


(: -------------------------------------------------------------------------- :)
declare function u:outputResultElement($exp){
    <c:result>{$exp}</c:result>
};


(: -------------------------------------------------------------------------- :)
declare function u:get-option($option-name as xs:string,$options,$v){

let $option := xs:string($options/*[@name=$option-name]/@select)
return
	(: TODO - if required this could be an error :)
    if (empty($option)) then
        ()
    (: TODO- need to remove this branch at some point :)
    else if(contains($option,"'")) then
    	string(replace($option,"'",""))
    else
    	string(u:evalXPATH(string($option),$v))
};


(: -------------------------------------------------------------------------- :)
declare function u:get-secondary($name as xs:string,$secondary){
(: -------------------------------------------------------------------------- :)

if($secondary/xproc:input[@port=$name]//t:document) then
    for $child in $secondary/xproc:input[@port=$name]/t:document/node()
    return
        document{$child}
else
    for $child in $secondary/xproc:input[@port=$name]/node()
    return
        document{$child}
};


(: -------------------------------------------------------------------------- :)
declare function u:get-primary($primary){
(: -------------------------------------------------------------------------- :)
if($primary//t:document) then
	for $child in $primary/t:document/node()
	return
		    document{$child}
else
	for $child in $primary/node()
	return
		    document{$child}
};


(: -------------------------------------------------------------------------- :)
declare function u:random() as  xs:double  {
   util:random()
};


(: -------------------------------------------------------------------------- :)
declare function u:eval($exp as xs:string) as item()*{
    util:eval($exp)
};


(: -------------------------------------------------------------------------- :)
(: TODO: refactor the following into a single function :)

declare function u:call($func,$a) as item()*{
    util:call($func,$a)
};
declare function u:call($func,$a,$b) as item()*{
    util:call($func,$a,$b)
};
declare function u:call($func,$a,$b,$c) as item()*{
    util:call($func,$a,$b,$c)
};
declare function u:call($func,$a,$b,$c,$d) as item()*{
    util:call($func,$a,$b,$c,$d)
};
declare function u:call($func,$a,$b,$c,$d,$e) as item()*{
    util:call($func,$a,$b,$c,$d,$e)
};
declare function u:call($func,$a,$b,$c,$d,$e,$f) as item()*{
    util:call($func,$a,$b,$c,$d,$e,$f)
};


(: -------------------------------------------------------------------------- :)
(:
declare function u:function($func,$arity){
    util:function($func, $arity)
};
:)


(: -------------------------------------------------------------------------- :)
declare function u:xquery($query,$xml){
    let $static-content := <static-context>
						<default-context>{$xml}</default-context>
						</static-context>
    let $qry := if (starts-with(normalize-space($query),'/') or starts-with(normalize-space($query),'//')) then
                concat('.',$query)
			  else if(contains($query,'(/')) then
				replace($query,'\(/','(./')
              else
                  $query

let $result := util:eval-with-context($qry,$static-content,false())
    return
        $result
};


(: -------------------------------------------------------------------------- :)
declare function u:xquery-with-context($query,$xml){
    let $static-content := <static-context>
						<default-context>{$xml}</default-context>
						</static-context>
    let $qry := if (starts-with(normalize-space($query),'/') or starts-with(normalize-space($query),'//')) then
                concat('.',$query)
			  else if(contains($query,'(/')) then
				replace($query,'\(/','(./')
              else
                  $query


let $result : = util:eval-inline($xml,$query)

(:  let $result := util:eval-with-context($qry,$static-content,false())
:)
    return
        $result
};


(: -------------------------------------------------------------------------- :)
declare function u:xquery($query as xs:string){
    let $qry := if (starts-with($query,'/') or starts-with($query,'//')) then
                concat('.',$query)
			  else if(contains($query,'(/')) then
				replace($query,'\(/','(./')
              else if($query eq '') then
			    u:dynamicError('err:XD0001','query is empty and/or XProc step is not supported')
              else
                  $query
    let $result := util:eval($qry)   
    return
        $result
};


(: -------------------------------------------------------------------------- :)
declare function u:xslt($input,$style){
    transform:transform($input, document{$style}, ())
};


(: -------------------------------------------------------------------------- :)
declare function u:safe-evalXPATH($qry as xs:string, $xml,$pipeline){
    util:catch("*",u:evalXPATH($qry, $xml, $pipeline/*),())
};


(: -------------------------------------------------------------------------- :)
declare function u:safe-evalXPATH($qry as xs:string, $xml){
    util:catch("*", u:evalXPATH($qry,$xml),())
};


(: -------------------------------------------------------------------------- :)
declare function u:evalXPATH($qry as xs:string, $xml){

if(empty($qry) or $qry eq '/') then
	$xml
else
	let $query := if (starts-with($qry,'/') or starts-with($qry,'//')) then
                concat('.',$qry)
			  else if(contains($qry,'(/')) then
				replace($qry,'\(/','(./')
              else
                  $qry
    return
	    util:eval-inline($xml,$query)
		(:
		if ( $result instance of element() or $result instance of document-node()) then 
		
			u:dynamicError('err:XD0016',$xpathstring)
			:)
};


(: -------------------------------------------------------------------------- :)
declare function u:evalXPATH($qry as xs:string, $xml, $namespaces){

if(empty($qry) or $qry eq '/') then
	$xml
else
	let $query := if (starts-with($qry,'/') or starts-with($qry,'//')) then
                concat('.',$qry)
			  else if(contains($qry,'(/')) then
				replace($qry,'\(/','(./')
              else
                  $qry
    return
        let $declarens := u:declare-ns($namespaces)
        return
	       util:eval-inline($xml,$query)

		(:
		if ( $result instance of element() or $result instance of document-node()) then
			u:dynamicError('err:XD0016',$xpathstring)
	    :)
};


(: -------------------------------------------------------------------------- :)
declare function u:add-ns-node(
    $elem   as element(),
    $prefix as xs:string,
    $ns-uri as xs:string
  ) as element()
{
  element { QName($ns-uri, concat($prefix, ":x")) }{ $elem }/*
};


(: -------------------------------------------------------------------------- :)
declare function u:deep-equal-seq($primary,$secondary,$strict) {

if ($strict eq '1') then
let $e1 := (for $child in $primary/*
		 return
			$child)
			
let $e2 := (for $child in $secondary/*
			return
				$child)
				
return

every $i in 1 to max((count($e1),count($e2)))
satisfies deep-equal($e1[$i],$e2[$i])


else

let $e1 := (for $child in $primary/*
		 return
			u:treewalker($child))
			
let $e2 := (for $child in $secondary/*
			return
				u:treewalker($child))
				
return

every $i in 1 to max((count($e1),count($e2)))
satisfies deep-equal($e1[$i],$e2[$i])


};

(: -------------------------------------------------------------------------- :)
declare function u:treewalker($element) {
element {node-name($element)}
   {$element/@*,

       for $child in $element/node()
           return
            if ($child instance of element()) then 
					u:treewalker($child)
              else 
					normalize-space($child)
															
   }
};



declare function u:treewalker-add-attribute($element as element(),$match,$attrName,$attrValue) as element() {
   element {node-name($element)}
      {$element/@*,
       if(name($element) = string($match)) then attribute {$attrName}{$attrValue} else (),
          for $child in $element/node()
              return
               if ($child instance of element())
                 then u:treewalker-add-attribute($child,$match,$attrName,$attrValue)
                 else $child
      }
};

declare function u:copy-filter-elements($element as element(), $element-name as xs:string*) as element() {
   element {node-name($element) }
             { $element/@*,
               for $child in $element/node()[not(name(.)=$element-name)]
                  return if ($child instance of element())
                    then u:copy-filter-elements($child,$element-name)
                    else $child
           }
};


declare function u:rename-inline-element($element as element(),$match,$newelement) as element() {
   element {if(string(node-name($element)) = string($match)) then node-name($newelement) else node-name($element)}
      {$element/@*,
       if(string(node-name($element)) = $match) then 
				($newelement/@*)
		else 
			(),
          for $child in $element/node()
              return
               if ($child instance of element())
                 then u:rename-inline-element($child,$match,$newelement)
                 else $child
      }
};

declare function u:delete-matching-elements($element as element(),$select) as element() {
   element {node-name($element)}
      {$element/@*[not(. intersect $select)],
          for $child in $element/node()[not(. intersect $select)]
              return                             
               if ($child instance of element())
                 then 
                     u:delete-matching-elements($child,$select)
                 else
                     $child
      }
};



declare function u:replace-matching-elements($element as element(),$select,$replace) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
	    	    		$replace
	    	    	else if ($child/text() eq $select ) then
	    	    	    element {node-name($child)}{
                            $replace
 	    	    	    }
    			    else
                        u:replace-matching-elements($child,$select,$replace)
                else
                    $child          		
      }
};

declare function u:insert-matching-elements($element as element(),$select,$replace,$position) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
						if($position eq 'before' or $position eq 'first-child') then
							($replace,u:insert-matching-elements($child,$select,$replace,$position))
						else if($position eq 'after' or $position eq 'last-child') then
							(u:insert-matching-elements($child,$select,$replace,$position),$replace)
						else	
							u:insert-matching-elements($child,$select,$replace,$position)
    			    else
                        u:insert-matching-elements($child,$select,$replace,$position)
                else
                    $child          		
      }
};



declare function u:rename-matching-elements($element as element(),$select,$new-name) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
				   		element {$new-name}{$child/*
						}						
    			    else
                        u:rename-matching-elements($child,$select,$new-name)
                else
                    $child          		
      }
};

declare function u:wrap-matching-elements($element as element(),$select,$wrapper) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
				   		element {$wrapper}{
                        	u:wrap-matching-elements($child,$select,$wrapper)
						}						
    			    else
                        u:wrap-matching-elements($child,$select,$wrapper)
                else
                    $child          		
      }
};

declare function u:unwrap-matching-elements($element as element(),$select) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
                        	$child/*
    			    else
                        u:unwrap-matching-elements($child,$select)
                else
                    $child          		
      }
};

declare function u:label-matching-elements($element as element(),$select,$attribute,$label,$replace) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child at $pos in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
				   element {node-name($child)}{    
				        attribute {$attribute} {$label,"_",$pos},
                   		u:label-matching-elements($child,$select,$attribute,$label,$replace)
						}	
    			    else
                        u:label-matching-elements($child,$select,$attribute,$label,$replace)
                else
                    $child          		
      }
};

declare function u:label-matching-elements($element as element(),$select,$attribute,$label,$replace) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child at $pos in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
				   element {node-name($child)}{    
				        attribute {$attribute} {$label,"_",$pos},
                   		u:label-matching-elements($child,$select,$attribute,$label,$replace)
						}	
    			    else
                        u:label-matching-elements($child,$select,$attribute,$label,$replace)
                else
                    $child          		
      }
};

declare function u:add-attribute-matching-elements($element as element(),$select,$attribute,$label) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child at $pos in $element/node()
              return                   
              if ($child instance of element())
                then

            		if ($child intersect $select) then
				   		element {node-name($child)}{
				    		$child/@*[not(name(.)=$attribute)],
				        	attribute {$attribute} {$label},
							if ($child/node() instance of text()) then
								$child/text()
							else if ($child/node() instance of element()) then						
               					u:add-attribute-matching-elements($child,$select,$attribute,$label)
							else
								$child/*
						}	
    			    else
                        u:add-attribute-matching-elements($child,$select,$attribute,$label)
                
				else if ($child/node() instance of text()) then
                    $child/text()
          		else 
					$child
      }
};

declare function u:add-attributes-matching-elements($element as element(),$select,$attributes) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child at $pos in $element/node()
              return                   
              if ($child instance of element())
                then
            		if ($child intersect $select) then
				   element {node-name($child)}{
				    	$attributes,
                   		u:add-attributes-matching-elements($child,$select,$attributes)
						}	
    			    else
                        u:add-attributes-matching-elements($child,$select,$attributes)
                else
                    $child          		
      }
};

declare function u:string-replace-matching-elements($element as element(),$select,$replace) as element() {
   element {node-name($element)}
      {$element/@*,
          for $child at $pos in $element/node()
              return                   
              if ($child instance of element())
                then
        			if ($child intersect $select) then
    	    			string($replace)
			    	else
                    	u:string-replace-matching-elements($child,$select,$replace)
                else
                    $child          		
      }
};

(: -------------------------------------------------------------------------- :)
declare function u:treewalker ($tree,$attrFunc,$elemFunc) {

  let $children := $tree/*
  return
      if(empty($children)) then ()
      else
        for $c in $children
            return
                ( element {node-name($c)}{
                            u:call($attrFunc,$c/@*),
                            u:call($elemFunc,$c/*),
                        u:treewalker($tree,$attrFunc,$elemFunc)
                })
};

declare function u:treewalker ($tree,$attrFunc,$textFunc,$attName,$attValue) {

  let $children := $tree/*
  return
      if(empty($children)) then ()
      else
        for $c in $children
            return
                ( element {node-name($c)}{
                            u:call($attrFunc,$c/@*,$attName,$attValue),
                            u:call($textFunc,$c/text()),
                        u:treewalker($c,$attrFunc,$textFunc,$attName,$attValue)
                })
};


declare function u:attrHandler ($attr,$attName,$attValue) {
	$attr, attribute {string($attName)}{string($attValue)}
 };

declare function u:textHandler ($text) {
	$text
 };

(: -------------------------------------------------------------------------- :)
declare function u:declare-used-namespaces ( $root as node()? )  as xs:anyURI* {
let $namespaces :=   (distinct-values($root/descendant-or-self::*/(.|@*)/namespace-uri(.)) )
return
for $namespace at $pos in $namespaces
return 
    let $ns := concat('ns',$pos)
    return
        util:declare-namespace($ns,$namespace)
} ;

(: -------------------------------------------------------------------------- :)
declare function u:list-used-namespaces1 ( $root as node()? )  {
let $prefix :=   (distinct-values($root/descendant-or-self::*/(.|@*)/substring-before(name(.), ':')) )
let $namespaces :=   (distinct-values($root/descendant-or-self::*/(.|@*)/namespace-uri(.)) )
return
for $namespace at $pos in $namespaces
	return 
	 if ($namespace eq 'http://www.w3.org/XML/1998/namespace') then
		()
	 else if ($namespace eq 'http://www.w3.org/ns/xproc-step') then
		()

	 else
		let $ns := $prefix[$pos]
    			return
         			concat('declare namespace ',$ns,'="',$namespace,'";')
} ;

 declare function u:list-used-namespaces ( $root as node()? )  as xs:string* {
let $prefix :=   (distinct-values($root/descendant-or-self::*/(.|@*)/substring-before(name(.), ':')) )
let $namespaces :=   (distinct-values($root/descendant-or-self::*/(.|@*)/namespace-uri(.)) )
return
for $namespace at $pos in $namespaces
    return 
        
		let $ns := $prefix[$pos - 1]
    			return
            if ($namespace eq '') then
                ()
			else if ($namespace eq 'http://www.w3.org/XML/1998/namespace') then
				()
            else if ($ns) then 	
       			concat('declare namespace ',$ns,'="',$namespace,'";')
            else
                concat('declare default element namespace "',$namespace,'";')
} ;


(: -------------------------------------------------------------------------- :)
declare function u:validate($exp) as xs:string {
$exp
(:
    nvdl:main("file:test/data/w3schema.xml file:test/data/schema-example.xml")
:)
};


(: -------------------------------------------------------------------------- :)
declare function u:serialize($xml,$options){
	util:serialize($xml,$options)
};


(: -------------------------------------------------------------------------- :)
declare function u:parse-string($string) as item()*{
    util:parse($string)
};

(: -------------------------------------------------------------------------- :)
declare function u:map($func, $seqA as item()*, $seqB as item()*) 
as item()* {
	if(count($seqA) != count($seqB)) then ()
	else
    	for $a at $i in $seqA
    	let $b := $seqB[$i]
    	return
        	u:call($func, $a, $b)
};

(: -------------------------------------------------------------------------- :)
declare function u:filter($func, $seq as item()*) 
as item()* {
	for $i in $seq
	return
		if(u:call($func, $i)) then
			$i
		else
			()
};

(: -------------------------------------------------------------------------- :)
(: test folding the step with a different function :)
declare function u:printstep ($step,$meta,$value) {
    u:call( $step, $value)
};


(: -------------------------------------------------------------------------- :)

declare function u:strip-namespace($e as element()) as element() {
  
   element {QName((),local-name($e))} {
    for $child in $e/(@*,node())
    return
      if ($child instance of element())
      then
        u:strip-namespace($child)
      else
        $child
  }
};

(: -------------------------------------------------------------------------- :)

declare function u:uniqueid($unique_id,$count){
    concat($unique_id,'.',$count)
};

(: -------------------------------------------------------------------------- :)
declare function u:final-result($pipeline,$resulttree){
    ($pipeline,$resulttree)
};


(: -------------------------------------------------------------------------- :)
declare function u:step-fold( $pipeline,
                              $namespaces,
                              $steps,
                              $evalstep-function,
                              $primary,
                              $outputs) {

    if (empty($steps)) then
        u:final-result($pipeline,$outputs)

    else
        let $result:= u:call($evalstep-function,
                                $steps[1],
                                $namespaces,
                                $primary,
                                $pipeline,
                                $outputs)
    return
        u:step-fold($pipeline,
                    $namespaces,
                    remove($steps, 1),
                    $evalstep-function,
                    $result[last()],
                    ($outputs,$result))
};


