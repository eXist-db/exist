xquery version "1.0";
(: $Id$ :)

declare namespace sandbox="http://exist-db.org/xquery/sandbox";
declare namespace system="http://exist-db.org/xquery/system";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=xhtml indent=no";

(:~ Points to the location of the xml-highlight.xsl stylesheet stored in the database :)
declare variable $sandbox:XML_HIGHLIGHT_STYLE { "/db/sandbox/xml-highlight.xsl" };

(:~ The required stylesheets were not found in the db. Try to import them. :)
declare function sandbox:import-stylesheets() as xs:string* {
    let $home := system:get-exist-home()
    let $pathSep := util:system-property("file.separator")
    let $dir :=
			if (doc-available(concat("file:///", system:get-module-load-path(), "/stylesheets/xml-highlight.xsl")))
			then
				concat(system:get-module-load-path(), $pathSep, "stylesheets")
        	else if(ends-with($home, "WEB-INF")) then
        	    concat(substring-before($home, "WEB-INF"), "sandbox", $pathSep, "stylesheets")
        	else
               concat($home, $pathSep, "webapp", $pathSep, "sandbox", $pathSep, "stylesheets")
    let $col := xdb:create-collection("/db", "sandbox")
    return
        concat("Imported: ", xdb:store-files-from-pattern($col, $dir, "*.xsl", "text/xml"))
};

(:~ Check for stylesheets required for the application. Try to import them if they could not be found. :)
declare function sandbox:check-paths() as xs:string* {
    if (not(doc-available($sandbox:XML_HIGHLIGHT_STYLE))) then
        let $dummy := sandbox:import-stylesheets()
        return
            if (not(doc-available($sandbox:XML_HIGHLIGHT_STYLE))) then
                concat($sandbox:XML_HIGHLIGHT_STYLE, " not found! Please store this file into ",
                    "the database collection or the application will not work properly.")
            else ()
    else ()
};

declare function sandbox:init-slots() as element()+ {
    for $i in 1 to 10 return
        <li><span class="num">[{$i}]</span> <a href="#" id="slot{$i}" onclick="switchSlot(this)">--- empty ---</a></li>
};

(:~ Export the current query results into a new document in the database. :)
declare function sandbox:export($docName as xs:string) as element() {
    let $collection := request:get-parameter("collection", ())
    let $wrapper0 := request:get-parameter("wrapper", ())
    let $wrapper := if ($wrapper0) then $wrapper0 else "exist:result"
    let $cached := session:get-attribute("cached")
    return
        if (empty($cached)) then
            <error>No query results to export!</error>
        else
            let $newDoc :=
                element { $wrapper } { $cached }
            return
                util:catch("java.lang.Exception",
                    (let $dummy := xdb:store($collection, $docName, $newDoc, "text/xml") return <ajax-response/>),
                    <error>{$util:exception-message}</error>
                )
};

(:~ Append the given query to the query examples file or update an existing old query. :) 
declare function sandbox:save-query($query as xs:string, $description as xs:string) as element() {
    let $root0 := /example-queries
    let $root :=
        if ($root0) then
            $root0
        else
            xdb:store("/db", "examples.xml", <example-queries/>, "text/xml")
    let $existing := $root/query[description = $description][1]
    let $newEntry := 
            <query>
                <description>{$description}</description>
                <code>{$query}</code>
            </query>
    return (
        if ($existing) then
            update replace $existing with $newEntry
        else
            update insert $newEntry into $root,
        <ajax-response/>
    )
};

(:~ Check if the passed query string would correctly compile. :)
declare function sandbox:check-query($query as xs:string) as element() {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    <ajax-response>
        { util:compile($query) }
    </ajax-response>
};

(:~ Retrieve a single query result. :)
declare function sandbox:retrieve($num as xs:integer) as element() {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no indent=no"),
    let $cached := session:get-attribute("cached")
    let $item :=
        <item num="{$num}">
            {$cached[$num]}
        </item>
    return
        transform:transform($item, doc($sandbox:XML_HIGHLIGHT_STYLE), ())
};

