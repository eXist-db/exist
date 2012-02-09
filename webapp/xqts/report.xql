xquery version "1.0";
(: $Id$ :)

import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace system="http://exist-db.org/xquery/system";

declare namespace xqts="http://exist-db.org/xquery/xqts";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";

declare variable $xqts:XML_HIGHLIGHT_STYLE := "/db/XQTS/xml-highlight.xsl";
declare variable $xqts:CONFIG := xqts:initialize();
declare variable $xqts:XQTS_HOME := $xqts:CONFIG/basedir/text();

declare option exist:output-size-limit "-1";

declare function xqts:initialize() as element()? {
    let $collection := xdb:create-collection("/db", "XQTS")
    let $config := if (doc-available("/db/XQTS/config.xml")) then doc("/db/XQTS/config.xml")/config else () 
    return
        if (exists($config)) then
            $config
        else
            let $home := system:get-exist-home()
            let $pathSep := util:system-property("file.separator")
    		let $dir :=
				if (doc-available(concat("file:///", system:get-module-load-path(), "/config.xml")))
				then
					system:get-module-load-path()
        		else if(ends-with($home, "WEB-INF")) then
        	    	concat(substring-before($home, "WEB-INF"), "xqts")
        		else
               		concat($home, $pathSep, "webapp", $pathSep, "xqts")
            let $stored := xdb:store-files-from-pattern("/db/XQTS", $dir, "*.xml", "text/xml")
            return
                if (doc-available("/db/XQTS/config.xml")) then doc("/db/XQTS/config.xml")/config else ()               
};

declare function xqts:read($filePath as xs:string) {
	system:as-user("admin", (), file:read($filePath, "UTF-8"))
};

(:~ The required stylesheets were not found in the db. Try to import them. :)
declare function xqts:import-stylesheet() as xs:string* {
    let $home := system:get-exist-home()
    let $pathSep := util:system-property("file.separator")
    let $dir :=
			if (doc-available(concat("file:///", system:get-module-load-path(), "config.xml")))
			then
				concat(system:get-module-load-path())
        	else if(ends-with($home, "WEB-INF")) then
        	    concat(substring-before($home, "WEB-INF"), "xqts")
        	else
               concat($home, $pathSep, "webapp", $pathSep, "xqts")
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
   let $path := concat(xqts:path-to-uri($xqts:XQTS_HOME), "Queries/XQuery/", 
       $case/@FilePath, $query-name, ".xq")
   let $xq-string := xqts:read($path)
   return $xq-string
   (: let $tokenized := tokenize($xq-string, "\n")
   for $token in $tokenized return
       ($token, <br/>) :)
};

