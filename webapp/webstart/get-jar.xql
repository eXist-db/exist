xquery version "1.0";

import module namespace lib    = "http://exist-db.org/xquery/lib";
import module namespace request = "http://exist-db.org/xquery/request";
import module namespace response = "http://exist-db.org/xquery/response";

let $name := request:get-attribute("name"),
    $jar := lib:get-lib($name),
    $last-modified := lib:get-lib-info($name)/@modified,
    $type := if (ends-with($name, ".jar"))
                then "application/x-java-archive"
                else if (ends-with($name, ".jar"))
                        then "application/x-java-pack200"
                        else "application/octet-stream",
    $dummy := response:set-date-header("Last-Modified", $last-modified)
return response:stream-binary($jar, $type, $name)    
