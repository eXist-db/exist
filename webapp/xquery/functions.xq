(:~
    Search the built-in function library
:)
xquery version "1.0";
(: $Id$ :)

declare namespace xqdoc="http://www.xqdoc.org/1.0";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace ngram="http://exist-db.org/xquery/ngram" at
    "java:org.exist.xquery.modules.ngram.NGramModule";

(:~
    Collection configuration for the function docs. We use an ngram
    index for fast substring searches.
:)
declare variable $xqdoc:config :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xqdoc="http://www.xqdoc.org/1.0">
            <fulltext default="none" attributes="no">
            </fulltext>
            <ngram qname="xqdoc:name"/>
            <ngram qname="xqdoc:description"/>
        </index>
    </collection>;

(:~
    Before the function document can be searched, it needs to be extracted
    from the Java files. We use util:extract-docs() to generate one XQDoc
    document for each module and store it into /db/xqdocs.
:)
declare function xqdoc:setup($adminPass as xs:string) {
    if (empty(//xqdoc:module)) then
        let $setuser := xdb:login("/db", "admin", $adminPass)
        let $confCol := (
            xdb:create-collection("/db/system/config", "db"),
            xdb:create-collection("/db/system/config/db", "xqdocs")
        )
        let $confStored := xdb:store("/db/system/config/db/xqdocs", "collection.xconf", $xqdoc:config)
        let $output := (
            xdb:create-collection("/db", "xqdocs"),
            xdb:chmod-collection("/db/xqdocs", 508)
        )
        for $moduleURI in util:registered-modules()
        let $moduleDocs := util:extract-docs($moduleURI)
        let $docName := concat(util:md5($moduleURI), ".xml")
        return (
            xdb:store("/db/xqdocs", $docName, $moduleDocs, "text/xml"),
            xdb:chmod-resource("/db/xqdocs", $docName, 508)
        )
    else
        ()
};

(:~
    Execute a query or list all functions in a given module.
:)
declare function xqdoc:do-query($module as xs:string?, $type as xs:string?, 
$qs as xs:string?) as element()* {
    if ($qs != '' or $module != '') then
        let $matches :=
            if ($module) then
                /xqdoc:xqdoc[xqdoc:module/xqdoc:uri = $module]//xqdoc:function
            else if ($type eq 'name') then
                //xqdoc:function[ngram:contains(xqdoc:name, $qs)]
            else
                //xqdoc:function[ngram:contains(xqdoc:comment/xqdoc:description, $qs)]
        for $match in $matches
        order by $match/xqdoc:name
        return
            <div class="f-function">
                <div class="f-module">
                    {$match/ancestor::xqdoc:xqdoc/xqdoc:module/xqdoc:uri/text()}
                </div>
                <h3>{$match/xqdoc:name/text()}</h3>
                <div class="f-signature">{$match/xqdoc:signature/text()}</div>
                <div class="f-description">{$match/xqdoc:comment/xqdoc:description/text()}</div>
            </div>
    else
        ()
};

(:~
    Return the main XML page, which will be transformed into HTML by Cocoon.
    If Javascript is enabled on the client, this function will only be called
    once. All subsequent calls to this script will be made via AJAX and we don't
    need to return the entire page.
:)
declare function xqdoc:get-page($module as xs:string?, $type as xs:string?, 
$query as xs:string?, $askPass as xs:boolean) as element() {
    <book>
        <bookinfo>
            <graphic fileref="logo.jpg"/>
    
            <productname>Open Source Native XML Database</productname>
            <title>XQuery Function Documentation</title>
            <link rel="stylesheet" type="text/css" href="styles/fundocs.css"/>
            <script type="text/javascript" src="../scripts/yui/utilities.js"/>
            <script type="text/javascript" src="scripts/fundocs.js"/>
        </bookinfo>
        
        <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>
    
        <chapter>
    		<title>XQuery Function Documentation</title>
            {
                if ($askPass) then
                    <form id="f-pass" name="f-pass" action="functions.xq" method="POST">
                        <para>The function documentation needs to be generated first,
                        which requires administrator rights. Please enter the
                        password for the admin user below:</para>
                        <input type="password" name="pass" value=""/>
                        <input type="hidden" name="generate" value="true"/>
                        <button type="submit">Generate</button>
                    </form>
                else (
                    <div id="f-search">
                        <form name="f-query" action="functions.xq" method="POST">
                            <img id="f-loading" src="../resources/loading.gif"/>
                            <label for="q">Search:</label>
                            <input name="q" type="text" value="{$query}"/>
                            <label for="type">in</label>
                            <select name="type">
                                <option value="name">Function Name</option>
                                <option value="desc">Description</option>
                            </select>
                            <button type="submit">Find</button><br/>
                        </form>
                        <form name="f-browse" action="functions.xq" method="POST">
                            <label for="module">Or display <b>all</b> functions in module:</label>
                            <select name="module">
                            {
                                for $module in //xqdoc:module
                                return
                                    <option value="{$module/xqdoc:uri}">
                                    {$module/xqdoc:uri/text()}
                                    </option>
                            }
                            </select>
                            <button type="submit">Browse</button>
                        </form>
                        <div id="f-result">
                        { if ($query or $module) then xqdoc:do-query($module, $type, $query) else () }
                        </div>
                    </div>,
                    
                    <para>(<b>eXist version: {util:system-property("product-version")}, build: {util:system-property("product-build")}</b>).
                    Modules have to be enabled in conf.xml to appear here. 
                    </para>
                )
            }
        </chapter>
    </book>
};

(:
    The mainline of the script. First checks if the documentation has
    already been extracted. If not, ask for the admin password and
    call xqdoc:setup() to generate the documentation. 
:)
let $askPass :=
    if (empty(//xqdoc:module)) then
        let $adminPass := request:get-parameter("pass", ())
        let $generate := request:get-parameter("generate", ())
        return
            if ($generate) then
                let $dummy := xqdoc:setup($adminPass)
                return false()
            else
                true()
    else
        false()
let $query := request:get-parameter("q", ())
let $type := request:get-parameter("type", "name")
let $mode := request:get-parameter("mode", ())
let $module := request:get-parameter("module", ())
return
    if ($mode = 'ajax') then
        xqdoc:do-query($module, $type, $query)
    else
        xqdoc:get-page($module, $type, $query, $askPass)