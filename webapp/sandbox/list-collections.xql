xquery version "1.0";
(: $Id$ :)

declare namespace sandbox="http://exist-db.org/xquery/sandbox";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace util="http://exist-db.org/xquery/util";

declare option exist:serialize "media-type=text/xml";

declare function sandbox:display-child-collections($collection as xs:string) as element()*
{
    for $child in xdb:get-child-collections($collection)
    let $path := concat($collection, '/', $child)
    order by $child
    return (
        <option value="{$path}">{$path}</option>,
        sandbox:display-child-collections($path)
    )
};

<select id="collection">
{ sandbox:display-child-collections("/db") }
</select>