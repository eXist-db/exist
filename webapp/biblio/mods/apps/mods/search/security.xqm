xquery version "1.0";

module namespace security="http://exist-db.org/mods/security";

import module namespace config="http://exist-db.org/mods/config" at "config.xqm";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $security:GUEST_CREDENTIALS := ("guest", "guest");
declare variable $security:SESSION_USER_ATTRIBUTE := "biblio.user";
declare variable $security:SESSION_PASSWORD_ATTRIBUTE := "biblio.password";

declare variable $security:biblio-users-group := "biblio.users";
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
   xmldb:collection-available(security:get-home-collection-uri($user))
};

(:~
: Get the URI of a users mods home collection
:)
declare function security:get-home-collection-uri($user as xs:string) as xs:string
{
    fn:concat($security:users-collection, "/", $user)
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
declare function security:can-read-collection($user as xs:string, $collection as xs:string) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $users-groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            fn:substring($permissions, 1, 1) eq 'r'
        else if ($group = $users-groups) then
            fn:substring($permissions, 4, 1) eq 'r'
        else
            fn:substring($permissions, 7, 1) eq 'r'
};

(:~
: Determines if a user has write access to a collection
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:can-write-collection($user as xs:string, $collection as xs:string) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $users-groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            fn:substring($permissions, 2, 1) eq 'w'
        else if ($group = $users-groups) then
            fn:substring($permissions, 5, 1) eq 'w'
        else
            fn:substring($permissions, 8, 1) eq 'w'
};

(:~
: Determines if a group has read access to a collection
:
: @param group The group name
: @param collection The path of the collection
:)
declare function security:group-can-read-collection($group as xs:string, $collection as xs:string) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner-group := xmldb:get-group($collection)
    return
        ($group eq $owner-group and fn:substring($permissions, 4, 1) eq 'r')
};

(:~
: Determines if a group has write access to a collection
:
: @param group The group name
: @param collection The path of the collection
:)
declare function security:group-can-write-collection($group as xs:string, $collection as xs:string) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner-group := xmldb:get-group($collection)
    return
        ($group eq $owner-group and fn:substring($permissions, 5, 1) eq 'w')
};

(:~
: Determines if everyone has read access to a collection
:
: @param collection The path of the collection
:)
declare function security:other-can-read-collection($collection as xs:string) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    return
        fn:substring($permissions, 7, 1) eq 'r'
};

(:~
: Determines if everyone has write access to a collection
:
: @param collection The path of the collection
:)
declare function security:other-can-write-collection($collection as xs:string) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    return
        fn:substring($permissions, 8, 1) eq 'w'
};

(:~
: Determines if the user is the collection owner
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:is-collection-owner($user as xs:string, $collection as xs:string) as xs:boolean
{
    let $owner := xmldb:get-owner($collection) return
        $user eq $owner
};

(:~
: Gets the users for a group
:
: @param the group name
: @return The list of users in the group
:)
declare function security:get-group-members($group as xs:string) as xs:string*
{
    xmldb:get-users($group)
};

(:~
: Gets a list of other biblio users
:)
declare function security:get-other-biblio-users() as xs:string*
{
    security:get-group-members($security:biblio-users-group)[. ne security:get-user-credential-from-session()[1]]
};

declare function security:get-group($collection as xs:string) as xs:string
{
    xmldb:get-group($collection)
};

declare function security:set-other-can-read-collection($collection, $read as xs:boolean) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection)) return
        let $new-permissions := if($read)then(
            fn:replace($permissions, "(......)(.)(..)", "$1r$3")
        ) else (
           fn:replace($permissions, "(......)(.)(..)", "$1-$3")
        )
        return
            xmldb:set-collection-permissions($collection, xmldb:get-owner($collection), xmldb:get-group($collection), xmldb:string-to-permissions($new-permissions)),
            
            true()
};

declare function security:set-other-can-write-collection($collection, $write as xs:boolean) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection)) return
        let $new-permissions := if($write)then(
            fn:replace($permissions, "(.......)(.)(.)", "$1w$3")
        ) else (
           fn:replace($permissions, "(.......)(.)(.)", "$1-$3")
        )
        return        
            xmldb:set-collection-permissions($collection, xmldb:get-owner($collection), xmldb:get-group($collection), xmldb:string-to-permissions($new-permissions)),
            
            true()
};

declare function security:set-group-can-read-collection($collection, $read as xs:boolean) as xs:boolean
{
    security:set-group-can-write-collection($collection, xmldb:get-group($collection), $read)
};

declare function security:set-group-can-write-collection($collection, $write as xs:boolean) as xs:boolean
{
    security:set-group-can-write-collection($collection, xmldb:get-group($collection), $write)
};

declare function security:set-group-can-read-collection($collection, $group as xs:string, $read as xs:boolean) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection)) return
        let $new-permissions := if($read)then(
            fn:replace($permissions, "(...)(.)(.....)", "$1r$3")
        ) else (
           fn:replace($permissions, "(...)(.)(.....)", "$1-$3")
        )
        return
            xmldb:set-collection-permissions($collection, xmldb:get-owner($collection), $group, xmldb:string-to-permissions($new-permissions)),
            
            true()
};

declare function security:set-group-can-write-collection($collection, $group as xs:string, $write as xs:boolean) as xs:boolean
{
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection)) return
        let $new-permissions := if($write)then(
            fn:replace($permissions, "(....)(.)(....)", "$1w$3")
        ) else (
           fn:replace($permissions, "(....)(.)(....)", "$1-$3")
        )
        return        
            xmldb:set-collection-permissions($collection, xmldb:get-owner($collection), $group, xmldb:string-to-permissions($new-permissions)),
            
            true()
};

declare function security:create-group($group-name as xs:string, $group-member as xs:string*) as xs:boolean
{
    if(xmldb:create-group($group-name))then
    (
        let $results:= for $gm in $group-member return
            xmldb:add-user-to-group($gm, $group-name)
        return
            not($results = false())        
    )
    else
    (
        false()
    )
};

declare function security:set-group-can-read-resource($group-name as xs:string, $resource as xs:string, $read as xs:boolean) as xs:boolean
{
    let $collection-uri := fn:replace($resource, "(.*)/.*", "$1"),
    $resource-uri := fn:replace($resource, ".*/", ""),
    $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection-uri, $resource-uri)) return
        let $new-permissions := if($read)then(
            fn:replace($permissions, "(...)(.)(.....)", "$1r$3")
        ) else (
           fn:replace($permissions, "(...)(.)(.....)", "$1-$3")
        )
        return
            xmldb:set-resource-permissions($collection-uri, $resource-uri, xmldb:get-owner($collection-uri, $resource-uri), $group-name, xmldb:string-to-permissions($new-permissions)),
            
            true()
};