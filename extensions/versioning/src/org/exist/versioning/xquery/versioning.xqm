module namespace v="http://exist-db.org/versioning"; 

import module namespace version="http://exist-db.org/xquery/versioning"
at "java:org.exist.versioning.xquery.VersioningModule";
import module namespace util="http://exist-db.org/xquery/util";

declare function v:version-collection($collectionPath as xs:string) as xs:string {
	concat("/db/system/versions/", replace($collectionPath, "^/?(.*)$", "$1"))
};

declare function v:list-revisions($doc as node()) as xs:integer* {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    for $version in collection($vCollection)//v:properties[v:document = $docName]
    let $rev := xs:long($version/v:revision)
    order by $rev ascending
    return
        $rev
};

declare function v:list-versions($doc as node()) as element(v:version)* {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    for $version in collection($vCollection)/v:version[v:properties[v:document = $docName]]
    order by xs:long($version/v:properties/v:revision) ascending
    return
        $version
};

declare function v:doc($doc as node(), $rev as xs:integer) {
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

declare function v:apply-patch($base as node(), $diffs as element(v:version)*) {
    if (empty($diffs)) then
        $base
    else
        v:apply-patch(version:patch($base, $diffs[1]), subsequence($diffs, 2))
};

declare function v:annotate($doc as node(), $rev as xs:integer) {
    let $collection := util:collection-name($doc)
    let $docName := util:document-name($doc)
    let $vCollection := concat("/db/system/versions", $collection)
    let $revisions := v:list-revisions($doc)
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