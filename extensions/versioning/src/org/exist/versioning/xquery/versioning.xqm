module namespace v="http://exist-db.org/versioning"; 

import module namespace version="http://exist-db.org/xquery/versioning"
at "java:org.exist.versioning.xquery.VersioningModule";
import module namespace util="http://exist-db.org/xquery/util";

declare function v:version-collection($collectionPath as xs:string) as xs:string {
	concat("/db/system/versions/", replace($collectionPath, "^/?(.*)$", "$1"))
};

declare function v:list-revisions($root as node()) as xs:long* {
    let $collection := util:collection-name($root)
    let $docName := util:document-name($root)
    let $vCollection := concat("/db/system/versions", $collection)
    for $version in collection($vCollection)//v:properties[v:document = $docName]
    let $rev := xs:long($version/v:revision)
    order by $rev ascending
    return
        $rev
};

declare function v:list-versions($root as node()) as element(v:version)* {
    let $collection := util:collection-name($root)
    let $docName := util:document-name($root)
    let $vCollection := concat("/db/system/versions", $collection)
    for $version in collection($vCollection)/v:version[v:properties[v:document = $docName]]
    order by xs:long($version/v:properties/v:revision) ascending
    return
        $version
};

declare function v:get-revision($root as node(), $rev as xs:long) {
    let $collection := util:collection-name($root)
    let $docName := util:document-name($root)
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
                    	[v:revision <= $rev]]
					order by xs:long($version/v:properties/v:revision) ascending
				return
					$version
            )
};

declare function v:apply-patch($base, $diffs as element(v:version)*) {
	let $log := util:log("DEBUG", ("Applying revision: ", $diffs[1]//v:revision))
    let $newVersion := version:patch($base, $diffs[1])
    return
        if (count($diffs) > 1) then
            v:apply-patch($newVersion, subsequence($diffs, 2))
        else
            $newVersion
};
