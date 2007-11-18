xquery version "1.0";
(: $Id$ :)
(: prints a list of all built-in functions :)

declare namespace util="http://exist-db.org/xquery/util";
declare namespace xqdoc="http://www.xqdoc.org/1.0";

import module namespace ngram="http://exist-db.org/xquery/ngram" at
    "java:org.exist.xquery.modules.ngram.NGramModule";
    
declare function xqdoc:do-query($qs as xs:string) as element()+ {
    if ($qs != '') then
        let $matches := //xqdoc:function[ngram:contains(xqdoc:name, $qs)] 
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

declare function xqdoc:get-page($query as xs:string?) as element() {
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

            <div id="f-search">
                <form name="f-query" action="fundocs.xql" method="POST">
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
                { if ($query) then xqdoc:do-query($query) else () }
                </div>
            </div>
            
            <para>(<b>eXist version: {util:system-property("product-version")}, build: {util:system-property("product-build")}</b>).
            Modules have to be enabled in conf.xml to appear here. 
            </para>
            
        </chapter>
    </book>
};

let $query := request:get-parameter("q", ())
let $mode := request:get-parameter("mode", ())
return
    if ($mode = 'ajax') then
        xqdoc:do-query($query)
    else
        xqdoc:get-page($query)