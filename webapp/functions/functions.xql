xquery version "1.0";

(: $Id$ :)

(:~
    Main XQuery script for the RESTful function docs application.
    
    The parameters passed in from controller.xql are:
        basepath: Used as base path for URLs
        module:   Module name
        function: Function name
        
    The controller.xql intercepts URLs like
        http://exist-db.org/exist/functions/xmldb/login
    and parses and passes these parameters transparently to the script:
        basepath: /exist/functions/
        module:   xmldb
        function: login
:)

(: eXist module namespaces :)
declare namespace request="http://exist-db.org/xquery/request";
declare namespace text="http://exist-db.org/xquery/text";
declare namespace util="http://exist-db.org/xquery/util";

(: Document namespaces :)
declare namespace xq="http://www.xqdoc.org/1.0";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

(: App root URL, passed in from controller.xql.  Used as base path for URLs. :)
declare variable $basepath := request:get-parameter('basepath', ());


(: Render a function or a set of functions :)
declare function local:render-function( $functionNode as element(xq:function)+, $showlinks as xs:boolean) as element() {
    let $parentModule := $functionNode/ancestor::xq:xqdoc/xq:module/xq:uri/text()
    let $parentModuleName := $functionNode/ancestor::xq:xqdoc/xq:module/xq:name/text()
    let $functionName := $functionNode/xq:name/text()
    let $functionSignature := $functionNode/xq:signature/text()
    let $functionDeprecated := $functionNode/xq:comment/xq:deprecated/text()
    let $functionDescription := $functionNode/xq:comment/xq:description/text()
    let $functionVersion := $functionNode/xq:comment/xq:version/text()
    let $functionParameters := $functionNode/xq:comment/xq:param
    let $functionSees := $functionNode/xq:comment/xq:see
    let $functionReturns := $functionNode/xq:comment/xq:return/text()
    let $authors := $functionNode/xq:comment/xq:author
    let $sinces := $functionNode/xq:comment/xq:since
    return
        <div class="function">
            <div class="name">{
                if ($showlinks) then
                    <a href="{local:function-to-URL($parentModule, $functionName)}">{$functionName}</a>
                else $functionName
            }</div>
            <hr/>
            <div class="signature">{
                if (not(starts-with($functionSignature, $parentModuleName))) then 
                    concat($parentModuleName, ':', $functionSignature) 
                else 
                    $functionSignature
            }</div>
            <div class="description">
                <div class="f-description-para">{$functionDescription}</div>
            </div>
            <div class="authors">{
                for $author in $authors
                return
                    <span class="author">{$author}</span>
            }</div>
            {
            if (string-length($functionVersion) > 0) then
                <div class="version">Version {$functionVersion}</div>
            else ()    
            }
            <div class="sinces">{
                if (empty($sinces)) then ()
                else
                    <div class="since">Since {string-join($sinces, ", ")}</div>
            }</div>
            <div class="sees">{
                for $see in $functionSees
                return
                    <div class="see">See <a href="{$see}">{$see/text()}</a></div>
            }</div>
            <div class="parameters">
                <table class="f-params">{
                    for $parameter in $functionParameters
                    let $split := text:groups(normalize-space($parameter), "^(\$[^ ]+) (.*)$")
                    return
                        <tr>
                            <td class="f-param1">{$split[2]}</td>
                            <td class="f-param2">{$split[3]}</td>
                        </tr>
                }</table>
            </div>
            {
            if (string-length($functionReturns) > 0) then
                <div class="returning"><hr/>Returns {$functionReturns}</div>
            else (),
            if (string-length($functionDeprecated) > 0) then
                <div class="deprecated"><hr/>Deprecated: {$functionDeprecated}</div>
            else ()
            }
        </div>
};

