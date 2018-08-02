xquery version "3.1";

declare namespace compression = "http://exist-db.org/xquery/compression";
declare namespace util = "http://exist-db.org/xquery/util";

(:~
:
: Simple example showing how to use compression:deflate() / inflate()
:
: @author Olaf Schreck <olaf@existsolutions.com>
:)

let $testinput  := "Hello World!"

(: RFC1950 deflate [compressed data wrapped in zlib header/footer] :)
let $ex_defl    := compression:deflate(util:string-to-binary($testinput))
let $ex_infl    := compression:inflate($ex_defl)
let $output     := util:base64-decode($ex_infl)
let $result     := if ($output = $testinput) then "OK" else "FAIL"

(: RFC1951 deflate [raw compression without zlib header/footer] :)
(: for raw deflate/inflate, set 2nd arg to true() :)
let $ex_rawdefl := compression:deflate(util:string-to-binary($testinput), true())
let $ex_rawinfl := compression:inflate($ex_rawdefl, true())
let $rawoutput  := util:base64-decode($ex_rawinfl)
let $rawresult  := if ($rawoutput = $testinput) then "OK" else "FAIL"

return
    <result>
        <rfc1950>
            <ex_defl>{$ex_defl}</ex_defl>
            <ex_infl>{$ex_infl}</ex_infl>
            <output>{$output}</output>
            <result>{$result}</result>
        </rfc1950>
        <rfc1951>
            <ex_rawdefl>{$ex_rawdefl}</ex_rawdefl>
            <ex_rawinfl>{$ex_rawinfl}</ex_rawinfl>
            <rawoutput>{$rawoutput}</rawoutput>
            <rawresult>{$rawresult}</rawresult>
        </rfc1951>
    </result>
