xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";

declare namespace mods="http://www.loc.gov/mods/v3";

let $resources := request:request-parameter("r", ())
let $cached := request:get-session-attribute("cache")
return
    if ($cached) then (
        request:set-response-content-type("text/xml"),
        <mods:modsCollection>
        {
            for $r as xs:integer in $resources
            return $cached[$r]
        }
        </mods:modsCollection>
    ) else
        <html>
            <body>
                <h1>No valid session found!</h1>
            </body>
        </html>
