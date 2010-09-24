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

declare function sharing:get-group-members($groupId) as xs:string*
{
        security:get-group-members(sharing:__group-id-to-system-group-name($groupId))
};

declare function sharing:get-group-id($collection as xs:string) as xs:string?
{
    let $security-group := security:get-group($collection) return
        fn:string(fn:collection($sharing:groups-collection)/group:group[group:system/group:group eq $security-group]/@id)
};

declare function sharing:group-readable($collection as xs:string) as xs:boolean
{
    security:group-can-read-collection(security:get-group($collection), $collection)
};

declare function sharing:group-readable($collection as xs:string, $groupId as xs:string) as xs:boolean
{
    security:group-can-read-collection(sharing:__group-id-to-system-group-name($groupId), $collection)
};

declare function sharing:group-writeable($collection as xs:string) as xs:boolean
{
    security:group-can-write-collection(security:get-group($collection), $collection)
};

declare function sharing:group-writeable($collection as xs:string, $groupId as xs:string) as xs:boolean
{
    security:group-can-write-collection(sharing:__group-id-to-system-group-name($groupId), $collection)
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