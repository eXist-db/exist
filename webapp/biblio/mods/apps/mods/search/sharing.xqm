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
    let $group := fn:collection($sharing:groups-collection)/group:group[@id eq $groupId] return
        security:get-group-members($group/group:system/group:group)
};