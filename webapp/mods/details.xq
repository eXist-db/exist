xquery version "1.0";

import module namespace display="http://exist-db.org/biblio/display" at "display.xq";

declare namespace details="http://exist-db.org/biblio/details";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace request="http://exist-db.org/xquery/request";

let $item as xs:int := request:request-parameter("item", ()),
    $cached := request:get-session-attribute("cache")
return
    if ($cached) then
        let $rec := $cached[$item]
        return
            display:record-full($rec)
    else
        <p class="error">Item not found!</p>