xquery version "1.0";

import module namespace v="http://exist-db.org/versioning";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace version="http://exist-db.org/xquery/versioning"
at "java:org.exist.versioning.xquery.VersioningModule";

declare namespace versions="http://exist-db.org/versioning/versions";

declare option exist:serialize "method=xml media-type=text/xml expand-xincludes=no";

declare function versions:restore() {
	let $resource := request:get-parameter("resource", ())
	let $rev as xs:integer := request:get-parameter("rev", 0) cast as xs:integer
	let $doc := doc($resource)
	return
	    v:doc($doc, $rev)
};

declare function versions:diff() {
	let $resource := request:get-parameter("resource", ())
	let $rev := request:get-parameter("rev", ())
	let $doc := doc($resource)
	let $collection := util:collection-name($doc)
	let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    return
		collection($vCollection)/v:version[v:properties[v:document = $docName]
			[v:revision = $rev]]
};

declare function versions:annotate() {
    let $resource := request:get-parameter("resource", ())
	let $rev := request:get-parameter("rev", 0)
	let $doc := doc($resource)
	return
	    v:annotate($doc, xs:integer($rev))
};

let $action := request:get-parameter("action", ())
return
	if ($action eq 'restore') then
		versions:restore()
	else if ($action eq 'diff') then
		versions:diff()
	else if ($action eq 'annotate') then
	    versions:annotate()
	else ()
