xquery version "1.0";

module namespace security="http://exist-db.org/mods/security";

import module namespace config="http://exist-db.org/mods/config" at "config.xqm";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $security:GUEST_CREDENTIALS := ("guest", "guest");
declare variable $security:SESSION_USER_ATTRIBUTE := "biblio.user";
declare variable $security:SESSION_PASSWORD_ATTRIBUTE := "biblio.password";
declare variable $security:users-collection := fn:concat($config:mods-root, "/users");

(:~
: Authenticates a user and creates their mods home collection if it does not exist
:
: @param user The username of the user
: @param password The password of the user
:)
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
: Stores a users credentials for the mods app into the http session
:
: @param user The username
: @param password The password
:)
declare function security:store-user-credential-in-session($user as xs:string, $password as xs:string?) as empty()
{
    session:set-attribute($security:SESSION_USER_ATTRIBUTE, $user),
    session:set-attribute($security:SESSION_PASSWORD_ATTRIBUTE, $password)
};

(:~
: Retrieves a users credentials for the mods app from the http session
: 
: @return The sequence (username as xs:string, password as xs:string)
: If there is no entry in the session, then the guest account credentials are returned
:)
declare function security:get-user-credential-from-session() as xs:string+
{
    let $user := session:get-attribute($security:SESSION_USER_ATTRIBUTE) return
        if($user)then
        (
            $user,
            session:get-attribute($security:SESSION_PASSWORD_ATTRIBUTE)
        )
        else
            $security:GUEST_CREDENTIALS
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

(:~
: Determines if a user has read access to a collection
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:can-read-collection($user as xs:string, $collection as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $users-groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 1, 1) eq 'r'
        else if ($group = $users-groups) then
            substring($permissions, 4, 1) eq 'r'
        else
            substring($permissions, 7, 1) eq 'r'
};

(:~
: Determines if a user has write access to a collection
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:can-write-collection($user as xs:string, $collection as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $users-groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 2, 1) eq 'w'
        else if ($group = $users-groups) then
            substring($permissions, 5, 1) eq 'w'
        else
            substring($permissions, 8, 1) eq 'w'
};