(:~
    Search the built-in function library
:)
xquery version "1.0";
(: $Id: functions.xq 8359 2008-12-03 20:31:47Z wolfgang_m $ :)

declare namespace xqdoc="http://www.xqdoc.org/1.0";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace ngram="http://exist-db.org/xquery/ngram" at
    "java:org.exist.xquery.modules.ngram.NGramModule";

import module namespace setup="http://exist-db.org/xquery/docs/setup" at "docsetup.xql";

declare option exist:serialize "media-type=text/xml";
(:~
    Execute a query or list all functions in a given module.
:)
declare function xqdoc:do-query($action as xs:string, $module as xs:string?, $type as xs:string?, 
$qs as xs:string?, $print as xs:boolean) as element()* {
    if ($qs != '' or $module != '') then
        let $matches :=
            if ($action eq "Browse") then
                if( $module eq "All" ) then
                    /xqdoc:xqdoc//xqdoc:function
                else
                    /xqdoc:xqdoc[xqdoc:module/xqdoc:uri = $module]//xqdoc:function
            else if( $qs != '' ) then
                if ($type eq "name") then
                    //xqdoc:function[ngram:contains(xqdoc:name, $qs)]
                else
                    //xqdoc:function[ngram:contains(xqdoc:comment/xqdoc:description, $qs)]
            else ()
    
        let $hideIndicator   := if( $print ) then "" else "-"
    
        let $hideshowButtons := if( $print ) then () 
                                else 
                                    <div>
                                        <table class="f-hideshow-buttons">
                                            <tr>
                                                <td class="f-show-buttons">
                                                    Modules:  <a id="showAllModules" href="#">Show All</a> | <a id="hideAllModules" href="#">Hide All</a>
                                                </td>
                                                <td class="f-hide-buttons">
                                                    Function Descriptions: <a id="showAllDescriptions" href="#">Show All</a> | <a id="hideAllDescriptions" href="#">Hide All</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <br/>
                                    </div>
            
    
        let $return := 
            for $modURI in distinct-values( $matches/ancestor::xqdoc:xqdoc/xqdoc:module/xqdoc:uri )
            order by $modURI
            return 
                <div class="f-module-heading">
                    <br/>
                    <table class="f-module-heading-table">
                        <tr>
                            <td class="f-module-heading-namespace">{ $modURI }</td>
                            <td class="f-module-heading-description"><div>{  /xqdoc:xqdoc/xqdoc:module[ xqdoc:uri = $modURI ]/xqdoc:comment/xqdoc:description/text() }</div>
			    <br/>
			    <div>{if (/xqdoc:xqdoc/xqdoc:module[ xqdoc:uri = $modURI ]/xqdoc:comment/xqdoc:release-version/text() eq "trunk") then "Can only be used with current svn trunk from sourceforge at https://exist.svn.sourceforge.net/svnroot/exist/trunk/" else concat("Available from release: ", /xqdoc:xqdoc/xqdoc:module[ xqdoc:uri = $modURI ]/xqdoc:comment/xqdoc:release-version/text())}</div>
			    </td>
                            <td class="f-module-heading-hideshow">{ $hideIndicator }</td>
                        </tr>
                    </table>
                    <div class="f-module-heading-section">
                        <br/>
                        {
                            for $match in $matches[ ancestor::xqdoc:xqdoc/xqdoc:module/xqdoc:uri = $modURI ]
                            order by $match/xqdoc:name
                            return
                                <div class="f-function">
                                    <div class="f-module">
                                        {$match/ancestor::xqdoc:xqdoc/xqdoc:module/xqdoc:uri/text()}
                                    </div>
                                    <h3>{$match/xqdoc:name/text()}</h3>
                                    <div class="f-signature">{$match/xqdoc:signature/text()}</div>
                                    <div class="f-description">
                                    	{ xqdoc:print-description($match/xqdoc:comment) }
                                        { xqdoc:print-parameters($match/xqdoc:comment) }
                                        { xqdoc:print-return($match/xqdoc:comment) }
                                    </div>{
			                       if (string-length($match/xqdoc:comment/xqdoc:deprecated/text()) > 0)
			                       then
			                           <div class="f-deprecated"><div>Deprecated: {$match/xqdoc:comment/xqdoc:deprecated/text()}</div></div>
			                       else ()
                                }</div>
                        } 
                    </div>
                </div>
    
        return <div class="query-result">{ $hideshowButtons, $return }</div>
    else
        ()
};

declare function xqdoc:print-description($comment as element(xqdoc:comment)) {
    for $desc in tokenize($comment/xqdoc:description, "\n")
    return
    	<div class="f-description-para">{$desc}</div>
};

declare function xqdoc:print-parameters($comment as element(xqdoc:comment)) {
    let $params := $comment/xqdoc:param
    return
        if ($params[1] != '$a') then
            <table class="f-params">
            {
                for $param in $comment/xqdoc:param
                let $split := text:groups($param, "^(\$[^ ]+) (.*)$")
                return
                    <tr>
                        <td>{$split[2]}</td>
                        <td>{$split[3]}</td>
                    </tr>
            }
            </table>
        else
            ()
};

declare function xqdoc:print-return($comment as element(xqdoc:comment)) {
    let $returning := $comment/xqdoc:return
    return
        if (string-length($returning/text())) then
            <div class="f-return">Returns {$returning/text()}</div>
        else
            ()
};

