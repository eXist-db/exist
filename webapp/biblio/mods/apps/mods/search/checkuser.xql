xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";

declare namespace request = "http://exist-db.org/xquery/request";
declare namespace response = "http://exist-db.org/xquery/response";

declare function local:authenticate($user as xs:string, $password as xs:string?) as element()
{
    if(security:login($user, $password))then(
        <ok/>
    )
    else (
        response:set-status-code(403),
        <span>Wrong user or password.</span>
    )
};

declare function local:user-can-write-collection($user as xs:string, $collection as xs:string) as element(result)
{
    <result>{security:can-write-collection($user, $collection)}</result>
};

declare function local:user-is-collection-owner($user as xs:string, $collection as xs:string) as element(result)
{
    <result>{security:is-collection-owner($user, $collection)}</result>
};

declare function local:is-users-home-collection($user as xs:string, $collection as xs:string) as element(result)
{
    <result>{security:get-home-collection-uri($user) eq $collection}</result>
};

declare function local:user-is-collection-owner-and-not-home($user as xs:string, $collection as xs:string) as element(result)
{
    <result>{security:is-collection-owner($user, $collection) and (security:get-home-collection-uri($user) ne $collection)}</result>
};

if(request:get-parameter("action",()))then
(
    if(request:get-parameter("action",()) eq "can-write-collection")then
    (
        local:user-can-write-collection(security:get-user-credential-from-session()[1], request:get-parameter("collection",()))
    )
    else if(request:get-parameter("action",()) eq "is-collection-owner")then
    (
        local:user-is-collection-owner(security:get-user-credential-from-session()[1], request:get-parameter("collection",()))
    )
    else if(request:get-parameter("action",()) eq "is-users-home-collection")then
    (
        local:is-users-home-collection(security:get-user-credential-from-session()[1], request:get-parameter("collection",()))
    )
    else if(request:get-parameter("action",()) eq "is-collection-owner-and-not-home")then
    (
        local:user-is-collection-owner-and-not-home(security:get-user-credential-from-session()[1], request:get-parameter("collection",()))
    )
    else
    (
        response:set-status-code(403),
        <unknown/>
    )
)
else
(
    local:authenticate(request:get-parameter("user", ()), request:get-parameter("password", ()))  
)