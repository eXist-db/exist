xquery version "1.0";
(: $Id$ :)

declare namespace ajax="http://exist-db.org/xquery/ajax";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace request="http://exist-db.org/xquery/request";

declare option exist:serialize "media-type=text/xml";

declare function ajax:display-collection($collection as xs:string) as element()* {
    (
        if ($collection ne '/db') then
            <item>
                <name>..</name>
                <type>collection</type>
                <path>{replace($collection, "/[^/]*$", "")}</path>
            </item>
        else (),
        for $child in xdb:get-child-collections($collection) order by $child return
            <item>
                <name>{$child}</name>
                <type>collection</type>
                <path>{concat($collection, '/', $child)}</path>
                <mime></mime>
                <size></size>
            </item>,
            
        for $child in xdb:get-child-resources($collection) order by $child return
            <item>
                <name>{$child}</name>
                <type>resource</type>
                <path>{concat($collection, '/', $child)}</path>
                <mime>{xdb:get-mime-type(xs:anyURI(concat($collection, '/', $child)))}</mime>
                <size>{fn:ceiling(xdb:size($collection, $child) div 1024)}</size>
            </item>
    )
};

let $collection := request:get-parameter("collection", ())
return
    <ajax-response>
    { 
        ajax:display-collection($collection)
    }
    </ajax-response>