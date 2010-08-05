xquery version "1.0" encoding "UTF-8";
module namespace naming = "http://xproc.net/xproc/naming";
(: ------------------------------------------------------------------------------------

	naming.xqm - manages the first pass parsing of xproc pipeline, providing the output
	in topological order and cross referencing step with defined functional signatures.

---------------------------------------------------------------------------------------- :)


(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";
declare namespace xproc = "http://xproc.net/xproc";
declare namespace xxq-error = "http://xproc.net/xproc/error";

(: Module Imports :)
import module namespace const = "http://xproc.net/xproc/const" at "resource:net/xproc/xprocxq/src/xquery/const.xqm";
import module namespace u = "http://xproc.net/xproc/util" at "resource:net/xproc/xprocxq/src/xquery/util.xqm";
import module namespace std = "http://xproc.net/xproc/std" at "resource:net/xproc/xprocxq/src/xquery/std.xqm";
import module namespace opt = "http://xproc.net/xproc/opt" at "resource:net/xproc/xprocxq/src/xquery/opt.xqm";
import module namespace ext = "http://xproc.net/xproc/ext" at "resource:net/xproc/xprocxq/src/xquery/ext.xqm";


(: -------------------------------------------------------------------------- :)
declare function naming:get-step($stepname as xs:string) {
(: -------------------------------------------------------------------------- :)
    $const:std-steps/p:declare-step[@type=$stepname],
    $const:opt-steps/p:declare-step[@type=$stepname],
    $const:ext-steps/p:declare-step[@type=$stepname]
};


(: -------------------------------------------------------------------------- :)
declare function naming:type($stepname as xs:string,$is_declare-step) as xs:string {
(: -------------------------------------------------------------------------- :)

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


                     (: --------------------------------------------------------------------------- :)
                                                                              (: PREPARSE I ROUTINES:)
                     (: --------------------------------------------------------------------------- :)


(: -------------------------------------------------------------------------- :)
declare function naming:preparse-options($allstep,$step,$stepname){
(: -------------------------------------------------------------------------- :)
    for $option in $allstep
        return

            if ($step/p:with-option[@name=$option/@name] and $step/@*[name(.)=$option/@name]) then

               u:staticError('err:XS0027', concat($stepname,":",$step/@name,' option:',$option/@name,' duplicate options'))

            else if ($option/@required eq 'true' and $option/@select) then

               u:staticError('err:XS0017', concat($stepname,":",$step/@name,' option:',$option/@name,' duplicate options'))

            else if ($step/p:with-option[@name=$option/@name]) then

               <p:with-option name="{$option/@name}" select="{$step/p:with-option[@name=$option/@name]/@select}"/>

            else if($step/@*[name(.)=$option/@name]) then

               <p:with-option name="{$option/@name}" select="{concat("'",$step/@*[name(.)=$option/@name],"'")}"/>

            else if($option/@select) then

               <p:with-option name="{$option/@name}" select="{$option/@select}"/>

            else if(not($step/p:with-option[@name=$option/@name] and $step/@*[name(.)=$option/@name]) and $option/@required eq 'true') then

                u:staticError('err:XS0018', concat($stepname,":",$step/@name,' option:',$option/@name,' is required and seems to be missing or incorrect'))

            else
                (: TODO: may have to throw additional errors before this :)
                <p:with-option name="{$option/@name}" select="{$option/@default}"/>
};


(: -------------------------------------------------------------------------- :)
declare function naming:preparse-input-bindings($allstep,$step,$allbindings){
(: -------------------------------------------------------------------------- :)

if ($allbindings eq 'all') then
    $step/p:input
else
    for $binding in $allstep
       let $currentport := $step/*[@port=$binding/@port]
        return
            element {node-name($binding)} {
               $binding/@port,
               $binding/@primary,
               $binding/@kind,
               $binding/@sequence,

               if($currentport/@select='') then
                  attribute select{$binding/@select}
               else if($currentport/@select) then
                  $currentport/@select
               else
                  attribute select{$binding/@select},
                   $currentport/*
               }
};


(: -------------------------------------------------------------------------- :)
declare function naming:preparse-output-bindings($allstep,$step,$allbindings){
(: -------------------------------------------------------------------------- :)

if ($allbindings eq 'all') then
    $step/p:output
else
    for $binding in $allstep
       let $currentport := $step/*[@port=$binding/@port]
        return
            element {node-name($binding)} {
               $binding/@port,
               $binding/@primary,
               $binding/@kind,
               $binding/@sequence,
               if($currentport/@select='') then
                  attribute select{$binding/@select}
               else if($currentport/@select) then
                  $currentport/@select
               else
                  attribute select{$binding/@select},
                   $currentport/*
               }
};



(: -------------------------------------------------------------------------- :)
declare function naming:pipeline-step-sort($unsorted, $sorted, $pipelinename )  {
(: -------------------------------------------------------------------------- :)
    if (empty($unsorted)) then
        ($sorted)
    else
        let $allnodes := $unsorted [ every $id in p:input[@primary eq 'true'][@port eq 'source']/p:pipe/@step satisfies ($id = $sorted/@name or $id=$pipelinename)]
    return
        if ($allnodes) then
            naming:pipeline-step-sort( $unsorted except $allnodes, ($sorted, $allnodes ),$pipelinename)
        else
            ()
};


(: -------------------------------------------------------------------------- :)
declare function naming:generate-step($xproc,$is_step,$step,$stepname){
(: -------------------------------------------------------------------------- :)
if($is_step/@xproc:support) then
    element {node-name($step)} {
        attribute name{$step/@name},
		(: TODO - need to fix input/output preparse bindings :)
        naming:preparse-input-bindings($is_step/p:*[@primary='true'],$step,$is_step/@xproc:bindings),
        naming:preparse-output-bindings($is_step/p:*[@primary='false'],$step,$is_step/@xproc:bindings),
        naming:preparse-options($is_step/p:option,$step,$stepname)
   }
else if($is_step/@type) then
    <ext:xproc name="{if ($step/@name) then $step/@name else ''}">
        <p:input port="source" primary="true" select="/"/>
        <p:output port="result" primary="true" select=""/>
        <p:input port="pipeline" primary="false" select="/">
           <p:inline>
                <p:declare-step name="{$step/@name}">
                {$is_step/*}
                </p:declare-step>
           </p:inline>
        </p:input>
        <p:input port="bindings" primary="false" select="/"/>
        <p:with-option name="dflag" select="'0'"/>
        <p:with-option name="tflag" select="'0'"/>
    </ext:xproc>
else
	u:xprocxqError('xxq-error:XXQ0001',concat($stepname,":",$step/@name,u:serialize($step,$const:TRACE_SERIALIZE)))
};


(: -------------------------------------------------------------------------- :)
declare function naming:generate-component($xproc,$is_comp,$step,$stepname){
(: -------------------------------------------------------------------------- :)
if($is_comp/@xproc:support) then
        element {node-name($step)} {
            if ($is_comp/@xproc:step = "true") then (attribute name{$step/@name},$step/@*[not(name(.) eq 'name')]) else  $step/@*,

            (: TODO - will need to fixup top level input/output ports :)
            <ext:pre test="test">
				{naming:preparse-output-bindings($is_comp/p:*[@primary='true'],$step,())}
			</ext:pre>,

            naming:explicitnames(document{$step/*})
        }
else
	u:xprocxqError('xxq-error:XXQ0002',concat($stepname,":",$step/@name,u:serialize($step,$const:TRACE_SERIALIZE)))

};


(: -------------------------------------------------------------------------- :)
declare function naming:explicitnames($xproc as item()){
(: -------------------------------------------------------------------------- :)

if(empty($xproc/*)) then
    ()
else
    let $pipelinename := $xproc/@name
    let $explicitnames :=

        for $step at $count in $xproc/*
            let $stepname := name($step)
            let $is_declared_step := $xproc/p:declare-step[@type=$stepname]
            let $is_standard_step := $const:std-steps/p:declare-step[@type=$stepname]
            let $is_optional_step := $const:opt-steps/p:declare-step[@type=$stepname]
            let $is_extension_step := $const:ext-steps/p:declare-step[@type=$stepname]
            let $is_step := ($is_declared_step,
                             $is_standard_step,
                             $is_optional_step,
                             $is_extension_step)
            let $is_component := $const:comp-steps//xproc:element[@type=$stepname]
            return
               if ( name($step) eq 'p:import') then
                    ()
               else if (name($step) eq 'p:documentation') then
                    ()
               else if (exists($is_component) and $step/@type)then
                    ()
               else if(exists($is_step)) then
                    (: generate std,opt,ext and declared steps:)
                    naming:generate-step($xproc,$is_step,$step,$stepname)
               else if (exists($is_component)) then
                    (: generate p:declare-step and all other xproc components :)
                    naming:generate-component($xproc,$is_component,$step,$stepname)
               else
                    (: throws error on unknown element in pipeline namespace :)
                    u:staticError('err:XS0044', concat("static error during explicit naming pass:  ",$stepname,":",$step/@name,u:serialize($step,$const:TRACE_SERIALIZE)))
    return

		let $sorted := naming:pipeline-step-sort($explicitnames,(),$pipelinename)
		return
        	if(empty($pipelinename))then
    			$sorted
        	else
            	<p:declare-step name="{$pipelinename}">
                {
                    $sorted
                }
                <ext:post name="{$pipelinename}!">
					{$xproc/p:serialization/@*}
                    <p:input port="source" primary="true"/>
                    <p:output primary="true" port="stdout" select="/"/>
                </ext:post>
            	</p:declare-step>
};




(: -------------------------------------------------------------------------- :)
declare function naming:fixup($xproc as item(),$stdin){
(: -------------------------------------------------------------------------- :)
let $pipeline := $xproc/p:*[name(.) = "p:pipeline" or name(.) ="p:declare-step"]
let $steps := <p:declare-step>
               <ext:pre name="!{$pipeline/@name}">
            {
            if ($pipeline/p:input[@primary='true']) then
                    $pipeline/p:input[@primary='true']
            else
                    <p:input port="source"
                             kind="document"
                             primary="true"
                             select="{if(empty($pipeline/p:input[@port='source']/@select)) then '/' else $pipeline/p:input[@port='source']/@select}"
                             sequence="{$pipeline/p:input[@port='source']/@sequence}">
                    {if($stdin) then
                        <p:pipe step="{$pipeline/@name}" port="stdin"/>
                    else
                        $pipeline/p:input[@port='source'][@primary="true"]
                    }
                    </p:input>,

            <p:output port="result" primary="true" select="{if ($pipeline/p:output[@port='result']/@select) then $pipeline/p:output[@port='result']/@select else '/' }"/>,

            $pipeline/p:input[@primary='false'],

            $pipeline/p:output[@primary='false']
            }
           </ext:pre>
            {
               for $import in $xproc/*/p:import
                return
                    if (doc-available($import/@href)) then
                    let $imported := doc($import/@href)
                    return
                          if ($imported/p:library)then
                            $imported/p:library/*
                          else
                            $imported/p:pipeline/*
                    else
                          u:dynamicError('XD0002',"cannot import pipeline document ")
            }

            {$pipeline/*[not(name(.)="p:input")][not(name(.)="p:output")][not(name(.)="p:import")]}

</p:declare-step>
return
    <p:declare-step name="{if ($pipeline/@name) then $pipeline/@name else 'xproc:default-pipeline'}">
        {$steps/*}
    </p:declare-step>
};


