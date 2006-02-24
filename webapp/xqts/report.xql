xquery version "1.0";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare namespace xqts="http://exist-db.org/xquery/xqts";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";

declare variable $xqts:XML_HIGHLIGHT_STYLE { "/db/XQTS/xml-highlight.xsl" };
declare variable $xqts:CONFIG { xqts:initialize() };
declare variable $xqts:XQTS_HOME { $xqts:CONFIG/basedir/text() };

declare function xqts:initialize() as element() {
    let $collection := xdb:create-collection("/db", "XQTS")
    let $config := doc("/db/XQTS")/config
    return
        if ($config) then
            $config
        else
            let $home := util:system-property("exist.home")
            let $path := concat($home, "/tools/XQTS")
            let $stored := xdb:store-files-from-pattern("/db/XQTS", $path, "*.xml", "text/xml")
            return
                doc("/db/XQTS/config.xml")/config
};

(:~ The required stylesheets were not found in the db. Try to import them. :)
declare function xqts:import-stylesheet() as xs:string* {
    let $home := util:system-property("exist.home")
    let $pathSep := util:system-property("file.separator")
    let $dir :=
        	if(ends-with($home, "WEB-INF")) then
        	    concat(substring-before($home, "WEB-INF"), "xqts", $pathSep, "stylesheets")
        	else
               concat($home, $pathSep, "webapp", $pathSep, "xqts", $pathSep, "stylesheets")
    let $col := xdb:create-collection("/db", "XQTS")
    return
        concat("Imported: ", xdb:store-files-from-pattern($col, $dir, "*.xsl", "text/xml"))
};

(:~ Check for stylesheets required for the application. Try to import them if they could not be found. :)
declare function xqts:check-paths() as xs:string* {
    if (not(doc-available($xqts:XML_HIGHLIGHT_STYLE))) then
        let $dummy := xqts:import-stylesheet()
        return
            if (not(doc-available($xqts:XML_HIGHLIGHT_STYLE))) then
                concat($xqts:XML_HIGHLIGHT_STYLE, " not found! Please store this file into ",
                    "the database collection or the application will not work properly.")
            else ()
    else ()
};

declare function xqts:path-to-uri($path as xs:string) as xs:string {
        if (starts-with($path, "/")) then
            concat("file://", $path)
        else
            concat("file:///", $path)
};

declare function xqts:get-query($case as element(catalog:test-case)) {
   let $query-name := $case//catalog:query/@name
   let $path := concat( xqts:path-to-uri($xqts:XQTS_HOME), "Queries/XQuery/", 
       $case/@FilePath, $query-name, ".xq" )
   let $xq-string := util:file-read($path)
   let $tokenized := tokenize($xq-string, "\n")
   for $token in $tokenized return
       ($token, <br/>)
};

declare function xqts:display-page() as element() {
    util:declare-option("exist:serialize", "media-type=text/html method=xhtml"),
    <html>
        <head>
            <title>XQuery Test Suite - Test Reports</title>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
            <link type="text/css" href="styles/report.css" rel="stylesheet"/>
            <script language="Javascript" type="text/javascript" src="scripts/prototype.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/behaviour.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/YAHOO.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/treeview.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/report.js"/>
        </head>
        <body>
            <div id="header">
                <ul id="menu">
                    <li><a href="../index.xml">Home</a></li>
                    <li><a href="../index.xml#download">Download</a></li>
                    <li><a href="http://wiki.exist-db.org">Wiki</a></li>
                    <li><a href="../examples.xml">Demo</a></li>
                </ul>
                <h1>XQuery Test Suite - Test Reports</h1>
            </div>
            
            <div id="content">
                <div id="messages">{xqts:check-paths()}</div>
                <div id="navtree"></div>
                <div id="panel-right">
                    <div id="testcases"></div>
                    <div id="details">
                        <div id="tabs">
                            <ul>
                                <li><a href="#" id="summary-link"><span>Summary</span></a></li>
                                <li><a href="#" id="query-link"><span>Query</span></a></li>
                                <li><a href="#" id="testresult-link"><span>Test Result</span></a></li>
                                <li><a href="#" id="testdef-link"><span>Test Definition</span></a></li>
                            </ul>
                        </div>
                        <div id="details-content"></div>
                    </div>
                </div>
            </div>
        </body>
    </html>
};

