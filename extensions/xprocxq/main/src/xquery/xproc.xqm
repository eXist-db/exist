 xquery version "1.0" encoding "UTF-8";
 module namespace xproc = "http://xproc.net/xproc";
 (: -------------------------------------------------------------------------------------

    xproc.xqm - core xqm containing entry points, primary eval-step function and
    control functions.

 ---------------------------------------------------------------------------------------- :)

 (: XProc Namespace Declaration :)
 declare namespace p="http://www.w3.org/ns/xproc";
 declare namespace c="http://www.w3.org/ns/xproc-step";
 declare namespace err="http://www.w3.org/ns/xproc-error";
 declare namespace xsl="http://www.w3.org/1999/XSL/Transform";
 
 (: Module Imports :)
 import module namespace const = "http://xproc.net/xproc/const" at "resource:net/xproc/xprocxq/src/xquery/const.xqm";
 import module namespace u = "http://xproc.net/xproc/util" at "resource:net/xproc/xprocxq/src/xquery/util.xqm";
 import module namespace opt = "http://xproc.net/xproc/opt" at "resource:net/xproc/xprocxq/src/xquery/opt.xqm";
 import module namespace std = "http://xproc.net/xproc/std" at "resource:net/xproc/xprocxq/src/xquery/std.xqm";
 import module namespace ext = "http://xproc.net/xproc/ext" at "resource:net/xproc/xprocxq/src/xquery/ext.xqm";
 import module namespace naming = "http://xproc.net/xproc/naming" at "resource:net/xproc/xprocxq/src/xquery/naming.xqm";

 (: disallow xinclude for p:xinclude step to manage:)
 declare option exist:serialize "expand-xincludes=no";

 (: -------------------------------------------------------------------------- :)
 declare variable $xproc:run-step := util:function(xs:QName("xproc:run-step"), 5);
 declare variable $xproc:parse-and-eval := util:function(xs:QName("xproc:parse_and_eval"), 5);
 (: -------------------------------------------------------------------------- :)
 declare variable $xproc:declare-step :=util:function(xs:QName("xproc:declare-step"), 5);
 declare variable $xproc:choose :=util:function(xs:QName("xproc:choose"), 5);
 declare variable $xproc:try :=util:function(xs:QName("xproc:try"), 5);
 declare variable $xproc:catch :=util:function(xs:QName("xproc:catch"), 5);
 declare variable $xproc:group :=util:function(xs:QName("xproc:group"), 5);
 declare variable $xproc:for-each :=util:function(xs:QName("xproc:for-each"), 5);
 declare variable $xproc:viewport :=util:function(xs:QName("xproc:viewport"), 5);
 declare variable $xproc:library :=util:function(xs:QName("xproc:library"), 4);
 declare variable $xproc:pipeline :=util:function(xs:QName("xproc:pipeline"), 4);
 (: -------------------------------------------------------------------------- :)


 (: ------------------------------------------------------------------------------------------ :)
                                                                           (: XPROC COMPONENTS :)
 (: ------------------------------------------------------------------------------------------ :)


 (: -------------------------------------------------------------------------- :)
 declare function xproc:declare-step($primary,$secondary,$options,$currentstep,$outputs) {
 (: -------------------------------------------------------------------------- :)
    let $v := u:get-primary($primary)
    return
	    $v
 };


(: -------------------------------------------------------------------------- :)
declare function xproc:for-each($primary,$secondary,$options,$currentstep,$outputs) {
(: -------------------------------------------------------------------------- :)
 let $v := if ($currentstep/p:for-each/p:iteration-source/@select) then
                u:evalXPATH(string($currentstep/p:iteration-source/@select), u:get-primary($primary), $primary)
            else
                u:get-primary($primary)
 let $defaultname := concat(string($currentstep/@xproc:defaultname),'.0')
 let $subpipeline := $currentstep/node()
 let $namespaces := xproc:enum-namespaces($subpipeline)

 return
    for $child in $v/node()
    let $iteration-source := <xproc:output step="{$defaultname}"
                   port-type="output"
                   primary="false"
                  xproc:defaultname="$defaultname"
                   select="/"
                   port="iteration-source"
                   func="$xproc:for-each">
                    {$child}
                  </xproc:output>
    return

        (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >
        {$subpipeline}</p:declare-step>,$namespaces,$child,(),($outputs,$iteration-source))/.)[last()]/node()
 };

