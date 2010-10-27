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
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return

        (: authenticate against eXist-db :)
        if(xmldb:login("/db", $username, $password))then
        (
            (: check if the users mods home collectin exists, if not create it (i.e. first login) :)
            if(security:home-collection-exists($username))then
                true()
            else
            (
                 let $users-collection-uri := security:create-home-collection($username) return
                    true()
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
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return
    (
        session:set-attribute($security:SESSION_USER_ATTRIBUTE, $username),
        session:set-attribute($security:SESSION_PASSWORD_ATTRIBUTE, $password)
    )
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
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return
        xmldb:collection-available(security:get-home-collection-uri($username))
};

(:~
: Get the URI of a users mods home collection
:)
declare function security:get-home-collection-uri($user as xs:string) as xs:string
{
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return
        fn:concat($security:users-collection, "/", $username)
};

(:~
: Creates a users mods home collection and sets permissions
:
: @return The uri of the users home collection or an empty sequence if it could not be created
:)
declare function security:create-home-collection($user as xs:string) as xs:string?
{
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return
        if(xmldb:collection-available($security:users-collection))then
        (
            let $collection-uri := xmldb:create-collection($security:users-collection, $username) return
                if($collection-uri) then
                (
                    (: note users the group biblio.users need read access to a users home collection root so that they can list the collections inside to match against shared groups :)
                    let $null := xmldb:set-collection-permissions($collection-uri, $username, $security:biblio-users-group, xmldb:string-to-permissions("rwur-----")) return
                        $collection-uri
                ) else (
                    $collection-uri
                )
        )else()
};

(:~
: Determines if a user has read access to a collection
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:can-read-collection($user as xs:string, $collection as xs:string) as xs:boolean
{
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return

        if(xmldb:collection-available($collection))then
        (
            let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
            let $owner := xmldb:get-owner($collection)
            let $group := xmldb:get-group($collection)
            let $users-groups := xmldb:get-user-groups($username)
            return
                if ($owner eq $username) then
                    fn:substring($permissions, 1, 1) eq 'r'
                else if ($group = $users-groups) then
                    fn:substring($permissions, 4, 1) eq 'r'
                else
                    fn:substring($permissions, 7, 1) eq 'r'
        )
        else
        (
            false()
        )
};

(:~
: Determines if a user has write access to a collection
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:can-write-collection($user as xs:string, $collection as xs:string) as xs:boolean
{
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return

        if(xmldb:collection-available($collection))then
        (
            let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
            let $owner := xmldb:get-owner($collection)
            let $group := xmldb:get-group($collection)
            let $users-groups := xmldb:get-user-groups($username)
            return
                if ($owner eq $username) then
                    fn:substring($permissions, 2, 1) eq 'w'
                else if ($group = $users-groups) then
                    fn:substring($permissions, 5, 1) eq 'w'
                else
                    fn:substring($permissions, 8, 1) eq 'w'
        )
        else
        (
            false()
        )
};

(:~
: Determines if a group has read access to a collection
:
: @param group The group name
: @param collection The path of the collection
:)
declare function security:group-can-read-collection($group as xs:string, $collection as xs:string) as xs:boolean
{
    if(xmldb:collection-available($collection))then
    (
        let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
        let $owner-group := xmldb:get-group($collection)
        return
            ($group eq $owner-group and fn:substring($permissions, 4, 1) eq 'r')
    )
    else
    (
        false()
    )
};

(:~
: Determines if a group has write access to a collection
:
: @param group The group name
: @param collection The path of the collection
:)
declare function security:group-can-write-collection($group as xs:string, $collection as xs:string) as xs:boolean
{
    if(xmldb:collection-available($collection))then
    (
        let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
        let $owner-group := xmldb:get-group($collection)
        return
         ($group eq $owner-group and fn:substring($permissions, 5, 1) eq 'w')
    )
    else
    (
        false()
    )
};

(:~
: Determines if everyone has read access to a collection
:
: @param collection The path of the collection
:)
declare function security:other-can-read-collection($collection as xs:string) as xs:boolean
{
    if(xmldb:collection-available($collection))then
    (
        let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
        return
            fn:substring($permissions, 7, 1) eq 'r'
    )
    else
    (
        false()
    )
};

(:~
: Determines if everyone has write access to a collection
:
: @param collection The path of the collection
:)
declare function security:other-can-write-collection($collection as xs:string) as xs:boolean
{
    if(xmldb:collection-available($collection))then
    (
        let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
        return
            fn:substring($permissions, 8, 1) eq 'w'
    )
    else
    (
        false()
    )
};

(:~
: Determines if the user is the collection owner
:
: @param user The username
: @param collection The path of the collection
:)
declare function security:is-collection-owner($user as xs:string, $collection as xs:string) as xs:boolean
{
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return

        if(xmldb:collection-available($collection))then
        (
            let $owner := xmldb:get-owner($collection) return
                $username eq $owner
        )
        else
        (
            false()
        )
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

declare function security:get-group($collection as xs:string) as xs:string?
{
    if(xmldb:collection-available($collection))then
    (
        xmldb:get-group($collection)
    ) else()
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
    let $group-member-username := if($config:force-lower-case-usernames)then(fn:lower-case($group-member))else($group-member) return
        xmldb:create-group($group-name, $group-member-username)
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

declare function security:set-resource-permissions($resource as xs:string, $owner as xs:string, $group as xs:string, $owner-read as xs:boolean, $owner-write as xs:boolean, $group-read as xs:boolean, $group-write as xs:boolean, $other-read as xs:boolean, $other-write as xs:boolean) as empty() {
    
    let $owner-username := if($config:force-lower-case-usernames)then(fn:lower-case($owner))else($owner) return
        let $permissions := fn:concat(
            if($owner-read)then("r")else("-"),
            if($owner-write)then("w")else("-"),
            if($owner-write)then("u")else("-"),
            
            if($group-read)then("r")else("-"),
            if($group-write)then("w")else("-"),
            if($group-write)then("u")else("-"),
            
            if($other-read)then("r")else("-"),
            if($other-write)then("w")else("-"),
            if($other-write)then("u")else("-")
        ) return
            let $collection-uri := fn:replace($resource, "(.*)/.*", "$1"),
            $resource-uri := fn:replace($resource, ".*/", "") return
                xmldb:set-resource-permissions($collection-uri, $resource-uri, $owner-username, $group, xmldb:string-to-permissions($permissions))
};

declare function security:get-groups($user as xs:string) as xs:string*
{
    let $username := if($config:force-lower-case-usernames)then(fn:lower-case($user))else($user) return
        xmldb:get-user-groups($username)
};

declare function security:find-collections-with-group($collection-path as xs:string, $group as xs:string) as xs:string*
{
	for $child-collection in xmldb:get-child-collections($collection-path)
	let $child-collection-path := fn:concat($collection-path, "/", $child-collection) return
		(
			if(xmldb:get-group($child-collection-path) eq $group)then(
				$child-collection-path
			)else(),
			security:find-collections-with-group($child-collection-path, $group)
		)
};