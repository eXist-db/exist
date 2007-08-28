xquery version "1.0";
(: $Id$ :)

declare namespace sandbox="http://exist-db.org/xquery/sandbox";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace util="http://exist-db.org/xquery/util";

declare option exist:serialize "media-type=text/xml";

declare function sandbox:display-child-collections($collection as object)
as element()* {
    let $parent := util:collection-name($collection)
    for $child in xdb:get-child-collections($collection)
    let $path := concat($parent, '/', $child)
    order by $child
    return (
        <option value="{$path}">{$path}</option>,
        sandbox:display-child-collections(xdb:collection($path, "guest", "guest"))
    )
};

<select id="collection">
{ sandbox:display-child-collections(xdb:collection("/db", "guest", "guest")) }
</select>