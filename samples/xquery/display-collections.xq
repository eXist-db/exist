xquery version "1.0";

(:
	Display all collections and resources in the database.
	We retrieve the root-collection	object with xmldb:collection, 
	then use the Java API to scan through the collection.
:)

(: Namespace points to the Collection class :)
declare namespace coll="java:org.xmldb.api.base.Collection";

declare function local:display-resources($collection as object)
as element()?
{
	let $resources := coll:list-resources($collection)
	return
		if(exists($resources)) then
			<resources count="{count($resources)}">
				{
					for $r in $resources
					return
						<resource name="{$r}"/>
				}
			</resources>
		else
			()
};

declare function local:display-collection($collection as object)
as element()
{
	let $subcolls := coll:list-child-collections($collection)
	return
		<collection name="{coll:get-name($collection)}"
			child-collections="{count($subcolls)}">
			{ local:display-resources($collection) }
			{
				for $c in $subcolls
				let $child := coll:get-child-collection($collection, $c)
				return
					local:display-collection($child)
			}
		</collection>
};


xmldb:register-database("org.exist.xmldb.DatabaseImpl", true()),
let
	$rootColl :=
	    xmldb:collection("xmldb:exist:///db/", "admin", "")
return
	local:display-collection($rootColl)
