xquery version "1.0";

module namespace security="http://exist-db.org/mods/security";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $security:GUEST_CREDENTIALS := ("guest", "guest");
declare variable $security:users-collection := "/db/mods/users";


declare function security:login($user as xs:string, $password as xs:string?) as xs:boolean
{
    (: authenticate against eXist-db :)
    if(xmldb:login("/db", $user, $password))then
    (
        (: check if the users mods home collectin exists, if not create it (i.e. first login) :)
        if(security:home-collection-exists($user))then
            true()
        else
        (
            if(security:create-home-collection($user))then
            (
                true()
            )
            else
                (: failed to create the users mods home collection :)
                false()
        )
    ) else
        (: authentication failed:)
        false()
};

(:~
: Checks whether a users mods home collection exists
:
: @param user The username
:)
declare function security:home-collection-exists($user as xs:string) as xs:boolean
{
   xmldb:collection-available(fn:concat($security:users-collection, "/", $user))
};

(:~
: Creates a users mods home collection and sets permissions
:)
declare function security:create-home-collection($user as xs:string) as xs:string
{
    let $collection-uri := xmldb:create-collection($security:users-collection, $user) return
        if($collection-uri) then
        (
            let $null := xmldb:set-collection-permissions($collection-uri, $user, xmldb:get-user-primary-group($user), xmldb:string-to-permissions("rwu------")) return
                $collection-uri
        ) else (
            $collection-uri
        )            
};