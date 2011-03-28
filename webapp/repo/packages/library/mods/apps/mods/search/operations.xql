xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";
declare namespace group = "http://commons/sharing/group";

declare namespace op="http://exist-db.org/xquery/biblio/operations";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace response="http://exist-db.org/xquery/response";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare namespace mods="http://www.loc.gov/mods/v3";

declare variable $rwx------ := 448;
declare variable $rwxrwx--- := 504;
declare variable $rwxrwxrwx := 511;

(:~
: Creates a collection inside a parent collection
:
: The new collection inherits the owner, group and permissions of the parent
:
: @param $parent the parent collection container
: @param $name the name for the new collection
:)
declare function op:create-collection($parent as xs:string, $name as xs:string) as element(status) {
    let $collection := xmldb:create-collection($parent, fn:lower-case(fn:replace($name, " ", ""))),
    
    (: by default newly created collections inherit the owner of their parent and are set to private permissions :)
    $parent-owner := xmldb:get-owner($parent),
    $group := xmldb:get-user-primary-group($parent-owner),
    $parent-permissions := xmldb:get-permissions($parent),
    $null := xmldb:set-collection-permissions($collection, $parent-owner, $group, $rwx------) return
        <status id="created">{$collection}</status>
};

declare function op:move-collection($parent as xs:string, $path as xs:string) as element(status) {
    let $null := xmldb:move($parent, $path) return
        <status id="moved" from="{$parent}">{$path}</status>
};

declare function op:remove-collection($collection as xs:string) as element(status) {
    let $null := xmldb:remove($collection) return
        <status id="removed">{$collection}</status>
};

declare function op:update-collection-sharing($collection as xs:string, $sharing-collection-with as xs:string*, $group-list as xs:string?, $group-member as xs:string*, $group-sharing-permissions as xs:string*, $other-sharing-permissions as xs:string*) as element(status) {

    (: other :)
    let $share-with-other-outcome := if($sharing-collection-with = "other")then
    (
        sharing:share-with-other($collection, true(), ($other-sharing-permissions = "write"))
    )
    else
    (
        (: dont share with other :)
        sharing:share-with-other($collection, false(), false())
    ),
    
    
    (: group :)
    $share-with-group-outcome := if($sharing-collection-with = "group")then
    (
        (: does the group exist? :)
        let $group-id := if(sharing:group-exists($group-list))then
        (
            let $null := util:log("debug", "********* GROUP ALREADY EXISTS ***********") return
                
            (: yes, are we the owner? :)
            if(sharing:is-group-owner($group-list, security:get-user-credential-from-session()[1]))then
            (
                (: yes, so update the group members :)
                sharing:update-group($group-list, $group-member)
            )
            else
            (
                (: no, so we cant modify the group members :)
                $group-list
            )
        )
        else
        (
            let $null := util:log("debug", "********* CREATE GROUP CALLED ***********") return
        
            (: no, so create it! :)
            sharing:create-group($group-list, security:get-user-credential-from-session()[1], $group-member)
        )
        return
            (: change the collection permissions to that of the group and appropriate read/write :)
            sharing:share-with-group($collection, $group-id, true(), ($group-sharing-permissions = "write"))
    )
    else
    (
        (: dont share with group :)
        sharing:share-with-group($collection, false(), false())
    )
    return
        if($share-with-other-outcome and $share-with-group-outcome)then
            <status id="sharing">done</status>
        else
            <status id="sharing">invalid permissions</status>
};

declare function op:get-collection-sharing($collection as xs:string) as element(collection)
{
    <collection uri="{$collection}">
    {
        let $group-id := sharing:get-group-id($collection) return
            if($group-id)then
            (
                <group id="{$group-id}">
                {
                    if(sharing:group-readable($collection, $group-id))then(<read/>)else(),
                    if(sharing:group-writeable($collection, $group-id))then(<write/>)else()
                }
                </group>
            )else(),
        
        let $other-readable := sharing:other-readable($collection),
        $other-writeable := sharing:other-writeable($collection) return
        if($other-readable or $other-writeable)then
        (
            <other>
            {
                if($other-readable)then(<read/>)else(),
                if($other-writeable)then(<write/>)else()
            }
            </other>
        )else()
    }
    </collection>
};

(:~
:
: @ resource-id has the format db-document-path#node-id e.g. /db/mods/eXist/exist-articles.xml#1.36
:)
declare function op:remove-resource($resource-id as xs:string) as element(status) {
    
    let $path := substring-before($resource-id, "#")
    let $id := substring-after($resource-id, "#")
    let $doc := doc($path)
    return (
        if ($id eq "1") then
            xmldb:remove(util:collection-name($doc), util:document-name($doc))
        else
            update delete util:node-by-id($doc, $id),
    
        <status id="removed">{$resource-id}</status>
    )
};

