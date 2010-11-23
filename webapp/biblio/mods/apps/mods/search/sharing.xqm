xquery version "1.0";

module namespace sharing = "http://exist-db.org/mods/sharing";

import module namespace config = "http://exist-db.org/mods/config" at "../config.xqm";
import module namespace mail = "http://exist-db.org/xquery/mail";
import module namespace security = "http://exist-db.org/mods/security" at "security.xqm";
declare namespace group = "http://exist-db.org/mods/sharing/group";

declare variable $sharing:groups-collection := fn:concat($config:mods-root, "/groups");

declare function sharing:get-groups() as element(group:group)*
{
    fn:collection($sharing:groups-collection)/group:group
};

declare function sharing:group-exists($groupId as xs:string) as xs:boolean
{
    exists(fn:collection($sharing:groups-collection)/group:group[@id eq $groupId])
};

declare function sharing:get-group-members($groupId) as xs:string*
{
        let $group := sharing:__group-id-to-system-group-name($groupId) return
            if($group)then(
                security:get-group-members($group)
            )else()
};

declare function sharing:get-group-id($collection as xs:string) as xs:string?
{
    let $security-group := security:get-group($collection) return
        fn:string(fn:collection($sharing:groups-collection)/group:group[group:system/group:group eq $security-group]/@id)
};

declare function sharing:group-readable($collection as xs:string) as xs:boolean
{
    let $group := security:get-group($collection) return
        if($group)then(
            security:group-can-read-collection($group, $collection)
        ) else (
            false()
        )
};

declare function sharing:group-readable($collection as xs:string, $groupId as xs:string) as xs:boolean
{
    let $group := sharing:__group-id-to-system-group-name($groupId) return
        if($group)then(
            security:group-can-read-collection($group, $collection)
        ) else (
            false()
        )
};

declare function sharing:group-writeable($collection as xs:string) as xs:boolean
{
    let $group := security:get-group($collection) return
        if($group)then(
            security:group-can-write-collection($group, $collection)
        ) else (
            false()
        )
};

declare function sharing:group-writeable($collection as xs:string, $groupId as xs:string) as xs:boolean
{
    let $group := sharing:__group-id-to-system-group-name($groupId) return
        if($group)then(
            security:group-can-write-collection($group, $collection)
        ) else (
            false()
        )
};

declare function sharing:other-readable($collection as xs:string) as xs:boolean
{
    security:other-can-read-collection($collection)
};

declare function sharing:other-writeable($collection as xs:string) as xs:boolean
{
    security:other-can-write-collection($collection)
};


declare function sharing:__group-id-to-system-group-name($groupId as xs:string) as xs:string?
{
    fn:collection($sharing:groups-collection)/group:group[@id eq $groupId]/group:system/group:group
};

declare function sharing:share-with-other($collection as xs:string, $read as xs:boolean, $write as xs:boolean) as xs:boolean
{
    security:set-other-can-read-collection($collection, $read)
    and
    security:set-other-can-write-collection($collection, $write)
};

declare function sharing:share-with-group($collection as xs:string, $read as xs:boolean, $write as xs:boolean) as xs:boolean
{
    security:set-group-can-read-collection($collection, $read)
    and
    security:set-group-can-write-collection($collection, $write)
};

declare function sharing:share-with-group($collection as xs:string, $groupId as xs:string, $read as xs:boolean, $write as xs:boolean) as xs:boolean
{
    let $group := sharing:__group-id-to-system-group-name($groupId) return
    if($group)then
    (
        security:set-group-can-read-collection($collection, $group, $read)
        and
        security:set-group-can-write-collection($collection, $group, $write)
    )
    else
    (
        false()
    )
};

declare function sharing:is-group-owner($group-id as xs:string, $user as xs:string) as xs:boolean
{
    exists(sharing:get-group($group-id)/group:system[group:owner eq $user])
};

