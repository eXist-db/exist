(: Library module imported by xquery.xq :)
module namespace utils="http://exist-db.org/xquery/collection-utils";

(: Namespace points to the Collection class :)
declare namespace coll="java:org.xmldb.api.base.Collection";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $utils:driver { "org.exist.xmldb.DatabaseImpl" };

(:  Returns a sequence of xs:string containing the collection
    path of the specified collection and all child collections.
:)
declare function utils:list-collection-names(
    $collection as xs:string, $user as xs:string,
    $password as xs:string) as xs:string+
{
    let $init := xmldb:register-database(
            $utils:driver, 
            true()
        ),
        $root := xmldb:collection($collection, $user, $password)
    return
        utils:scan-collections($root)
};

declare function utils:scan-collections($collection as object)
as xs:string+
{ 
    coll:get-name($collection),
	for $c in coll:list-child-collections($collection)
	let $child := coll:get-child-collection($collection, $c)
	return
	    utils:scan-collections($child)
};
