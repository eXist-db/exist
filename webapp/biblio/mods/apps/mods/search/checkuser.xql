xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";

declare namespace request = "http://exist-db.org/xquery/request";
declare namespace response = "http://exist-db.org/xquery/response";

let $user := request:get-parameter("user", ())
let $password := request:get-parameter("password", ())
return
    if(security:login($user, $password))then(
        <ok/>
    )
    else (
        response:set-status-code(403),
        <span>Wrong user or password.</span>
    )