module namespace setup="http://exist-db.org/xquery/docs/setup";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare variable $setup:COLLECTION := "/db/xqdocs";

(:~
    Collection configuration for the function docs. We use an ngram
    index for fast substring searches.
:)
declare variable $setup:config :=
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

declare function setup:configure() {
	xdb:create-collection("/db/system/config", "db"),
	xdb:create-collection("/db/system/config/db", "xqdocs"),
	xdb:store("/db/system/config/db/xqdocs", "collection.xconf", $setup:config),
	xdb:create-collection("/db", "xqdocs"),
	xdb:chmod-collection("/db/xqdocs", 508)
};

declare function setup:load-fundocs() {
	for $moduleURI in util:registered-modules()
	let $moduleDocs := util:extract-docs($moduleURI)
	let $docName := concat(util:hash($moduleURI, "MD5"), ".xml")
	return (
		xdb:store($setup:COLLECTION, $docName, $moduleDocs, "text/xml"),
		xdb:chmod-resource($setup:COLLECTION, $docName, 508)
	)
};

declare function setup:load-documentation() {
	let $home := system:get-exist-home()
	let $dir := 
		if (doc-available(concat("file:///", $home, "/webapp/download.xml"))) then
			concat($home, "/webapp")
		else if(ends-with($home, "WEB-INF")) then
			substring-before($home, "WEB-INF")
		else
			concat($home)
	return 
		xdb:store-files-from-pattern($setup:COLLECTION, $dir, "*.xml", "text/xml")
};

declare function setup:setup($adminPass) {
	system:as-user("admin", $adminPass, (
		setup:configure(),
		setup:load-fundocs(),
		setup:load-documentation()
	))
};