(:~
    Return the main XML page, which will be transformed into HTML by Cocoon.
    If Javascript is enabled on the client, this function will only be called
    once. All subsequent calls to this script will be made via AJAX and we don't
    need to return the entire page.
:)
declare function xqdoc:get-page($action as xs:string, $module as xs:string?, $type as xs:string?, 
$query as xs:string?, $askPass as xs:boolean) as element() {
    <book>
        <bookinfo>
            <graphic fileref="logo.jpg"/>
    
            <productname>Open Source Native XML Database</productname>
            <title>XQuery Function Documentation</title>
            <link rel="stylesheet" type="text/css" href="styles/fundocs.css"/>
            <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
			<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
            <script type="text/javascript" src="../scripts/yui/utilities2.7.0.js"/>
            <script type="text/javascript" src="scripts/fundocs.js"/>
        </bookinfo>
        
        <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>
    
        <chapter>
            <title>XQuery Function Documentation</title>
            {
                if ($askPass) then
                    <form id="f-pass" name="f-pass" action="functions.xql" method="POST">
                        <para>The function documentation needs to be generated first,
                        which requires administrator rights. Please enter the
                        password for the admin user below:</para>
                        <input type="password" name="pass" value=""/>
                        <input type="hidden" name="generate" value="true"/>
                        <button type="submit">Generate</button>
                    </form>
                else (
                    <div id="f-search">
                        <form name="f-query" action="functions.xql" method="POST">
                            <table>
                                <tr>
                                    <td>
                                        <label for="q">Search:</label>
                                        <input name="q" type="text" value="{$query}"/>
                                        <label for="type">in</label>
                                        <select name="type">
                                            <option value="name">Function Name</option>
                                            <option value="desc">Description</option>
                                        </select>
                                    </td>
                                    <td class="f-btn">
                                        <input id="f-btn-search" type="submit" 
                                            name="action" value="Search"/>
                                    </td>
                                    <td>
                                        <input id="f-btn-print" type="submit" 
                                            name="action" value="Print"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <label for="module">Or display <b>all</b> in:</label>
                                        <select name="module">
                                            <option value="All">All</option>
                                        {
                                            for $mod in collection("/db")//xqdoc:module
                                            let $uri := $mod/xqdoc:uri/text()
                                            order by $uri
                                            return
                                                <option value="{$uri}">
                                                { if ($uri eq $module) then attribute selected { "true" } else () }
                                                { $uri }
                                                </option>
                                        }
                                        </select>
                                    </td>
                                    <td class="f-btn">
                                        <input id="f-btn-browse" type="submit" name="action" value="Browse"/> 
                                    </td>
                                    <td><img id="f-loading" src="../resources/loading.gif"/></td>
                                </tr>
                            </table>
                            <input type="hidden" name="prev" value="{$action}"/>
                        </form>
                        <p class="f-reload"><a href="?action=reload">Reload documentation</a>
                            (click here if you enabled/disabled additional modules)</p>
                        <p class="f-info">(<b>eXist version: {util:system-property("product-version")}, 
                        build: {util:system-property("product-build")},
                        functions: {count(//xqdoc:function)}</b>). Modules have to be enabled 
                        in conf.xml to appear here. 
                        </p>
                        <div id="f-result">
                            { if ($query or $module) then xqdoc:do-query($action, $module, $type, $query, false()) else () }
                        </div>
                    </div>
                )
            }
        </chapter>
    </book>
};

declare function xqdoc:print-page($module as xs:string?, $type as xs:string?,
$query as xs:string?) as element() {
    let $prevAction := request:get-parameter("prev", "Browse")
    return
        <html>
            <head>
                <title>XQuery Function Documentation</title>
                <link rel="stylesheet" type="text/css" href="styles/fundocs.css"/>
                <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
				<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
            </head>
            <body class="f-print">
                <h1>XQuery Function Documentation</h1>
                {
                    if ($prevAction = "Search") then
                        <p>Query: "{$query}" in {$type}.</p>
                    else ()
                }
    { xqdoc:do-query($prevAction, $module, $type, $query, true() ) }
            </body>
        </html>
};

declare function xqdoc:debug-parameters() {
    for $param in request:get-parameter-names()
    return
        util:log("DEBUG", ($param , " = ", request:get-parameter($param, ())))
};

(:
    The mainline of the script. First checks if the documentation has
    already been extracted. If not, ask for the admin password and
    call xqdoc:setup() to generate the documentation. 
:)
let $action := request:get-parameter("action", "Search")
let $generate := request:get-parameter("generate", ())
let $askPass :=
    if (empty(//xqdoc:module) or $generate) then
        let $adminPass := request:get-parameter("pass", ())
        return
            if ($generate) then
                let $setup-result := util:catch("java.lang.Exception",
                                     let $result := setup:setup($adminPass) return false(),
                                     true())
                return $setup-result
            else
                true()
    else
        $action eq 'reload'
let $log := xqdoc:debug-parameters()
let $query := request:get-parameter("q", ())
let $type := request:get-parameter("type", "name")
let $mode := request:get-parameter("mode", ())
let $module := request:get-parameter("module", ())
return
    if ($mode = "ajax") then
        xqdoc:do-query($action, $module, $type, $query, false())
    else if ($action eq "Print") then
        xqdoc:print-page($module, $type, $query)
    else
        xqdoc:get-page($action, $module, $type, $query, $askPass)
