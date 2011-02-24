xquery version "1.0";

import module namespace sm = "http://exist-db.org/xquery/securitymanager";
declare namespace request = "http://exist-db.org/xquery/request";

declare option exist:serialize "media-type=text/json";

let $term := request:get-parameter("term", ()) return

    fn:concat("[",
        fn:string-join(
            for $username in sm:find-users-by-username($term) return
                fn:concat("""", $username, """"),
            ', '
        ),
        "]")
