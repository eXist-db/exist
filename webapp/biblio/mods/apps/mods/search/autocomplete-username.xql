xquery version "1.0";

import module namespace sm = "http://exist-db.org/xquery/securitymanager";

declare option exist:serialize "media-type=text/json";

let $term := request:get-parameter("term", ()) return

    fn:concat("[",
        fn:string-join(
            sm:find-users-by-username($term),
            ', '
        ),
        "]")
