xquery version "1.0";

(:~ Retrieve the XML source of a MODS record :)

declare namespace mods="http://www.loc.gov/mods/v3";

import module namespace clean="http:/exist-db.org/xquery/mods/cleanup" at "cleanup.xql";

declare option exist:serialize "method=xml media-type=application/xml indent=yes";

let $id := request:get-parameter("id", ())
let $clean := request:get-parameter("clean", "no")
let $data := collection("/db/mods")//mods:mods[@ID = $id]
return
    if (empty($data)) then
        <error>No record found for id: {$id}.</error>
    else if ($clean eq "yes") then
        clean:cleanup($data)
    else
        $data