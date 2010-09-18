xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";

declare namespace request = "http://exist-db.org/xquery/request";
declare namespace response = "http://exist-db.org/xquery/response";

declare function local:authenticate() as element()
{
    let $user := request:get-parameter("user", ()),
    $password := request:get-parameter("password", ()) return
        if(security:login($user, $password))then(
            <ok/>
        )
        else (
            response:set-status-code(403),
            <span>Wrong user or password.</span>
        )
};

if(request:get-parameter("action",()))then
(
    if(request:get-parameter("action",()) eq "can-write-collection")then
    (
        <result>{security:can-write-collection(security:get-user-credential-from-session()[1], request:get-parameter("collection",()))}</result>
    )
    else if(request:get-parameter("action",()) eq "is-collection-owner")then
    (
        <result>{security:is-collection-owner(security:get-user-credential-from-session()[1], request:get-parameter("collection",()))}</result>
    )else
    (
        response:set-status-code(403),
        <unknown/>
    )
)
else
(
    local:authenticate()  
)