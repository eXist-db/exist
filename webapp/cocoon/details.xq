xquery version "1.0";
(: $Id: details.xq 6434 2007-08-28 18:59:23Z ellefj $ :)

import module namespace display="http://exist-db.org/biblio/display" at "display.xq";

declare namespace details="http://exist-db.org/biblio/details";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";

let $item as xs:int := request:get-parameter("item", ()),
    $cached := session:get-attribute("cache")
return
    if ($cached) then
        let $rec := $cached[$item]
        return
            display:record-full($rec)
    else
        <p class="error">Item not found!</p>