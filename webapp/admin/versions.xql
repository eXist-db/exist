xquery version "1.0";

import module namespace v="http://exist-db.org/versioning"
at "resource:org/exist/versioning/xquery/versioning.xqm";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace version="http://exist-db.org/xquery/versioning"
at "java:org.exist.versioning.xquery.VersioningModule";

declare namespace versions="http://exist-db.org/versioning/versions";

declare option exist:serialize "method=xml media-type=text/xml expand-xincludes=no";

declare function versions:restore() {
	let $resource := request:get-parameter("resource", ())
	let $rev := request:get-parameter("rev", ())
	let $doc := doc($resource)
	let $collection := util:collection-name($doc)
	let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    let $baseName := concat($vCollection, "/", $docName, ".base")
    return
        if (not(doc-available($baseName))) then
            ()
        else
            v:apply-patch(
                doc($baseName),
                collection($vCollection)/v:version[v:properties[v:document = $docName]
                    [v:revision <= $rev]]
            )
};

declare function versions:patch() {
	let $resource := request:get-parameter("resource", ())
	let $rev := request:get-parameter("rev", ())
	let $doc := doc($resource)
	let $collection := util:collection-name($doc)
	let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
	let $revisions := 
			for $r in collection($vCollection)/v:version/v:properties
				[v:document = $docName]/v:revision cast as xs:long
			order by $r ascending
			return xs:long($r)
	let $base :=
		if ($rev eq $revisions[1]) then
    		doc(concat($vCollection, "/", $docName, ".base"))
		else
			v:get-revision($doc, $revisions[index-of($revisions, $rev) - 1])
    return
		version:annotate($base, $rev)
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

let $action := request:get-parameter("action", ())
return
	if ($action eq 'restore') then
		versions:restore()
	else if ($action eq 'diff') then
		versions:diff()
	else if ($action eq 'annotate') then
		versions:annotate()
	else ()