declare function xqts:display-page() as element() {
    util:declare-option("exist:serialize", "media-type=text/html method=xhtml"),
    <html>
        <head>
            <title>{string(/catalog:test-suite/catalog:test-suite-info/catalog:title)} - Test Reports</title>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
            <link type="text/css" href="styles/report.css" rel="stylesheet"/>
			<link type="text/css" href="styles/SyntaxHighlighter.css" rel="stylesheet"/>
			<script language="Javascript" type="text/javascript" src="scripts/shCore.js"/>
			<script language="Javascript" type="text/javascript" src="scripts/shBrushXml.js"/>
			<script language="Javascript" type="text/javascript" src="scripts/shBrushXQuery.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/yahoo.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/treeview.js"/>
			<script language="Javascript" type="text/javascript" src="scripts/dom.js"/>
			<script language="Javascript" type="text/javascript" src="scripts/event.js"/>
			<script language="Javascript" type="text/javascript" src="scripts/connection.js"/>
			<script language="Javascript" type="text/javascript" src="scripts/container.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/report.js"/>
        </head>
        <body>            
            <div id="header">
                <ul id="menu">
                    <li><a href="../index.xml">Home</a></li>
                    <li><a href="../download.xml">Download</a></li>
                    <li><a href="http://atomic.exist-db.org">Wiki</a></li>
                    <li><a href="../examples.xql">DemoApps</a></li>
                    <li><a href="collectresults.xql">Results</a></li>
                </ul>
                <h1>{string(/catalog:test-suite/catalog:test-suite-info/catalog:title)} - Test Reports</h1>
            </div>
            
            <div id="content">
                <div id="messages"></div>
                <div id="panel-left">
                    <h1>Test Groups</h1>
                    <div id="navtree"></div>
                </div>
                <div id="panel-right">
                    <div id="testcases">
                        <div id="group-details">                            
                            <div class="group-heading">
                                <h1>Test Cases</h1>
                                <h2>Select a test group to view its test cases</h2>
                            </div>
                            <div id="tests"></div>
                        </div>
                    </div>
                </div>
                <div id="details">
                    <div id="tabs">
                        <ul>
                            <li><a href="#" id="tab-summary" class="tab"><span>Summary</span></a></li>
                            <li><a href="#" id="tab-query" class="tab"><span>Query</span></a></li>
                            <li><a href="#" id="tab-testresult" class="tab"><span>Test Result</span></a></li>
                            <li><a href="#" id="tab-testdef" class="tab"><span>Test Definition</span></a></li>
                        </ul>
                    </div>
                    <div id="details-content"></div>
                </div>
            </div>
            <div id="installation">
                <div class="hd">XQTS Data Not Found</div>
                <div class="bd">
                    <p>In order to run the XQuery Test Suite (XQTS), you need to download and install the
                    XQTS data from the W3C web site. To install and run the XQTS, read the following
                    instructions:</p>
                    
                    <ul>
                        <li>Get latest XQTS test suite ZIP file from the 
                            <a href="http://www.w3.org/XML/Query/test-suite/" target="_new">W3C site</a>. 
                            <b>Please note</b>: eXist 1.0 will only work with XQTS version 0.9.0. It won't run 
                            with later versions. eXist 1.1 can run all versions!</li>
                        <li>Extract the file to a location remembered as "XQTS_HOME" on your local drive (it is not needed to create an 
                            environment variable).</li>
                        <li>Modify <span class="filename">EXIST_HOME/webapp/xqts/config.xml</span>, set <b>&lt;basedir&gt;XQTS_HOME&lt;/basedir&gt;</b></li>                        
                        <li>Modify <span class="filename">EXIST_HOME/conf.xml</span>, set <b>suppress-whitespace="none"</b> if required</li>
                        <li>Modify <span class="filename">EXIST_HOME/conf.xml</span>, set <b>disable-deprecated-functions="yes"</b> if required</li>
                        <li>Modify <span class="filename">EXIST_HOME/conf.xml</span>, set <b>raise-error-on-failed-retrieval="yes"</b> if required</li>
                        <li>Start eXist as full server in EXIST_HOME : <span class="filename">bin/startup.sh</span> or <span class="filename">bin\startup.bat</span></li>
                        <li>Start data upload : <span class="filename">build.[sh|bat] -f EXIST_HOME/webapp/xqts/build.xml</span></li>
                        <li>Reload this page!</li>
                    </ul>
                </div>
                <div class="ft"></div>
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
                <button type="button" onclick="runTest('{$collection}', '{$name}')">Run Test</button>
                <form>                    
                {
                    if ($xqts:CONFIG/mode/text() = "memory") then      
                        <select name="processing">                                          
                          <option value="true" selected="">In memory nodes</option>
                          <option value="false">Persistent nodes</option>
                        </select>                          
                    else
                        <select name="processing">                  
                          <option value="true">In memory nodes</option>
                          <option value="false" selected="">Persistent nodes</option>
                        </select>
                    }
                </form>
                <h1>{$info/catalog:title/text()}</h1>
                <h2>{$info/catalog:description/text()}</h2>
            </div>
			<div id="tests">
				<table>
				{				    
		            if (xmldb:collection-available($collection)) then				    
    					for $case at $pos in xmldb:xcollection($collection)//test-case
    					let $result := $case/@result
    					let $name := string($case/@name)
    					return
    						<tr class="{if ($pos mod 2 = 0) then 'even' else ''}">
    							<td><a href="#" onclick="details('{$name}')">{$name}</a></td>
    							<td class="{$result}">{string($result)}</td>
    						</tr>
    				else ()
				}
				</table>
			</div>
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
                                    <a href="{$url}#{$spec/@section-pointer}" target="_blank">
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
                    <td class="{$result/@result[1]}">
                        {string($result[1]/@result)}
                    </td>
                </tr>
            </table>
            <div id="testdef" style="display: none">
                	<textarea name="code" class="xml">{ $case }</textarea>
            </div>
            <div id="query" style="display: none">
				<textarea name="code" class="xquery">
					{xqts:get-query($case)}
				</textarea>
            </div>
            <div id="testresult" style="display: none">
				<textarea name="code" class="xml">
				{ $result }
				</textarea>
            </div>
        </div>
};

declare function xqts:print-group($group as element(), $path as xs:string) {
    let $path := concat($path, "/", $group/@name)
    let $collection := concat("/db/XQTS/test-results", $path)
    let $result := if (xmldb:collection-available($collection)) then collection($collection)//test-result else ()
    let $failed := count($result/test-case[@result = 'fail'])
    let $passed := count($result/test-case[@result = 'pass'])
    let $errors := count($result/error)
    let $total  := $failed + $passed + $errors
    return
    <group
        collection="{$collection}"
        name="{$group/@name}" title="{$group/catalog:GroupInfo/catalog:title}"
        passed="{$passed}"
        failed="{$failed}"
        errors="{$errors}"
        total="{$total}">
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

let $tree := request:get-parameter("tree", ())
let $group := request:get-parameter("group", ())
let $name := request:get-parameter("name", ())
let $case := request:get-parameter("case", ())
return
    if ($case) then
        xqts:details($case)
    else if ($group) then
        xqts:print-tests($group, $name)
    else if ($tree) then
        xqts:tree()
    else
        xqts:display-page()