(:~
:
: @ resource-id has the format db-document-path#node-id e.g. /db/mods/eXist/exist-articles.xml#1.36
:)
declare function op:move-resource($resource-id as xs:string, $destination-collection as xs:string) as element(status) {
    let $path := substring-before($resource-id, "#")
    let $id := substring-after($resource-id, "#")
    let $destination-resource-name := replace($path, ".*/", "")
    let $destination-path := concat($destination-collection, "/", $destination-resource-name)
    let $sourceDoc := doc($path)
    return
        if (contains($id, ".")) then
            let $resource := util:node-by-id($sourceDoc, $id)
            let $mods-destination := 
                if(doc-available($destination-path))then
                    doc($destination-path)/mods:modsCollection
                else
                    doc(
                        xmldb:store($destination-collection, $destination-resource-name,
                            <modsCollection xmlns="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.loc.gov/mods/v3 ../../webapp/WEB-INF/entities/mods-3-3.xsd"/>
                        )
                    )/mods:modsCollection
            return
            (
                update insert util:node-by-id(doc($path), $id) into $mods-destination,
                update delete util:node-by-id(doc($path), $id),
                
                <status id="moved" from="{$resource-id}">{$destination-path}</status>
            )
        else (
            xmldb:move(util:collection-name($sourceDoc), $destination-collection, util:document-name($sourceDoc)),
            <status id="moved" from="{$resource-id}">{$destination-path}</status>
        )
};

declare function op:get-sharing-group-members($groupId as xs:string) as element(members){
    <members group-id="{$groupId}">
        <owner>{element {sharing:is-group-owner($groupId, security:get-user-credential-from-session()[1])} {""}}</owner>
        {
            for $group-member in sharing:get-group-members($groupId) return
                <member>{$group-member}</member>
        }
    </members>        
};

declare function op:get-groups($collection as xs:string) as element(groups) {
    <group:groups>
    {
        for $group in sharing:get-users-groups(security:get-user-credential-from-session()[1]) return
            element group:group {
                $group/@*,
                if($group/group:system/group:group eq security:get-group($collection))then
                (
                    attribute collection { $collection },
                    for $member in sharing:get-group-members($group/@id) return
                        <group:member>{$member}</group:member>
                )else(),
                $group/child::node()
            }
    }
    </group:groups>
};

declare function op:get-group-permissions($collection as xs:string, $groupId as xs:string) as element(permissions){
    <permissions>
    {
        if(sharing:group-readable($collection, $groupId))then
        (
            <read/>
        )else(),
        
        if(sharing:group-writeable($collection, $groupId))then
        (
            <write/>
        )else()
    }
    </permissions>
};

declare function op:get-other-permissions($collection as xs:string) as element(permissions){
    <permissions>
    {
        if(sharing:other-readable($collection))then
        (
            <read/>
        )else(),
        
        if(sharing:other-writeable($collection))then
        (
            <write/>
        )else()
    }
    </permissions>
};

declare function op:unknown-action($action as xs:string) {
        response:set-status-code(403),
        <p>Unknown action: {$action}.</p>
};

let $action := request:get-parameter("action", ()),
$collection := request:get-parameter("collection", ())
return
    if($action eq "create-collection")then
        op:create-collection($collection, request:get-parameter("name",()))
    else if($action eq "move-collection")then
        op:move-collection($collection, request:get-parameter("path",()))
    else if($action eq "remove-collection")then
        op:remove-collection($collection)
    else if($action eq "update-collection-sharing")then
        op:update-collection-sharing($collection, request:get-parameter("sharingCollectionWith[]",()), request:get-parameter("groupList",()), request:get-parameter("groupMember[]",()), request:get-parameter("groupSharingPermissions[]",()), request:get-parameter("otherSharingPermissions[]",()))
    else if($action eq "get-groups")then
        op:get-groups($collection)
    else if($action eq "get-group-permissions")then
        op:get-group-permissions($collection, request:get-parameter("groupId",()))
    else if($action eq "get-other-permissions")then
        op:get-other-permissions($collection)
    else if($action eq "get-sharing-group-members")then
        op:get-sharing-group-members(request:get-parameter("groupId",()))
    else if($action eq "remove-resource")then
        op:remove-resource(request:get-parameter("resource",()))
    else if($action eq "move-resource")then
        op:move-resource(request:get-parameter("resource",()), request:get-parameter("path",()))
    else if($action eq "get-collection-sharing")then
        op:get-collection-sharing($collection)
    else
        op:unknown-action($action)