module namespace v="http://exist-db.org/versioning"; 

import module namespace version="http://exist-db.org/xquery/versioning"
at "java:org.exist.versioning.xquery.VersioningModule";
import module namespace util="http://exist-db.org/xquery/util";

declare function v:version-collection($collectionPath as xs:string) as xs:string {
	concat("/db/system/versions/", replace($collectionPath, "^/?(.*)$", "$1"))
};

(:
	Return all revisions of the specified document 
	as a sequence of xs:integer in ascending order.
:)
declare function v:revisions($doc as node()) as xs:integer* {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    for $version in collection($vCollection)//v:properties[v:document = $docName]
    let $rev := xs:long($version/v:revision)
    order by $rev ascending
    return
        $rev
};

(:~
	Return all version docs, including the full diff, for the specified
	document.
:)
declare function v:versions($doc as node()) as element(v:version)* {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    for $version in collection($vCollection)/v:version[v:properties[v:document = $docName]]
    order by xs:long($version/v:properties/v:revision) ascending
    return
        $version
};

(:~
	Restore a certain revision of a document by applying a
	sequence of diffs and return it as an in-memory node.

	@TODO: check what happens to comments and pis occurring before
	the document element.
:)
declare function v:doc($doc as node(), $rev as xs:integer) as node()* {
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
				for $version in
                	collection($vCollection)/v:version[v:properties[v:document = $docName]
                    	[v:revision <= $rev]][v:diff]
					order by xs:long($version/v:properties/v:revision) ascending
				return
					$version
            )
};

(:~
	Apply a given patch on a document. This function is used by v:doc 
	internally.
:)
declare function v:apply-patch($doc as node(), $diffs as element(v:version)*) {
    if (empty($diffs)) then
        $doc
    else
        v:apply-patch(version:patch($doc, $diffs[1]), subsequence($diffs, 2))
};

(:~
	Return an XML document in which all changes between
	$rev and $rev - 1 are annotated.
:)
declare function v:annotate($doc as node(), $rev as xs:integer) {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    let $revisions := v:revisions($doc)
    let $p := index-of($revisions, $rev)
    let $previous := 
        if ($p eq 1) then
            doc(concat($vCollection, "/", $docName, ".base"))
        else
            v:doc($doc, $revisions[$p - 1])
    return
        version:annotate(
            $previous, 
            collection($vCollection)/v:version[v:properties[v:document = $docName]
                [v:revision = $rev]]
        )
};

(:~
	Check if there are any revisions in the database which are newer than the
	version identified by the specified base revision and key. If versioning is
	active, the base revision and key are added to the document root element
	as attributes whenever a document is serialized. The combination of the two
	attributes allows eXist to determine if a newer revision of the document
	exists in the database, which usually means that another user/client has
	committed it in the meantime.

	If one or more newer revisions exist in the database, v:find-newer-revision will
	return the version document of the newest revision or an empty sequence
	otherwise.

	@return a v:version element or the empty sequence if there's no newer revision
	in the database
:)
declare function v:find-newer-revision($doc as node(), $base as xs:integer, $key as xs:string) as element(v:version)? {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
	let $newer := 
		for $v in collection($vCollection)/v:version[
			v:properties[v:document = $docName][v:revision > $base][v:key != $key]
		]
    	order by xs:long($v/v:properties/v:revision) descending
		return $v
	return $newer[1]
};

declare function v:history($doc as node()) as element(v:history) {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
	return
		<v:history>
			<v:document>{base-uri($doc)}</v:document>
			<v:revisions>
			{
				for $v in collection($vCollection)//v:properties[v:document = $docName]
				order by xs:long($v/v:revision) ascending
				return
					<v:revision rev="{$v/v:revision}">
					{ $v/v:date, $v/v:user}
					</v:revision>
			}
			</v:revisions>
		</v:history>
};