(: -------------------------------------------------------------------------- :)
declare function xproc:replace-matching-elements($element as element(),$select,$defaultname,$currentstep,$outputs) as element() {
(: -------------------------------------------------------------------------- :)
   let $namespaces := xproc:enum-namespaces($currentstep)
   return
   element {node-name($element)}
      {$element/@*,
          for $child in $element/node()
              return
              if ($child instance of element())
                then
            		if ($child intersect $select) then
                          (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >{$currentstep/node()}</p:declare-step>,(),$child,(),$outputs)/.)[last()]/node()
    			    else
                        xproc:replace-matching-elements($child,$select,$defaultname,$currentstep,$outputs)
                else
                    $child
      }
};


(: ------------------------------------------------------------------------------- :)
declare function xproc:viewport($primary,$secondary,$options,$currentstep,$outputs) {
(: ------------------------------------------------------------------------------- :)
     let $v := u:get-primary($primary)
     let $match := string($currentstep/@match[1])
     let $query := if (contains($match,'/')) then
                    $match
                  else
                    concat('.//',$match)
     let $matchresult := u:evalXPATH($match, $v, $primary)
     let $defaultname := concat(string($currentstep/@xproc:defaultname),'.0')
     return

        for $child in $v/node()
        return
            xproc:replace-matching-elements($child,
                                            $matchresult,
                                            $defaultname,
                                            $currentstep,
                                            $outputs)
 };


(: -------------------------------------------------------------------------- :)
declare function xproc:library($primary,$secondary,$options,$step) {
(: -------------------------------------------------------------------------- :)
    <test4/>
};


(: -------------------------------------------------------------------------- :)
declare function xproc:pipeline($primary,$secondary,$options,$step) {
(: -------------------------------------------------------------------------- :)
    <test5/>
};


(: -------------------------------------------------------------------------- :)
declare function xproc:group($primary,$secondary,$options,$currentstep,$outputs) {
(: -------------------------------------------------------------------------- :)
    let $v := u:get-primary($primary)
    let $namespaces := xproc:enum-namespaces($currentstep)
    let $defaultname := concat(string($currentstep/@xproc:defaultname),'.0')
    return
        (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >{$currentstep/node()}</p:declare-step>,$namespaces,$v,(),$outputs)/.)[last()]/node()
};


(: -------------------------------------------------------------------------- :)
declare function xproc:try($primary,$secondary,$options,$currentstep,$outputs) {
(: -------------------------------------------------------------------------- :)
    let $v := u:get-primary($primary)
    let $defaultname := concat(string($currentstep/@xproc:defaultname),'.0')
    let $namespaces := xproc:enum-namespaces($currentstep/p:group)
    return

         util:catch('*',
             (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >
                 {$currentstep/p:group}</p:declare-step>,$namespaces,$v,(),$outputs)/.)[last()]/node(),
             (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >
                 {$currentstep/p:catch}</p:declare-step>,$namespaces,$v,(),$outputs)/.)[last()]/node())
};

(: -------------------------------------------------------------------------- :)
declare function xproc:catch($primary,$secondary,$options,$currentstep,$outputs) {
(: -------------------------------------------------------------------------- :)
    let $v := u:get-primary($primary)
    let $namespaces := xproc:enum-namespaces($currentstep)
    let $defaultname := concat(string($currentstep/@xproc:defaultname),'.0')
    return
        (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >{$currentstep/node()}</p:declare-step>,$namespaces,$v,(),$outputs)/.)[last()]/node()
 };