(:~ Execute a query :)
declare function sandbox:exec-query($qu as xs:string) as element() {
    let $startTime := util:system-time()
    let $results := util:eval($qu)
    let $elapsed := 
		string(seconds-from-duration(util:system-time() - $startTime))
	let $elapsedStr := concat(substring-before($elapsed, '.'), '.',
			substring(substring-after($elapsed, '.'), 1, 3))
    return (
        session:set-attribute("cached", $results),
        <result hits="{count($results)}" elapsed="{$elapsedStr}"/>
    )
};

declare function sandbox:query($qu as xs:string) as element() {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    util:catch("org.exist.xquery.XPathException",
         sandbox:exec-query($qu),
         <error>{$util:exception-message}</error>
    )
 };
 
declare function sandbox:display-page() as element() {
    util:declare-option("exist:serialize", "media-type=text/html method=xhtml"),
    <html>
        <head>
            <title>XQuery Sandbox</title>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
            <link type="text/css" href="styles/sandbox.css" rel="stylesheet"/>
            <script language="Javascript" type="text/javascript" src="scripts/prototype.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/scriptaculous.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/behaviour.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/ajax.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/sandbox.js"/>
        </head>
        <body>
            <div id="header">
                <ul id="menu">
                    <li><a href="../index.xml">Home</a></li>
                    <li><a href="../index.xml#download">Download</a></li>
                    <li><a href="http://atomic.exist-db.org">Wiki</a></li>
                    <li><a href="../examples.xml">Demo</a></li>
                </ul>
                <h1>XQuery Sandbox</h1>
            </div>
            
            <div id="content">
                <!--div id="user">Logged in as guest: <a href="#" id="change-user">Change</a></div-->
                <div id="errors">{sandbox:check-paths()}</div>
                <form name="main">
                    <div id="top-panel">
                        <div id="slots-panel">
                            <h2>Slots</h2>
                            <ul id="slots">
                                {sandbox:init-slots()}
                            </ul>
                        </div>
                        <div id="right-panel">
                            <div id="query-panel">
                                <a href="#" id="maximize">Maximize</a>
                                <p id="queries">
                                    <label for="saved">Paste saved query</label>
                                    <select id="saved" name="saved">
                                        <option></option>
                                    </select>
                                </p>
                                <textarea id="query" name="qu" />
                                <fieldset>
                                    <div id="buttons">
                                        <button type="button" id="submit">Send</button>
                                        <button type="button" id="clear">Clear</button>
                                        <button type="button" id="check">Check</button>
                                    </div>
                                    <label for="howmany">Display</label>
                                    <select id="howmany">
                                        <option>20</option>
                                        <option>50</option>
                                        <option>100</option>
                                    </select>
                                    <a href="#" id="show-options">More Options</a>
                                </fieldset>
                            </div>
                        </div>
                            <div id="save-panel">
                                <div>
                                    <h2>Save to examples.xml:</h2>
                                    <label for="description">Description</label>
                                    <input type="text" id="description"/>
                                    <button type="button" id="save">Save</button>
                                    
                                    <h2>Export results to new document:</h2>
                                    <label for="docname">Document path</label>
                                    <a href="#" id="export-resource">Click to select</a>
                                    <label for="wrapper">Wrapper element</label>
                                    <input type="text" id="wrapper"/>
                                    <button type="button" id="export">Export</button>
                                </div>
                            </div>
                        </div>
                </form>
                <div id="query-output">
                    <div id="query-result"/>
                    <div id="navbar">
                        <a id="previous" href="#">&lt;&lt;</a>
                        <a id="next" href="#">&gt;&gt;</a>
                        <div id="current">Query Result</div>
                    </div>
                    <div id="output"/>
                </div>
            </div>
        </body>
    </html>
};

session:create(),
let $pos := xs:integer(request:get-parameter("num", ()))
let $save := request:get-parameter("save", ())
let $query := request:get-parameter("qu", ())
let $check := request:get-parameter("check", ())
let $export := request:get-parameter("export", ())
return
    if ($save) then
        sandbox:save-query($query, $save)
    else if ($export) then
        sandbox:export($export)
    else if ($check) then
        sandbox:check-query($check)
    else if ($query) then
        sandbox:query($query)
    else if ($pos) then
        sandbox:retrieve($pos)
    else
        sandbox:display-page()
