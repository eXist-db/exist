xquery version "1.0";

import module namespace config="http://exist-db.org/mods/config" at "../config.xqm";
import module namespace json="http://www.json.org";
import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace session = "http://exist-db.org/xquery/session";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace group = "http://exist-db.org/mods/sharing/group";

(:~
: Generates an XML collection navigation representation for the biblio app
: and then renders it as JSON
:
: Includes virtual users homes and shared collections via groups
:
: @author Adam Retter <adam@exist-db.org>
:)

(:~
: Gets a collection and its descendants from the database
:)
declare function local:db-collection-children($collection-path as xs:string) as element(children)* {
    for $child in xmldb:get-child-collections($collection-path)
    let $sub-collection-path := fn:concat($collection-path, "/", $child) return
        let $children := local:collections($sub-collection-path) return
            if($children)then(
                <children>{$children}</children>
            )else()
};

(:~
: Gets all the child collections for the groups accesible to a user
:)
declare function local:group-children($collection-path as xs:string) as element(children)* {
    let $user-groups := sharing:get-users-groups(security:get-user-credential-from-session()[1]) return
        for $group at $i in  $user-groups return
            <children>{local:tree-node(fn:concat($sharing:groups-collection, "/", $group/@id), false(), $group/group:name, (), util:function(xs:QName("local:group-collection-children"), 1))/*}</children>
};

(:~
: Finds all of the collections inside a users home folder which match the group
:
:)
declare function local:user-collection-for-group($group-id as xs:string, $user-collection-path as xs:string) as node()*
{
    let $system-group := sharing:get-group($group-id)/group:system/group:group return
        for $user-sub-collection in xmldb:get-child-collections($user-collection-path)
        let $user-sub-collection-path := fn:concat($user-collection-path, "/", $user-sub-collection) return
            (
                if(security:get-group($user-sub-collection-path) eq $system-group)then(
                    local:tree-node($user-sub-collection-path, sharing:group-writeable($user-sub-collection-path, $group-id), (), (), ())
                )else(),
                    local:user-collection-for-group($group-id, $user-sub-collection-path)
            )
};

(:~
: Generates the collections for a group in the navigation tree
:
: $group-collection-path The virtual collection path of the group e.g. /db/mods/groups/1234-1234-1234-1234
:)
declare function local:group-collection-children($group-collection-path as xs:string)
{
    let $group-id := fn:replace($group-collection-path, ".*/", "") return
        for $user-collection in xmldb:get-child-collections($security:users-collection)
        let $user-collection-path := fn:concat($security:users-collection, "/", $user-collection) return
            for $node in local:user-collection-for-group($group-id, $user-collection-path) return
                <children>{$node/*}</children>
};

(:~
: Generates a node for the navigation tree
:)
declare function local:tree-node($collection-path as xs:string, $can-write as xs:boolean, $title as xs:string?, $icon-path as xs:string?, $children-function) as element(node)
{
    <node>
        <title>{if($title)then($title)else(fn:replace($collection-path, '.*/',''))}</title>
        <isFolder json:literal="true">true</isFolder>
        <key>{$collection-path}</key>
        <writeable json:literal="true">{$can-write}</writeable>
        <addClass>{if ($can-write) then 'writable' else 'readable'}</addClass>
        {
            if($icon-path)then(
                <icon>{$icon-path}</icon>
            )else(),
            
            if(not(empty($children-function)))then(
                util:call($children-function, $collection-path)
            )else()
        }
    </node>
};

(:~
: Gets the collections for the navigation
:)
declare function local:collections($root as xs:string) {
    
    if(xmldb:collection-available($root))then
    (
        let $user := security:get-user-credential-from-session()[1],
        $children := xmldb:get-child-collections($root),
        $can-write := security:can-write-collection($user, $root) return
        
            if(security:can-read-collection($user, $root)) then
            
                (: is this the users collection? :)
                if($root eq $security:users-collection) then
                (
                    (: users collection is treated specially :)
                    if($children = $user)then
                    (
                        (: found a mods home collection for the currently logged in user :)
                        let $home-collection-uri := security:get-home-collection-uri($user) return
                            local:tree-node($home-collection-uri, $can-write, "Home", "../skin/ltFld.user.gif", util:function(xs:QName("local:db-collection-children"), 1))/*
                    )else()
                )
                (: groups collection is treated specially, i.e. skipped :)
                else if($root eq $sharing:groups-collection)then
                (
                    if($user ne "guest")then(
                        local:tree-node($sharing:groups-collection, false(), "Groups", "../skin/ltFld.groups.gif", util:function(xs:QName("local:group-children"), 1))/*
                    )else()
                )
                else
                (   (: normal collection:)
                    local:tree-node($root, $can-write, (), (), util:function(xs:QName("local:db-collection-children"), 1))/*
                )
            else()
    )else()
};

(: get the collection navigation and convert to json :)
let $xml := <json>{local:collections($config:mods-root)}</json> return
    json:xml-to-json($xml)