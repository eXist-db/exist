xquery version "1.0";

declare namespace json="http://www.json.org";
declare option exist:serialize "method=json media-type=text/javascript";

declare function local:sub-collections($root as xs:string, $children as xs:string*) {
        for $child in $children
        return
            <children json:array="true">
			{ local:collections(concat($root, '/', $child), $child) }
			</children>
};

declare function local:collections($root as xs:string, $label as xs:string) {
    let $children := xmldb:get-child-collections($root)
    return (
        <title>{$label}</title>,
        <isFolder json:literal="true">true</isFolder>,
        <key>{$root}</key>,
    	if (exists($children)) then
            local:sub-collections($root, $children)
    	else
            ()
    )
};

let $collection := request:get-parameter("root", "/db")
return
    <collection json:array="true">
    {local:collections($collection, replace($collection, "^.*/([^/]+$)", "$1"))}
    </collection>