(: ----------------------------------------------------------------------------- :)
declare function xproc:choose($primary,$secondary,$options,$currentstep,$outputs) {
(: ----------------------------------------------------------------------------- :)
    let $v := u:get-primary($primary)
    let $namespaces := xproc:enum-namespaces($currentstep)
    
    let $defaultname := concat(string($currentstep/@xproc:defaultname),'.0')
    let $xpath-context := <test1/>
    let $xpath-context-output := <xproc:output step="{$defaultname}"
                   port-type="output"
                   primary="false"
                  xproc:defaultname="$defaultname"
                   select="/"
                   port="xpath-context"
                   func="$xproc:choose">
                    {$xpath-context}
                  </xproc:output>

     let $whens := $currentstep/p:when
     let $otherwise := $currentstep/p:otherwise
    let $when := (
        for $when in $whens
            let $when_eval := u:evalXPATH(string($when/@test),$v, $primary)
            return
                if($when_eval) then
                    $when
                else
                    ()
        )
    return
        if ($when) then
            (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >{$when/node()}</p:declare-step>,$namespaces,$v,(),$outputs)/.)[last()]/node()
         else
            (u:call($xproc:parse-and-eval,<p:declare-step name="{$defaultname}" xproc:defaultname="{$defaultname}" >{$otherwise/node()}</p:declare-step>,$namespaces,$v,(),$outputs)/.)[last()]/node()

 };


 (: ------------------------------------------------------------------------ :)
 declare function xproc:run-step($primary,$secondary,$options,$step,$outputs) {
 (: ------------------------------------------------------------------------ :)
    let $v := u:get-primary($primary)
    let $pipeline := u:get-secondary('pipeline',$secondary)
    let $bindings := u:get-secondary('bindings',$secondary)
    let $dflag := u:get-option('dflag',$options,$v)
    let $tflag := u:get-option('tflag',$options,$v)
    let $step-options := ()
    return
        xproc:run($pipeline,$primary,$dflag,$tflag,$bindings,$step-options,$outputs)
 };


 (: ----------------------------------------------------------------------------------- :)
                                                                 (: PREPARSE II UTILS   :)
 (: ----------------------------------------------------------------------------------- :)

 (: ---------------------------------------------------------------- :)
 declare function xproc:get-step($stepname as xs:string,$declarestep) {
 (: ---------------------------------------------------------------- :)

     $const:std-steps/p:declare-step[@type=$stepname],
     $const:opt-steps/p:declare-step[@type=$stepname],
     $const:ext-steps/p:declare-step[@type=$stepname],
     $declarestep/@type
 };


 (: ----------------------------------------------------------------------------- :)
 declare function xproc:type($stepname as xs:string,$is_declare-step) as xs:string {
 (: ----------------------------------------------------------------------------- :)
 (: TODO - refactor at some point perhaps using base-uri ? :)

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
         'xproc'
     else if($is_declare-step) then
       string(substring-before($is_declare-step/@type,':'))
     else
       u:staticError('err:XS0044', concat($stepname,":",$stepname,' has no visible declaration'))
 };



 (: -------------------------------------------------------------------------------------- :)
                                                                    (: PREPARSE II ROUTINES:)
 (: -------------------------------------------------------------------------------------- :)


