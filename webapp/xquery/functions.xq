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

declare variable $xqdoc:config :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xqdoc="http://www.xqdoc.org/1.0">
            <fulltext default="none" attributes="no">
                <create qname="xqdoc:description"/>
            </fulltext>
            <ngram qname="xqdoc:name"/>
        </index>
    </collection>;

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

declare function xqdoc:do-query($type as xs:string, $qs as xs:string) as element()* {
    if ($qs != '') then
        let $matches :=
            if ($type eq 'name') then
                //xqdoc:function[ngram:contains(xqdoc:name, $qs)]
            else
                //xqdoc:function[xqdoc:comment/xqdoc:description &= $qs]
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

declare function xqdoc:get-page($type as xs:string?, $query as xs:string?, $askPass as xs:boolean) 
as element() {
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
                        <form id="f-query" name="f-query" action="fundocs.xql" method="POST">
                            <img id="f-loading" src="../resources/loading.gif"/>
                            <label for="q">Search:</label>
                            <input name="q" type="text" value="{$query}"/>
                            <label for="type">in</label>
                            <select name="type">
                                <option value="name">Function Name</option>
                                <option value="desc">Description</option>
                            </select>
                            <button type="submit">Find</button>
                        </form>
                        
                        <div id="f-result">
                        { if ($query) then xqdoc:do-query($type, $query) else () }
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
return
    if ($mode = 'ajax') then
        xqdoc:do-query($type, $query)
    else
        xqdoc:get-page($type, $query, $askPass)