xquery version "1.0";
(: $Id$ :)

declare namespace ajax="http://exist-db.org/xquery/ajax";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace request="http://exist-db.org/xquery/request";

declare option exist:serialize "media-type=text/xml";

declare function ajax:display-collection($collection as xs:string) as element()* {
    (
        for $child in xdb:get-child-collections($collection) order by $child return
            <collection name="{$child}" path="{concat($collection, '/', $child)}"/>,
            
        for $child in xdb:get-child-resources($collection) order by $child return
            <resource name="{$child}" path="{concat($collection, '/', $child)}"/>
    )
};

let $collection := request:get-parameter("collection", ())
return
    <ajax-response root="{replace($collection, '/[^/]*$', '', 'mx')}">
    { 
        ajax:display-collection($collection)
    }
    </ajax-response>