declare function sharing:create-group($group-name as xs:string, $owner as xs:string, $group-member as xs:string*) as xs:string?
{
    let $new-group-id := util:uuid(),
    $system-group-name := fn:concat($owner, ".", fn:lower-case(fn:replace($group-name, "[^a-zA-Z0-9]", ""))) return
    
        if(security:create-group($system-group-name, $group-member))then
        (
            let $group-doc := xmldb:store($sharing:groups-collection, (),
                <group:group id="{$new-group-id}">
                    <group:system>
                        <group:owner>{$owner}</group:owner>
                        <group:group>{$system-group-name}</group:group>
                    </group:system>
                    <group:name>{$group-name}</group:name>
                </group:group>
            ) return
                 (: NOTE - 
                    using $system-group-name means that users can only share with groups of which they are already a member
                    otherwise $security:biblio-users-group could be used, allowing a user to share with any existing group (whether they are a member of that group or not!)
                 :)
                 security:set-resource-permissions($group-doc, $owner, $system-group-name, true(), true(), true(), false(), false(), false()),
                 $new-group-id
        )
        else() 
};

declare function sharing:update-group($group-name as xs:string, $group-members as xs:string) as xs:string
{
    let $group := fn:collection($sharing:groups-collection)/group:group[group:name eq $group-name],
    $group-id := $group/@id,
    $system-group := $group/group:system/group:group,
    $existing-group-members := security:get-group-members($system-group),
    $group-modifications := (
        for $existing-group-member in $existing-group-members return
            if(fn:contains($group-members, $existing-group-member))then
            (
                (: user is in both lists, do nothing :)
            )
            else
            (
                (: user is not in the new list, remove the user from the group :)
                security:remove-user-from-group($existing-group-member, $system-group),
                if($config:send-notification-emails)then
                (
                    sharing:send-group-removal-mail($group-name, $existing-group-member)
                )else()
            )
        ,
        for $group-member in $group-members return
            if(fn:contains($existing-group-members, $group-member))then
            (
                (: user is in both lists, do nothing :)
            )
            else
            (
                (: user is not in the new list, add the user to the group :)
                security:add-user-to-group($group-member, $system-group),
                if($config:send-notification-emails)then
                (
                    sharing:send-group-invitation-mail($group-name, $group-member)
                )else()
            )
    ) return
        $group-id
};

declare function sharing:send-group-invitation-mail($group-name as xs:string, $username as xs:string) as empty()
{
    let $mail-template := fn:doc(fn:concat($config:search-app-root, "/group-invitation-email-template.xml")) return
        mail:send-email(sharing:process-email-template($mail-template, $group-name, $username), $config:smtp-server, ())
};

declare function sharing:send-group-removal-mail($group-name as xs:string, $username as xs:string) as empty()
{
    let $mail-template := fn:doc(fn:concat($config:search-app-root, "/group-removal-email-template.xml")) return
        mail:send-email(sharing:process-email-template($mail-template, $group-name, $username), $config:smtp-server, ())
};

declare function sharing:process-email-template($element as element(), $group-name as xs:string, $username as xs:string) as element() {
    element {node-name($element) } {
        $element/@*,
        for $child in $element/node() return
            if ($child instance of element()) then
            (
                if(fn:node-name($child) eq xs:QName("config:smtp-from-address"))then
                (
                    text { $config:smtp-from-address }
                )
                else if(fn:node-name($child) eq xs:QName("sharing:user-smtp-address"))then
                (
                    text { security:get-email-address-for-user($username) }
                )
                else if(fn:node-name($child) eq xs:QName("sharing:group-name"))then
                (
                    text { $group-name }
                )
                else if(fn:node-name($child) eq xs:QName("sharing:user-name"))then
                (
                    text { $username }
                )
                else
                (
                    sharing:process-email-template($child, $group-name, $username)
                )
            )
            else
            (
                $child
            )
    }
};

declare function sharing:get-users-groups($user as xs:string) as element(group:group)*
{
    fn:collection($sharing:groups-collection)/group:group[group:system/group:group = security:get-groups($user)]       
};

declare function sharing:get-group($group-id as xs:string) as element(group:group)?
{
    fn:collection($sharing:groups-collection)/group:group[@id eq $group-id]
};

declare function sharing:find-group-collections($group-id as xs:string) as xs:string*
{
    let $system-group := sharing:__group-id-to-system-group-name($group-id) return
        security:find-collections-with-group($security:users-collection, $system-group)
};