xquery version "1.0";
(: $Id$ :)

import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";

declare namespace mods="http://www.loc.gov/mods/v3";

(: declare option exist:serialize "media-type="text/xml"; :)

let $resources := request:get-parameter("r", ())
let $cached := session:get-attribute("cache")
return
    if ($cached) then
        <mods:modsCollection>
        {
            for $r in $resources
            return $cached[xs:int($r)]
        }
        </mods:modsCollection>
    else
        <html>
            <body>
                <h1>No valid session found!</h1>
            </body>
        </html>