declare function xqts:print-tests($collection as xs:string, $name as xs:string) {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    let $group := /catalog:test-suite//catalog:test-group[@name = $name]
    let $info := $group/catalog:GroupInfo
    return
        <div id="group-details">
            <div class="group-heading">
                <button type="button" onclick="runTest('{$name}')">Run Test</button>
                <h1>{$info/catalog:title/text()}</h1>
                <h2>{$info/catalog:description/text()}</h2>
            </div>
            <table class="tests">
            {
                for $case at $pos in xcollection($collection)//test-case
                let $result := $case/@result
                let $name := string($case/@name)
                return
                    <tr class="{if ($pos mod 2 = 0) then 'even' else ''}">
                        <td><a href="#" onclick="details('{$name}')">{$name}</a></td>
                        <td class="{$result}">{string($result)}</td>
                    </tr>
            }
            </table>
        </div>
};

declare function xqts:failure-details($result as element()) {
    <div id="query-result" style="display: none">
    {
        if ($result/exception) then
            $result/exception/text()
        else
            $result/result/text()
    }
    </div>,
    <div id="expected" style="display: none">
    { $result/expected/text() }
    </div>
};
        
declare function xqts:details($testName as xs:string) {
    let $case := /catalog:test-suite//catalog:test-case[@name = $testName]
    let $result := //test-case[@name = $testName]
    return
        <div id="test-details">
            <table id="summary">
                <tr>
                    <td class="label">Name:</td>
                    <td>{string($case/@name)}</td>
                </tr>
                <tr>
                    <td class="label">Description:</td>
                    <td>{$case/catalog:description/text()}</td>
                </tr>
                {
                    for $spec in $case/catalog:spec-citation
                    return
                        <tr>
                            <td class="label">Specification:</td>
                            <td>
                            {
                                let $citation := /catalog:test-suite//catalog:citation-spec[@name = $spec/@spec]
                                let $url := $citation/catalog:spec-URI/text()
                                return 
                                    <a href="{$url}#{$spec/@section-pointer}">
                                    {
                                        concat($spec/@spec, ': ', $spec/@section-title, ' (', $spec/@section-number, ')')
                                    }
                                    </a>
                            }
                            </td>
                        </tr>
                }
                <tr>
                    <td class="label">Result:</td>
                    <td class="{$result/@result}">
                        {string($result/@result)}
                    </td>
                </tr>
            </table>
            <div id="testdef" style="display: none">
            {
                transform:transform($case, doc($xqts:XML_HIGHLIGHT_STYLE), ())
            }
            </div>
            <div id="query" style="display: none">
                {xqts:get-query($case)}
            </div>
            <div id="testresult" style="display: none">
            {
                transform:transform($result, doc($xqts:XML_HIGHLIGHT_STYLE), ())
            }
            </div>
        </div>
};

declare function xqts:print-group($group as element(), $path as xs:string) {
    let $path := concat($path, "/", $group/@name)
    let $collection := concat("/db/XQTS/test-results", $path)
    let $result := collection($collection)//test-result
    let $failed := count($result/test-case[@result = 'fail'])
    let $passed := count($result/test-case[@result = 'pass'])
    return
    <group
        collection="{$collection}"
        name="{$group/@name}" title="{$group/catalog:GroupInfo/catalog:title}"
        passed="{$passed}"
        failed="{$failed}">
    {
        for $child in $group/catalog:test-group
        return
            xqts:print-group($child, $path)
    }
    </group>
};

declare function xqts:tree() {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    <groups>
    {
        for $group in /catalog:test-suite/catalog:test-group
        return
            xqts:print-group($group, "")
    }
    </groups>
};

let $tree := request:request-parameter("tree", ())
let $group := request:request-parameter("group", ())
let $name := request:request-parameter("name", ())
let $case := request:request-parameter("case", ())
return
    if ($case) then
        xqts:details($case)
    else if ($group) then
        xqts:print-tests($group, $name)
    else if ($tree) then
        xqts:tree()
    else
        xqts:display-page()