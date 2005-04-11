xquery version "1.0";

(::pragma exist:serialize media-type="text/xml"::)

import module namespace request="http://exist-db.org/xquery/request";

declare namespace mods="http://www.loc.gov/mods/v3";

let $resources := request:request-parameter("r", ())
let $cached := request:get-session-attribute("cache")
return
    if ($cached) then
        <mods:modsCollection>
        {
            for $r as xs:integer in $resources
            return $cached[$r]
        }
        </mods:modsCollection>
    else
        <html>
            <body>
                <h1>No valid session found!</h1>
            </body>
        </html>
