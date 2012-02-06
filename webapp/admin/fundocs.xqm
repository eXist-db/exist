module namespace docs="http://exist-db.org/xquery/admin/fundocs";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xqdm="http://exist-db.org/xquery/xqdoc";
import module namespace sm="http://exist-db.org/xquery/securitymanager";
declare namespace xqdoc="http://www.xqdoc.org/1.0";

declare variable $docs:COLLECTION := "/db/xqdocs";

(:~
    Collection configuration for the function docs. We use an ngram
    index for fast substring searches.
:)
declare variable $docs:config :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xqdoc="http://www.xqdoc.org/1.0">
            <fulltext default="none" attributes="no"/>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="section">
                    <ignore qname="title"/>
                    <ignore qname="programlisting"/>
                    <ignore qname="screen"/>
                    <ignore qname="synopsis"/>
                </text>
                <text qname="title" boost="2.0"/>
            </lucene>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="para"/>
                <text qname="title" boost="2.0"/>
                <ignore qname="title"/>
            </lucene>
            <ngram qname="xqdoc:name"/>
            <ngram qname="xqdoc:description"/>
        </index>
    </collection>;

declare function docs:main() as element() {
    let $action := request:get-parameter("action", ())
    let $context := request:get-context-path()
    return
        <div class="panel">
            <h1>Install Documentation</h1>
            
            <p>This form is used to extract function module documentation and to make
            the main documentation searchable.</p>
            
            {
                if ($action) then
                    <div>
                        <p>Processed:</p>
                        <div class="output">
                        {
                            if ($action eq 'generate') then
                                docs:setup()
                            else if ($action eq 'load') then
                                docs:action-load()
                            else
                                ()
                        }
                        </div>
                        <p>Continue to <a href="{$context}/xquery/search.xql">search documentation</a> or 
                            <a href="{$context}/xquery/functions.xql">function libraries</a>.</p>
                    </div>
                else
                    ()
            }
            
            <form action="{session:encode-url(request:get-uri())}" method="GET">
                <input type="hidden" name="panel" value="fundocs"/>
                <div class="inner-panel">
            	    <h2>Automatically Generate and Index the Documentation</h2>
                    <button type="submit" name="action" 
                        value="generate">Generate</button>
                </div>
                
                <div class="inner-panel">
                    <h2>Extract Documentation for Selected XQuery Modules</h2>
                    
                        <label for="uri2">Choose known module:</label>
                        <select name="uri2">
                            <option></option>
                        {
                            for $uri in util:mapped-modules()
                            return
                                <option>{$uri}</option>
                        }
                        </select><br/>
                        <label for="uri">or enter module URI:</label>
                        <input type="text" name="uri" size="40"/><br/>
                        <button type="submit" name="action" 
                            value="load">Load</button>
                </div>
            </form>
        </div>
};

declare function docs:action-load() {
    let $uri1 := request:get-parameter("uri", "")
    let $uri2 := request:get-parameter("uri2", "")
    let $uri := if (string-length($uri1) gt 0) then $uri1 else $uri2
    let $setup := 
        if (empty(collection("/db/xqdocs"))) then
            docs:configure()
        else
            ()
    return
        if (string-length($uri) gt 0) then
            docs:load-external($uri)
        else ()
};

declare function docs:create-collection($parent as xs:string, $child as xs:string) as empty() {
    let $null := xdb:create-collection($parent, $child)
    return ()
};

declare function docs:configure() {
    let $null := (
       	docs:create-collection("/db/system/config", "db"),
       	docs:create-collection("/db/system/config/db", "xqdocs"),
       	xdb:store("/db/system/config/db/xqdocs", "collection.xconf", $docs:config),
       	docs:create-collection("/db", "xqdocs"),
	sm:chown(xs:anyURI("/db/xqdocs"), "admin:guest"),
       	sm:chmod(xs:anyURI("/db/xqdocs"), "rwxr-xr--")
    ) return ()
};

declare function docs:load-external($uri as xs:string) {
    let $xml := xqdm:scan(xs:anyURI($uri))
    let $moduleURI := $xml//xqdoc:module/xqdoc:uri
    let $docName := concat(util:hash($moduleURI, "MD5"), ".xml")
	let $stored := xdb:store($docs:COLLECTION, $docName, $xml)
	let $null := sm:chown(xs:anyURI($stored), "admin:guest")
	let $null := sm:chmod(xs:anyURI($stored), "rwxr-xr--")
	return
	   <li>Extracted docs from external module {$moduleURI}</li>
};

declare function docs:load-external-modules() {
    for $uri in util:mapped-modules()
    return
        docs:load-external($uri)
};

declare function docs:load-internal-modules() {
    for $moduleURI in util:registered-modules()
	let $moduleDocs := util:extract-docs($moduleURI)
	let $docName := concat(util:hash($moduleURI, "MD5"), ".xml")
	return 
	   if ($moduleDocs) then 
    		  let $stored := xdb:store($docs:COLLECTION, $docName, $moduleDocs)
let $null := sm:chown(xs:anyURI($stored), "admin:guest")
        let $null := sm:chmod(xs:anyURI($stored), "rwxr-xr--")

            return
                <li>Extracted docs from builtin module {$moduleURI}</li>
	   else
	      <li>No content for module {$moduleURI}</li>
};

declare function docs:load-fundocs() {
	docs:load-internal-modules(),
	docs:load-external-modules()
};

declare function docs:load-documentation() {
	let $home := system:get-exist-home()
	let $dir := 
		if (doc-available(concat("file:///", $home, "/webapp/download.xml"))) then
			concat($home, "/webapp")
		else if(ends-with($home, "WEB-INF")) then
			substring-before($home, "WEB-INF")
		else
			concat($home)
	let $stored :=
		xdb:store-files-from-pattern($docs:COLLECTION, $dir, "*.xml", "text/xml")
	for $path in $stored
	return
	let $null := sm:chown(xs:anyURI($path), "admin:guest")
        let $null := sm:chmod(xs:anyURI($path), "rwxr-xr--") return

	   <li>{$path}</li>
};

declare function docs:setup() as element(ul) {
    <ul>
    {
       	docs:configure(),
       	docs:load-fundocs(),
       	docs:load-documentation()
    }
    </ul>
};
