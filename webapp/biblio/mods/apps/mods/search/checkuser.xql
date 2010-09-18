xquery version "1.0";

let $user := request:get-parameter("user", ())
let $password := request:get-parameter("password", ())
return
    if (xmldb:login("/db", $user, $password)) then
        <ok/>
    else (
        response:set-status-code(403),
        <span>Wrong user or password.</span>
    )