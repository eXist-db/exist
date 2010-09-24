xquery version "1.0";

module namespace sharing = "http://exist-db.org/mods/sharing";

import module namespace config = "http://exist-db.org/mods/config" at "config.xqm";
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

declare function sharing:is-group-owner($groupId as xs:string, $user as xs:string) as xs:boolean
{
    exists(fn:collection($sharing:groups-collection)/group:group[@id eq $groupId]/group:system[group:owner eq $user])
};

declare function sharing:create-group($group-name as xs:string, $owner as xs:string, $group-member as xs:string*) as xs:string?
{
    let $new-group-id := util:uuid(),
    $system-group-name :=  fn:concat($owner, ".", fn:replace($group-name, "[^a-zA-Z0-9]", "")) return
    
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
                 $new-group-id
        )
        else()
    
};