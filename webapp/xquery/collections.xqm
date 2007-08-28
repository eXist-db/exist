xquery version "1.0";
(: $Id$ :)
(: Library module imported by xquery.xq :)

module namespace utils="http://exist-db.org/xquery/collection-utils";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare function utils:list-collection-names($collection as xs:string, $user as xs:string, $password as xs:string) as xs:string+
{
	replace($collection, "xmldb:exist://", ""),
	let $root := xmldb:collection($collection, $user, $password) return
		for $child in xmldb:get-child-collections($root) return
			utils:recurseChildren(concat(replace($collection, "xmldb:exist://", ""), "/", $child))
};

declare function utils:recurseChildren($collection) as xs:string+
{
	$collection,
	for $child in xmldb:get-child-collections($collection) return
		utils:recurseChildren(concat($collection, "/", $child))
};
