xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace response="http://exist-db.org/xquery/response";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

let $load := request:get-parameter('load', ())
return
    if (util:binary-doc-available($load)) then
        let $data:= util:binary-doc($load)
        let $mime := xdb:get-mime-type(xs:anyURI($load))
        return
            response:stream-binary($data, $mime)
    else
        doc($load)