(: Render a module or set of modules :)
(: TODO add moduleSees, other standard XQDoc comments :)
declare function local:render-module($moduleURIs as xs:string+, $functionName as xs:string?, $showlinks as xs:boolean) as element()+ {
    for $moduleURI in $moduleURIs
	let $moduleDocs := /xq:xqdoc[xq:module/xq:uri eq $moduleURI]
	let $moduleName := if ($moduleURI = 'http://www.w3.org/2005/xpath-functions') then 'fn' else $moduleDocs/xq:module/xq:name/text()
	let $moduleDescription := $moduleDocs/xq:module/xq:comment/xq:description/node()
    let $moduleSees := $moduleDocs/xq:module/xq:comment/xq:see
	order by $moduleURI
	return 
	   <div class="module">
	       <div class="modhead">
               <div class="name">{
                  if ($showlinks) then
                     <a href="{local:moduleURI-to-URL($moduleURI)}">{$moduleName}</a>
                  else 
                     $moduleName
               }</div>
               <hr/>
    	       <div class="uri">{$moduleURI}</div>
    	       <div class="description">{$moduleDescription}</div>
    	       <div class="sees">{
                    for $see in $moduleSees
                    return
                        <div class="see">See <a href="{$see}">{$see/text()}</a></div>
               }</div>
	       </div>
	       <div class="functions">{
	           (: Decide whether to render a single function or to render them all :)
	           if ($functionName) then 
	               for $functionNode in $moduleDocs/xq:functions/xq:function[xq:name eq $functionName]
                   return local:render-function($functionNode, $showlinks) 
               else
    	           for $functionNode in $moduleDocs/xq:functions/xq:function
    	           return local:render-function($functionNode, $showlinks)
	       }</div>
	   </div>
};

(: 
 : URL handling functions 
 :)

(: Constructs a URL from a module URI :)
declare function local:moduleURI-to-URL($moduleURI as xs:string) as xs:string {
    if ($moduleURI = 'http://www.w3.org/2005/xpath-functions') then 
        concat($basepath, 'fn')
    else 
        concat($basepath, /xq:xqdoc/xq:module[xq:uri eq $moduleURI]/xq:name/text())
};

(: Constructs a URL from a module :)
declare function local:module-to-URL($module as xs:string) as xs:string {
    concat($basepath, $module)
};

(: Constructs a URL from a function :)
declare function local:function-to-URL($moduleURI as xs:string, $function as xs:string) {
    concat(local:moduleURI-to-URL($moduleURI), '/', $function)
};

(: Derives the module name from the module URI :)
declare function local:moduleURI-to-module($moduleURI as xs:string) as xs:string {
    if ($moduleURI = 'http://www.w3.org/2005/xpath-functions') then 
        'fn'
    else 
        /xq:xqdoc/xq:module[xq:uri eq $moduleURI]/xq:name/text()
};

