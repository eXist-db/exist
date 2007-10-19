xquery version "1.0";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

declare namespace display="display-collection";

declare function display:display-collection($colName as xs:string) as element()*
{
    <collection 
        name="{util:collection-name($colName)}"
        owner="{xdb:get-owner($colName)}"
        group="{xdb:get-group($colName)}"
        permissions="{xdb:permissions-to-string(xdb:get-permissions($colName))}"
    >
    {
        for $child in xdb:get-child-collections($colName)
        let $childCol := concat($colName, "/", $child)
        return
            display:display-collection($childCol)
    }
    {
        for $res in xdb:get-child-resources($colName)
        return
            display:display-resource($colName, $res)
    }
    </collection>
};

declare function display:display-resource($colName, $resource)
as element()* {
    <resource 
        name="{$resource}"
        owner="{xdb:get-owner($colName, $resource)}"
        group="{xdb:get-group($colName, $resource)}"
        permissions="{xdb:permissions-to-string(xdb:get-permissions($colName, $resource))}"/>
};


display:display-collection($xinclude:current-collection)