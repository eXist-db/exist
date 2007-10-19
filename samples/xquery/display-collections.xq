xquery version "1.0";

(:
	Display all collections and resources in the database.
:)

declare function local:display-resources($collection as xs:string) as element()?
{
	let $resources := xmldb:get-child-resources($collection) return
		if(exists($resources)) then
			<resources count="{count($resources)}">
				{
					for $r in $resources return
						<resource name="{$r}"/>
				}
			</resources>
		else
			()
};

declare function local:display-collection($collection as xs:string) as element()
{
	let $subcolls := xmldb:get-child-collections($collection) return
		<collection name="{$collection}"
			child-collections="{count($subcolls)}">
			{ local:display-resources($collection) }
			{
				for $child in $subcolls return
					local:display-collection(concat($collection, '/', $child))
			}
		</collection>
};


let $rootColl := "xmldb:exist:///db" return
	local:display-collection($rootColl)