xquery version "1.0";

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
    let $parentModule := $functionNode/ancestor::xq:xqdoc//xq:uri/text()
    let $functionName := $functionNode/xq:name/text()
    let $functionSignature := $functionNode/xq:signature/text()
    let $functionDeprecated := $functionNode/xq:comment/xq:deprecated/text()
    let $functionDescription := $functionNode/xq:comment/xq:description/text()
    let $functionParagraphs := tokenize($functionDescription, "\n")
    let $functionVersion := $functionNode/xq:comment/xq:version
    let $functionParameters := $functionNode/xq:comment/xq:param
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
            <div class="signature">{$functionSignature}</div>
            <div class="description">{
                for $desc in $functionParagraphs
                return
                    <div class="f-description-para">{$desc}</div>
            }</div>
            <div class="authors">{
                for $author in $authors
                return
                    <span class="author">{$author}</span>
            }</div>
            <div class="version">{$functionVersion}</div>
            <div class="sinces">{
                for $since in $sinces
                return
                    <span class="since">{$since}</span>
            }</div>
            <div class="parameters">
                <table class="f-params">{
                    for $parameter in $functionParameters
                    let $split := text:groups($parameter, "^(\$[^ ]+) (.*)$")
                    return
                        <tr>
                            <td class="f-param1">{$split[2]}</td>
                            <td class="f-param2">{$split[3]}</td>
                        </tr>
                }</table>
            </div>
            {
            if (string-length($functionReturns) > 0)
            then
                <div class="returning"><hr/>Returns {$functionReturns}</div>
            else (),
            if (string-length($functionDeprecated) > 0)
            then
                <div class="deprecated"><hr/>Deprecated: {$functionDeprecated}</div>
            else ()
            }
        </div>
};

(: Render a module or set of modules :)
declare function local:render-module($moduleURIs as xs:string+, $functionName as xs:string?, $showlinks as xs:boolean) as element()+ {
    for $moduleURI in $moduleURIs
	let $moduleDocs := util:extract-docs($moduleURI)
	let $moduleName := $moduleDocs/xq:module/xq:name/text()
	let $moduleDescription := $moduleDocs/xq:module/xq:comment/xq:description/text()
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
 : App URL handling functions 
 :)

(: Constructs a URL from a module URI :)
declare function local:moduleURI-to-URL($moduleURI as xs:string) as xs:string {
    concat($basepath, tokenize($moduleURI, '/')[last()])
};

(: Constructs a URL from a module :)
declare function local:module-to-URL($module as xs:string) as xs:string {
    let $moduleURI := local:module-to-moduleURI($module)
    return local:moduleURI-to-URL($moduleURI)
};

(: Constructs a URL from a function :)
declare function local:function-to-URL($moduleURI as xs:string, $function as xs:string) {
    concat(local:moduleURI-to-URL($moduleURI), '/', $function)
};

(: Derives a module URI from a shorthand :)
declare function local:module-to-moduleURI($module as xs:string) as xs:string {
    util:registered-modules()[ends-with(., $module)]
};

(: Lists all modules :)
declare function local:list-modules($moduleURIs) {
    (
    <p><a href="./all">Expand All</a> or select a single module:</p>
    ,
    <table class="modules">
        <thead>
            <tr><td>Name</td><td>Description</td><td>URI</td></tr>
        </thead>
        <tbody>{
            for $moduleURI in $moduleURIs
            let $moduleDocs := util:extract-docs($moduleURI)
        	let $moduleName := 
        	   if ($moduleURI eq 'http://www.w3.org/2005/xpath-functions') then 
        	       (: note: the xq:name for the xpath functions module is empty 
        	        : since it's the default function namespace, but we need a name
        	        : for the link, so we just use 'fn' :) 
        	       'fn' 
               else $moduleDocs/xq:module/xq:name/text()
        	let $moduleDescription := $moduleDocs/xq:module/xq:comment/xq:description/text()
        	order by $moduleURI
            return
                <tr class="module">
                    <td><a href="{local:moduleURI-to-URL($moduleURI)}">{$moduleName}</a></td>
                    <td>{$moduleDescription}</td>
                    <td>{$moduleURI}</td>
                </tr>
        }</tbody>
    </table>
    )
};

(: Breadcrumbs for the page :)
declare function local:breadcrumbs($module, $function, $viewall) {
    <div>
        <a href="{$basepath}">Modules</a>
        {
        if ($viewall) then 
            ' > All'
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

    (: Derive proper module URIs :)
    let $allModules := util:registered-modules()
    let $matchingModules := if ($module) then $allModules[ends-with(., $module)] else ()
    let $moduleURIs := if ($matchingModules) then $matchingModules else $allModules

    return
        (
        (: Show verbose module documentation :)
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
        else if ($module and $matchingModules) then
            (
            local:breadcrumbs($module, $function, $viewall)
            ,
            local:render-module($moduleURIs, $function, $showlinks)
            ,
            local:return-to-main()
            )
        (: Show error, since there was no matching module :)
        else 
            (: else if ($module and not($matchingModules)) :)
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
let $existVersion := system:get-version()
return
    <book>
        <bookinfo>
            <graphic fileref="logo.jpg"/>
            <productname>Open Source Native XML Database</productname>
            <title>eXist {$existVersion} Java-Based Function Modules</title>
            <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
			<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
            <style language="text/css">
            <![CDATA[
                body {font-family: Arial, Helvetica; sans-serif;}
                .module {border:1px black solid; margin:20px; page-break-after: always; }
                .modhead {padding:5px;}
                .functions {margin-left:10px; }
                .function {border:1px black solid; padding:5px; margin:5px; page-break-before: auto; page-break-inside: avoid; }
                .name {font-weight:bold;}
                .signature {margin-left:50px; font-style: italic;}
                .description {margin-left:20px; padding:5px;}
                .f-description-para {margin-bottom: 8px;}
                .parameters {margin-left:100px;}
                .f-params {}
                .f-param1 {width:250px;}
                .f-param2 {}
                .parameter {}
                .returning {color:green;}
                .deprecated {color:red; font-weight:bold;}
                ]]>
            </style>
        </bookinfo>
        
        <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>
    
        <chapter>
            <title>A RESTful browser for eXist {$existVersion} Java-Based Function Modules</title>
            { local:main() }
        </chapter>
    </book>