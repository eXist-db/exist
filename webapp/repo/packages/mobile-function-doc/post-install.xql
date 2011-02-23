xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xqdm="http://exist-db.org/xquery/xqdoc";

declare namespace docs="http://exist-db.org/xquery/docs";
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
       	xdb:chmod-collection("/db/xqdocs", 508)
    ) return ()
};

declare function docs:load-external($uri as xs:string) {
    let $xml := xqdm:scan(xs:anyURI($uri))
    let $moduleURI := $xml//xqdoc:module/xqdoc:uri
    let $docName := concat(util:hash($moduleURI, "MD5"), ".xml")
    let $null := (
		xdb:store($docs:COLLECTION, $docName, $xml),
		xdb:chmod-resource($docs:COLLECTION, $docName, 508)
	)
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
            let $null := (
    		  xdb:store($docs:COLLECTION, $docName, $moduleDocs),
    		  xdb:chmod-resource($docs:COLLECTION, $docName, 508)
            )
            return
                <li>Extracted docs from builtin module {$moduleURI}</li>
	   else
	      <li>No content for module {$moduleURI}</li>
};

declare function docs:load-fundocs() {
	docs:load-internal-modules(),
	docs:load-external-modules()
};

docs:configure(),
docs:load-fundocs()