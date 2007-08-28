xquery version "1.0";
(: $Id$ :)

import module namespace request="http://exist-db.org/xquery/request";
import module namespace response="http://exist-db.org/xquery/response";
import module namespace session="http://exist-db.org/xquery/session";

declare namespace mods="http://www.loc.gov/mods/v3";

let $resources := request:get-parameter("r", ())
let $cached := session:get-attribute("cache")
return
    if ($cached) then (
        response:set-header("Content-Type","text/xml"),
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