(: Lists all modules :)
declare function local:list-modules($moduleURIs) {
    (
    <p class="f-reload"><a href="{request:get-context-path()}/admin/admin.xql?panel=fundocs">Reload documentation</a>
        (click here if you enabled/disabled additional modules)</p>
    ,
    <p class="f-info">(<b>eXist version: {util:system-property("product-version")}, 
        build: {util:system-property("product-build")}, functions: {count(//xq:function)}.)</b> 
        Modules have to be enabled in conf.xml to appear here.</p>
    ,
    <p><a href="{concat($basepath, 'all')}">Expand All</a> or select a single module:</p>
    ,
    <table class="modules">
        <thead>
            <tr><td>Name</td><td>Description</td><td>Namespace URI</td></tr>
        </thead>
        <tbody>{
            for $moduleURI in $moduleURIs
            let $moduleDocs := /xq:xqdoc[xq:module/xq:uri eq $moduleURI]
        	let $moduleName := 
        	   if ($moduleURI eq 'http://www.w3.org/2005/xpath-functions') then 
        	       (: note: the xq:name for the xpath functions module is empty 
        	        : since it's the default function namespace, but we need a name
        	        : for the link, so we just use 'fn' :) 
        	       'fn' 
               else $moduleDocs/xq:module/xq:name/text()
        	let $moduleDescription := string-join(data($moduleDocs/xq:module/xq:comment/xq:description/node()), '')
        	let $moduleDescription := if (string-length($moduleDescription) lt 90) then $moduleDescription else concat(substring($moduleDescription, 1, 90), '...')
        	order by $moduleName
            return
                <tr class="module">
                    <td class="mod-entry"><a href="{local:moduleURI-to-URL($moduleURI)}">{$moduleName}</a></td>
                    <td class="mod-entry-desc">{if ($moduleDescription) then $moduleDescription else <em>No description present</em>}</td>
                    <td class="mod-entry">{$moduleURI}</td>
                </tr>
        }</tbody>
    </table>
    )
};

(: Breadcrumbs for the page :)
declare function local:breadcrumbs($module, $function, $viewall) {
    <div id="breadcrumbs">
        <a href="{$basepath}">Modules</a>
        {
        if ($viewall) then 
            ' > All Modules and Functions'
        else if ($module and not($function)) then 
            (
            ' > '
            ,
            <a href="{local:module-to-URL($module)}">{$module}</a>
            )
        else if ($module and $function) then
            (
            ' > '
            ,
            <a href="{local:module-to-URL($module)}">{$module}</a>
            ,
            ' > '
            ,
            <a href="{local:function-to-URL($module, $function)}">{$function}</a>
            )
        else 
            ' > Home'
        }
    </div>
};

(: Footer-like 'return to home' link :)
declare function local:return-to-main() as element() {
    <div>
        <a href="{$basepath}">Return to list of all modules</a>
    </div>
};

(: Business logic :)
declare function local:main() {
    (: Get URL parameters :)
    let $module := request:get-parameter('module', ())
    let $function := request:get-parameter('function', ())
    let $showlinks := request:get-parameter('showlinks', 1) cast as xs:boolean
    let $q := request:get-parameter('q', ())
    
    (: Catch a request for 'all', i.e. the complete listing :) 
    let $viewall := $module eq 'all'

    (: Derive module URIs based on URL parameter $module :)
    let $allModuleURIs := /xq:xqdoc/xq:module/xq:uri
    let $matchingModuleURIs := if ($module) then 
        if ($module = 'fn') then 
            'http://www.w3.org/2005/xpath-functions' 
        else 
            $allModuleURIs[following-sibling::xq:name eq $module] 
        else ()
    let $moduleURIs := if ($matchingModuleURIs) then $matchingModuleURIs else $allModuleURIs

    return
        (
        (: Show documentation for all modules:)
        if ($viewall) then 
            (
            local:breadcrumbs($module, $function, $viewall)
            ,
            local:render-module($moduleURIs, (), $showlinks)
            ,
            local:return-to-main()
            )
        (: Show just the list of modules :)
        else if (not($module)) then
            (
            local:breadcrumbs($module, $function, $viewall)
            ,
            local:list-modules($moduleURIs)
            )
        (: Show a single module :)
        else if ($module and $matchingModuleURIs) then
            if (count($matchingModuleURIs) gt 1) then 
                (: TODO: Add handling for resolving modules with the same 'name' :)
                (
                local:breadcrumbs($module, $function, $viewall)
                ,
                local:render-module($moduleURIs, $function, $showlinks)
                ,
                local:return-to-main()
                )                
            else
                (
                local:breadcrumbs($module, $function, $viewall)
                ,
                local:render-module($moduleURIs, $function, $showlinks)
                ,
                local:return-to-main()
                )
        (: Handle cases where there are no exact matching modules :)
        else 
            (: else if ($module and not($matchingModules)) :)
            (: TODO add a fuzzy search :)
            (
            local:breadcrumbs((), (), $viewall)
            ,
            <p>Sorry, there are no modules that match "{$module}."  Please choose from the list below:</p>
            ,
            local:list-modules($moduleURIs)
            )
        )

};

(: Module body :)
<book>
    <bookinfo>
        <graphic fileref="logo.jpg"/>
        <productname>Open Source Native XML Database</productname>
        <title>eXist Function Modules</title>
        <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
		<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
        <style language="text/css">
        <![CDATA[
            body {font-family: Arial, Helvetica, sans-serif; } 
            .module {border: 1px black solid; margin:20px; page-break-after: always; }
            .modhead {padding: 5px;}
            thead {font-size: larger; font-weight: bold; }
            .functions {margin-left: 10px; }
            .function {border: 1px black solid; padding:5px; margin:5px; page-break-before: auto; page-break-inside: avoid; font-family: Arial, Helvetica, sans-serif; }
            .name {font-weight: bold;}
            .signature {margin-left: 50px; font-style: italic;}
            .description {margin-left: 20px; padding: 5px;}
            .f-description-para {margin-bottom: 8px; white-space: pre-wrap;}
            .sees {margin-left: 20px; padding: 5px;}
            .see {margin-bottom: 8px; white-space: pre-wrap;}
            .authors {display:none;}
            .parameters {margin-left: 100px;}
            .mod-entry {vertical-align:top;}
            .mod-entry-desc {vertical-align:top;}
            .f-params {}
            .f-param1 {width: 250px; vertical-align:top;}
            .f-param2 {white-space: pre-wrap;}
            .version {margin-left: 20px; padding: 5px;}
            .since {margin-left: 20px; padding: 5px;}
            .parameter {}
            .returning {margin-left: 100px; color:green; white-space: pre-wrap;}
            .deprecated {color:red; font-weight:bold;}
            #breadcrumbs {margin: 1em 0 1em 0; }
            ]]>
        </style>
    </bookinfo>
    
        <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="../sidebar.xml"/>
    
    <chapter>
        <title>A RESTful browser for eXist Function Modules</title>
        { local:main() }
    </chapter>
</book>