(: -------------------------------------------------------------------------- :)
declare function xproc:generate-explicit-input($step,$count,$xproc,$unique_before,$unique_id,$allstep){
(: -------------------------------------------------------------------------- :)
 (: TODO - this section will be refactored:)
 for $input in $step/p:input
     return
         if($input/*) then
             $input
         else
             element {node-name($input)}{
                attribute port{$input/@port},
                attribute primary{if (empty($input/@primary)) then 'true' else $input/@primary},
                attribute select{if(empty($input/@select)) then string('/') else $input/@select},

                if ($input/p:document or $input/p:inline or $input/p:empty or $input/p:data) then
                     $input/*
                else if($input/@primary eq 'false') then
                    (: ensure required non primary inputs are bound :)
                    if($allstep/p:input[@port = $input/@port][@xproc:required eq 'true']) then
                        u:staticError('err:XS0032', concat("static error during explicit binding pass:",$input,$allstep))
                    else
                        $input/*
                else if(name($step)="ext:pre" or name($step)="ext:post" ) then
                    let $a := $unique_before
                    let $a1 := substring-before($a,'.0')
                    let $b := tokenize($a1,'\.')[last()]
                    let $c := substring-before($a1,$b)
                    return
                        if($input/*/p:pipe or contains($b,'!')) then
                            $input/*
                        else
                        let $d := xs:integer($b) - 1
                        let $e := concat($c,$d)
                        return
                            if ($e eq '') then
                                let $part  := tokenize($unique_id,'\.')[last()]
                                let $part1 := xs:integer($part) - 1
                                let $part2 := substring-before($unique_id,$part)
                                let $part3 := concat($part2,$part1)
                                return
                                    <p:pipe step="{$part3}" port="result"/>
                            else
                                <p:pipe step="{$e}" port="result"/>
                else
                    let $l_count := $count - 1
                    return
                        if($l_count=0) then
                            <p:pipe step="{substring-before($unique_id,'.1')}"
                                    port="source"/>
                        else
                            <p:pipe step="{
                                    if (empty(string($xproc/*[$l_count]/@name)) or string($xproc/*[$l_count]/@name) eq '') then
                                        $unique_before
                                    else
                                        $xproc/*[$l_count]/@name
                                        }"
                             port="{$xproc/*[$l_count]/p:output[@primary='true']/@port}"/>
                }
 };


(: -------------------------------------------------------------------------- :)
declare function xproc:generate-explicit-output($step){
(: -------------------------------------------------------------------------- :)
       for $output in $step/p:output
         return
             $output
};


(: -------------------------------------------------------------------------- :)
declare function xproc:generate-explicit-options($step){
(: -------------------------------------------------------------------------- :)
       for $option in $step/p:with-option
         return
             $option
};


(: -------------------------------------------------------------------------- :)
declare function xproc:generate-step-binding($step,$xproc,$count,$stepname,$is_declare-step,$unique_id,$unique_before,$allstep){
(: -------------------------------------------------------------------------- :)
             element {if (empty($allstep/@xproc:use-function)) then node-name($step) else $allstep/@xproc:use-function} {
                 attribute name{ if(empty(string($step/@name)) or string($step/@name) eq '') then $unique_id else $step/@name},
                 attribute xproc:defaultname{$unique_id},
                 attribute xproc:type{xproc:type($stepname,$is_declare-step)},
                 attribute xproc:step{if (empty($allstep/@xproc:use-function)) then concat('$',xproc:type($stepname,$is_declare-step),':',local-name($step)) else concat('$',$allstep/@xproc:use-function)},
                 xproc:generate-explicit-input($step,$count,$xproc,$unique_before,$unique_id,$allstep),
                 xproc:generate-explicit-output($step),
                 xproc:generate-explicit-options($step)
             }
};


(: -------------------------------------------------------------------------- :)
declare function xproc:generate-component-binding($step,$xproc,$count,$stepname,$is_declare-step,$unique_id,$unique_before,$compstep){
(: -------------------------------------------------------------------------- :)
             element {node-name($step)} {
                $step/@*,
                 if ($const:comp-steps/xproc:element[@type=$stepname]/@xproc:step) then attribute xproc:defaultname{$unique_id} else (),
                 attribute xproc:type{xproc:type($stepname,$is_declare-step)},
                if ($const:comp-steps/xproc:element[@type=$stepname]/@xproc:step) then attribute xproc:step{concat('$',xproc:type($stepname,$is_declare-step),':',local-name($step))} else (),
                xproc:generate-explicit-input($step,$count,$xproc,$unique_before,$unique_id,$compstep),
                xproc:explicitbindings(document{$step/*[not(name(.) eq 'p:input')][not(name(.) eq 'p:output')]},$unique_id)
             }
 };


(: -------------------------------------------------------------------------- :)
declare function xproc:generate-declare-step-binding($step,$is_declare-step){
(: -------------------------------------------------------------------------- :)
     $step
};


(: -------------------------------------------------------------------------- :)
declare function xproc:explicitbindings($xproc,$unique_id){
(: -------------------------------------------------------------------------- :)
 let $pipelinename := $xproc/@name
 let $explicitbindings := document {

     for $step at $count in $xproc/*

         let $stepname := name($step)

         let $unique_before := u:uniqueid($unique_id,$count - 1)
         let $unique_current := u:uniqueid($unique_id,$count)
         let $unique_after := u:uniqueid($unique_id,$count + 1)

         let $is_declare-step := $xproc//p:declare-step[@type=$stepname]
         let $is_step := xproc:get-step($stepname,$is_declare-step)
         let $is_component := u:get-comp($stepname)

           return

             if ($is_declare-step) then
                xproc:generate-declare-step-binding($step,$is_declare-step)
             else if($is_step) then
                xproc:generate-step-binding($step,$xproc,$count,$stepname,$is_declare-step,$unique_current,$unique_before,$is_step)
             else if ($is_component) then
                xproc:generate-component-binding($step,$xproc,$count,$stepname,$is_declare-step,$unique_current,$unique_before,$is_component)
             else
                 u:staticError('err:XS0044', concat("static error during explicit binding pass:",$stepname,$step/@name,$is_declare-step,$is_step,$is_component))
     }

     return
         (: if dealing with nested components --------------------------------------------------------- :)
         if(empty($pipelinename)) then
             $explicitbindings
         else
         (: if dealing with p:pipeline component ------------------------------------------------------ :)
             <p:declare-step xmlns:xproc="http://xproc.net/xproc"
                             name="{$pipelinename}"
                             xproc:defaultname="{$unique_id}">
                 {$explicitbindings}
             </p:declare-step>
 };



 (: ------------------------------------------------------------------------------------------ :)
                                                                      (: RUN TIME EVAL ROUTINES:)
 (:--------------------------------------------------------------------------------------------:)


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-empty-binding(){
 (: -------------------------------------------------------------------------- :)
    (<!-- TODO: replace this !!!! returned empty //-->)
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-inline-binding($child){
 (: -------------------------------------------------------------------------- :)
    $child/node()
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-document-binding($child){
 (: -------------------------------------------------------------------------- :)
    if (doc-available(string($child/@href))) then
        doc(string($child/@href))
    else
        u:dynamicError('err:XD0002',concat(" cannot access document ",$child/@href))
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-data-binding($child){
 (: -------------------------------------------------------------------------- :)
    if ($child/@href) then
    element {
    if ($child/@wrapper) then
        string($child/@wrapper)
    else
        'c:data'
    } {
    
        if ($child/@xproc:escape eq 'true') then
            attribute xproc:escape{'true'}
        else
            (),
        if (starts-with($child/@href,'file:')) then
              util:binary-doc($child/@href)
        else if (ends-with($child/@href,'.xml')) then
        let $result :=  doc($child/@href)
        let $namespaces := u:declare-ns(xproc:enum-namespaces($result))
        return
          $result
        
        else
	        util:binary-to-string(util:binary-doc($child/@href))


(:          u:unparsed-data($child/@href,'text/plain')
:)
    }
  else
     u:dynamicError('err:XD0002',concat("cannot access document:  ",$child/@href))
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-stdin-binding($result,$current-step-name){
 (: -------------------------------------------------------------------------- :)
    $result/xproc:output[@port eq 'stdin'][@step eq $current-step-name]/node()
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-primary-input-binding($result,$pipeline-name){
 (: -------------------------------------------------------------------------- :)
    $result/xproc:output[@port eq 'result'][@step eq concat('!',$pipeline-name)]/node()
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-non-primary-input-binding($result,$child,$pipeline-name){
 (: -------------------------------------------------------------------------- :)
    $result/xproc:output[@port=$child/@port][@step eq concat('!',$pipeline-name)]/node()
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-pipe-binding($result,$child){
 (: -------------------------------------------------------------------------- :)

    if ($result/xproc:output[@port=$child/@port][@step eq $child/@step]) then
        $result/xproc:output[@port=$child/@port][@step eq $child/@step]/node()
    else if ($result/xproc:output[@port=$child/@port][@xproc:defaultname eq $child/@step]) then 
        $result/xproc:output[@port=$child/@port][@xproc:defaultname eq $child/@step]/node()
    else
        $result/xproc:output[last()]/node()
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-port-binding($child,$result,$pipeline,$currentstep){
 (: -------------------------------------------------------------------------- :)
    if(name($child)='p:empty') then
          xproc:resolve-empty-binding()

     else if(name($child) eq 'p:inline') then
         xproc:resolve-inline-binding($child)

     else if(name($child)='p:document') then
         xproc:resolve-document-binding($child)

     else if(name($child)='p:data') then
         xproc:resolve-data-binding($child)

     else if ($child/@port eq 'stdin' and $child/@step eq $pipeline/@name) then
         xproc:resolve-stdin-binding($result,$currentstep/@name)

     else if ($child/@primary eq 'true' and $child/@step eq $pipeline/@name) then
        xproc:resolve-primary-input-binding($result,$pipeline/@name)

     else if ($child/@step eq $pipeline/@name) then
        xproc:resolve-non-primary-input-binding($result,$child,$pipeline/@name)

     else if ($child/@port and $child/@step) then
        xproc:resolve-pipe-binding($result,$child)

    else
        u:dynamicError('err:XD0001',concat(" cannot bind to port: ",$child/@port," step: ",$child/@step,' ',u:serialize($currentstep,$const:TRACE_SERIALIZE)))

 };


 (:---------------------------------------------------------------------------:)
 declare function xproc:eval-primary($pipeline,$step,$currentstep,$primaryinput,$result){
 (: -------------------------------------------------------------------------- :)
 let $primaryresult := document{
     if($currentstep/p:input[@primary eq 'true']/node()) then
        (: resolve each nested port binding :)
         for $child in $currentstep/p:input[@primary eq 'true']/node()
             return
                xproc:resolve-port-binding($child,$result,$pipeline,$currentstep)
     else
            $primaryinput/node() (: prev step is an atomic step output:)
     }

    let $select := string($currentstep/p:input[@primary='true']/@select)
    let $selectval := if ($select eq '/' or $select eq '') then
                        $primaryresult
                     else
                        u:evalXPATH(string($select),$primaryresult)
        return

             if (empty($selectval)) then
                u:dynamicError('err:XD0016',concat(string($pipeline/*[@name=$step]/p:input[@primary='true'][@select]/@select)," did not select anything at ",$step," ",name($pipeline/*[@name=$step])))
             else
                 $selectval
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:eval-secondary($pipeline,$step,$currentstep,$primaryinput,$result){
 (: -------------------------------------------------------------------------- :)
     <xproc:inputs>{
         for $input in $currentstep/p:input[@primary eq 'false']
             return
             <xproc:input port="{$input/@port}" select="{$input/@select}">
             {
                 let $primaryresult := document{
                     for $child in $input/node()
                     return
                         xproc:resolve-port-binding($child,$result,$pipeline,$currentstep)
                        }

                 let $select := string(
                            if ($input/@select eq '/' or empty($input/@select) or $input/@select eq ' ') then
                                 '/'
                            else
                                 string($input/@select)
                             )

                 let $selectval := if ($select eq '/' or empty($select)) then
                                        $primaryresult
                                 else
                                        let $namespaces :=   u:list-used-namespaces ($primaryresult)
                                            return
                                                u:evalXPATH(string($select),$primaryresult)
                    return
                         if (empty($selectval)) then

         (: TODO: investigate empty bindings :)
          u:dynamicError('err:XD0016',concat(string($pipeline/*[@name=$step]/p:input[@primary='true'][@select]/@select)," did not select anything at ",$step," ",name($pipeline/*[@name=$step])))
                         else
                             $selectval
             }
             </xproc:input>

     }</xproc:inputs>
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:eval-options($pipeline,$step){
 (: -------------------------------------------------------------------------- :)
     <xproc:options>
         {$pipeline/*[@name=$step]/p:with-option}
     </xproc:options>
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:eval-outputs($pipeline,$step){
 (: -------------------------------------------------------------------------- :)
     <xproc:outputs>
         {$pipeline/*[@name=$step]/p:output}
     </xproc:outputs>
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:evalstep ($step,$namespaces,$primaryinput,$pipeline,$outputs) {
 (: -------------------------------------------------------------------------- :)
     let $declarens :=  u:declare-ns($namespaces)
     let $currentstep := $pipeline/*[@name=$step][1]
     let $stepfuncname := $currentstep/@xproc:step
     let $stepfunc := concat($const:default-imports,$stepfuncname)
     let $outputs := document{$outputs}

     let $primary := xproc:eval-primary($pipeline,$step,$currentstep,$primaryinput,$outputs)
     let $secondary := xproc:eval-secondary($pipeline,$step,$currentstep,$primaryinput,$outputs)

     let $options := xproc:eval-options($pipeline,$step)
     let $output := xproc:eval-outputs($pipeline,$step)

     let $log-href := $currentstep/p:log/@href
     let $log-port := $currentstep/p:log/@port

     return
         if(name($currentstep) = "p:declare-step") then
            (: TODO: refactor p:pipeline and p:declare-step :)
             ()
         else
            let $primaryinput:= <xproc:output step="{$step}"
                           port-type="input"
                           primary="true"
                           select="{$currentstep/p:input[1][@primary='true']/@select}"
                           port="{$currentstep/p:input[1][@primary='true']/@port}"
                           func="{$stepfuncname}">{
                                      $primary/node()
                          }
                     </xproc:output>
            let $secondaryinput: =(  for $child in $secondary/xproc:input
                 return
                     <xproc:output step="{$step}"
                           port-type="input"
                           primary="false"
                           select="{$child/@select}"
                           port="{$child/@port}"
                           func="{$stepfuncname}">{
                             $child/node()
                           }
                     </xproc:output>
                    )
            return
                ($primaryinput,
                $secondaryinput,
                if($currentstep/p:output[@primary='true']) then
                      <xproc:output step="{$step}"
                                       port-type="output"
                                       primary="true"
                                       xproc:defaultname="{$currentstep/@xproc:defaultname}"
                                       select="{$currentstep/p:output[@primary='true']/@select}"
                                       port="{$currentstep/p:output[@primary='true']/@port}"
                                       func="{$stepfuncname}">{
                                           if(contains($stepfuncname,'xproc:')) then
                                             u:call(u:xquery($stepfunc),$primary,$secondary,$options,$currentstep,($outputs,$primaryinput,$secondaryinput))
                                           else
                                             u:call(u:xquery($stepfunc),$primary,$secondary,$options)
                                       }
                      </xproc:output>
                else
                      <xproc:output step="{$step}"
                                       port-type="output"
                                       primary="false"
                                      xproc:defaultname="{$currentstep/@xproc:defaultname}"
                                       select="{$currentstep/p:output[@primary='false']/@select}"
                                       port="{if (empty($currentstep/p:output[@primary='false']/@port)) then 'result' else $currentstep/p:output[@primary='false']/@port}"
                                       func="{$stepfuncname}">
                                       {
                                           if(contains($stepfuncname,'xproc:')) then
                                             u:call(u:xquery($stepfunc),$primary,$secondary,$options,$currentstep,($outputs,$primaryinput,$secondaryinput))
                                           else
                                             u:call(u:xquery($stepfunc),$primary,$secondary,$options)
                                       }
                      </xproc:output>
             )
 };

 (: ------------------------------------------------------------------------------------------ :)
                                                                           (: CONTROL ROUTINES:)
 (: ------------------------------------------------------------------------------------------ :)


 (: --------------------------------------------------------------------------- :)
 declare function xproc:genstepnames($steps) as xs:string* {
 (: -------------------------------------------------------------------------- :)
     for $step in $steps/*[not(name()='p:documentation')]
     return
        xs:string($step/@name)
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:parse_and_eval($pipeline,$namespaces,$stdin,$bindings) {
 (: -------------------------------------------------------------------------- :)
 let $steps := xproc:genstepnames($pipeline)
 return
     u:step-fold($pipeline,
                 $namespaces,
                 $steps,
                 util:function(xs:QName("xproc:evalstep"), 5),
                 $stdin,
                 (xproc:resolve-external-bindings($bindings,$pipeline/@name),
                 (<xproc:output
                     step="{if ($steps[1] = '|') then 
                                '!1|'
                            else $steps[1]}"
                     port="stdin"
                     test="test"
                     port-type="external"
                     primary="false"
                     func="{$pipeline/@type}">{$stdin}</xproc:output>))
     )
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:parse_and_eval($pipeline,$namespaces,$stdin,$bindings,$outputs) {
 (: -------------------------------------------------------------------------- :)

     let $steps := xproc:genstepnames($pipeline)
     return
         u:step-fold($pipeline,
                     $namespaces,
                     $steps,
                     util:function(xs:QName("xproc:evalstep"), 5),
                     $stdin,
                    ($outputs,
                     xproc:resolve-external-bindings($bindings,$pipeline/@name),
                     <xproc:output
                        step="{concat('!',$pipeline/@name)}"
                        port="stdin"
                        port-type="external"
                        primary="false"
                        func="example">{$stdin}</xproc:output>
                         )
                        )
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:enum-namespaces($pipeline){
 (: -------------------------------------------------------------------------- :)
    <namespace name="{$pipeline/@name}">{u:enum-ns(<dummy>{util:expand($pipeline)}</dummy>)}</namespace>
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:resolve-external-bindings($bindings,$pipelinename){
 (: -------------------------------------------------------------------------- :)
 if (empty($bindings)) then
     () 
 else if($bindings/bindings) then
    for $binding in $bindings/bindings/binding
    return
        <xproc:output port-type="external" port="{$binding/@port}" step="{concat('!',$pipelinename)}">
            {
            if ($binding/@uri) then
                doc($binding/@uri)
            else
                $binding/*
            }
        </xproc:output>
 else
    (: TODO- this is used for tests.xproc.org unit testing purposes :)
    for $binding in $bindings/*
    return
   <xproc:output port-type="external" port="{$binding/@port}" step="{concat('!',$pipelinename)}">
       {$binding/node()}
   </xproc:output>
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:output($result,$dflag){
 (: -------------------------------------------------------------------------- :)
 let $pipeline :=subsequence($result,1,1)
 let $output := <xproc:outputs>{ subsequence($result,2) } </xproc:outputs>
     return
         if($dflag eq "1") then
             <xproc:debug>
                 <xproc:pipeline>{$pipeline}</xproc:pipeline>
                {$output}
             </xproc:debug>
         else
            (: TODO - define default p:serialization options here:)
            let $stdout := $output//*[@port eq 'stdout']/node()
            let $count := count ($result)
         return
               if (empty($stdout)) then
                subsequence($result,$count - 2,1)/node()
               else
                  $stdout
 };

 (: ----------------------------------------------------------------------------------- :)
                                                                      (: ENTRY POINTS   :)
 (: ----------------------------------------------------------------------------------- :)


 (: -------------------------------------------------------------------------- :)
 declare function xproc:run($pipeline,$stdin){
 (: -------------------------------------------------------------------------- :)
    xproc:run($pipeline,$stdin,"0","0",(),())
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:run($pipeline,$stdin,$debug){
 (: -------------------------------------------------------------------------- :)
    xproc:run($pipeline,$stdin,$debug,"0",(),())
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:run($pipeline,$stdin,$debug,$bindings,$options){
 (: -------------------------------------------------------------------------- :)
    xproc:run($pipeline,$stdin,$debug,"0",$bindings,$options)
 };


 (: entry point :)
 (: -------------------------------------------------------------------------- :)
 declare function xproc:run($pipeline,$stdin,$dflag,$tflag,$bindings,$options){
 (: -------------------------------------------------------------------------- :)
    let $internaldbg := 0
    return
         if ($internaldbg eq 1) then
                     xproc:explicitbindings(
                       naming:explicitnames(
                             naming:fixup($pipeline,$stdin)
                       )
                     ,$const:init_unique_id
                     )
         else if ($internaldbg eq 2) then
                       naming:explicitnames(
                             naming:fixup($pipeline,$stdin)
                     )
         else if ($internaldbg eq 3) then
                $bindings
         else
     (
     (: STEP I: generate parse tree :)
     let $namespaces := xproc:enum-namespaces($pipeline)
     let $preparse-naming := naming:explicitnames(naming:fixup($pipeline,$stdin))
     let $xproc-binding := xproc:explicitbindings($preparse-naming,$const:init_unique_id)

     (: STEP II: parse and eval tree :)
     let $eval_result := xproc:parse_and_eval($xproc-binding,$namespaces,$stdin,$bindings)

     (: STEP III: serialize and return results :)
     let $serialized_result := xproc:output($eval_result,string($dflag))
     return
          if ($tflag="1") then
                 document
                    {
                     <xproc:result xproc:timing="disabled" xproc:ts="{current-dateTime()}">
                         {
                          $serialized_result
                         }
                     </xproc:result>
                     }
              else
                     $serialized_result
         )
 };


 (: -------------------------------------------------------------------------- :)
 declare function xproc:run($pipeline,$stdin,$dflag,$tflag,$bindings,$options,$outputs){
 (: -------------------------------------------------------------------------- :)
 if (exists($pipeline)) then

     let $namespaces := xproc:enum-namespaces($pipeline)

     (: STEP I: generate parse tree :)
     let $preparse-naming := naming:explicitnames(naming:fixup($pipeline,$stdin))
     let $xproc-binding := xproc:explicitbindings($preparse-naming,$const:init_unique_id)

     (: STEP II: parse and eval tree :)
     let $eval_result := xproc:parse_and_eval($xproc-binding,$namespaces,$stdin,$bindings,$outputs)

     (: STEP III: serialize and return results :)
     let $serialized_result := xproc:output($eval_result,"0")

     let $internaldbg := 0

     return
         if ($internaldbg eq 1) then
                     xproc:explicitbindings(
                       naming:explicitnames(
                             naming:fixup($pipeline,$stdin)
                       )
                     ,$const:init_unique_id
                     )
         else if ($internaldbg eq 2) then
                       naming:explicitnames(
                             naming:fixup($pipeline,$stdin)
                       )
         else if ($internaldbg eq 3) then
                     $eval_result
         else
         (
          if ($tflag="1") then
                 document
                    {
                     <xproc:result xproc:timing="disabled" xproc:ts="{current-dateTime()}">
                         {
                          $serialized_result
                         }
                     </xproc:result>
                     }
              else
                     $serialized_result
         )
 else
    u:xprocxqError('xxq-error:XXQ0003','check pipeline.')
 };