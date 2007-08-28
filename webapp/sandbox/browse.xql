xquery version "1.0";
(: $Id$ :)

declare namespace ajax="http://exist-db.org/xquery/ajax";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace request="http://exist-db.org/xquery/request";

declare option exist:serialize "media-type=text/xml";

declare function ajax:display-collection($collection as xs:string) as element()* {
    let $c := xdb:collection($collection, "guest", "guest")
    let $parent := util:collection-name($c)
    return (
        for $child in xdb:get-child-collections($collection)
        order by $child
        return
            <collection name="{$child}" path="{concat($parent, '/', $child)}"/>,
        for $child in xdb:get-child-resources($collection)
        order by $child
        return
            <resource name="{$child}" path="{concat($parent, '/', $child)}"/>
    )
};

let $collection := request:get-parameter("collection", ())
return
    <ajax-response root="{replace($collection, '/[^/]*$', '', 'mx')}">
    { 
        ajax:display-collection($collection)
    }
    </ajax-response>