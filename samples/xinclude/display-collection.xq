xquery version "1.0";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

declare namespace display="display-collection";

declare function display:display-collection($collection as object)
as element()* {
    <collection 
        name="{util:collection-name($collection)}"
        owner="{xdb:get-owner($collection)}"
        group="{xdb:get-group($collection)}"
        permissions="{xdb:permissions-to-string(xdb:get-permissions($collection))}"
    >
    {
        for $child in xdb:get-child-collections($collection)
        let $childCol := xdb:collection(concat(util:collection-name($collection), "/", $child), "guest", "guest")
        return
            display:display-collection($childCol)
    }
    {
        for $res in xdb:get-child-resources($collection)
        return
            display:display-resource($collection, $res)
    }
    </collection>
};

declare function display:display-resource($collection, $resource)
as element()* {
    <resource 
        name="{$resource}"
        owner="{xdb:get-owner($collection, $resource)}"
        group="{xdb:get-group($collection, $resource)}"
        permissions="{xdb:permissions-to-string(xdb:get-permissions($collection, $resource))}"/>
};

let $collection := xdb:collection($xinclude:current-collection, "guest", "guest")
return
    display:display-collection($collection)