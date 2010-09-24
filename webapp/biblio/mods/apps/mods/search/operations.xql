xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";

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
    let $collection := xmldb:create-collection($parent, $name),
    
    (: by default newly created collections inherit the permissions of their parent :)
    $parent-group := xmldb:get-group($parent),
    $parent-owner := xmldb:get-owner($parent),
    $parent-permissions := xmldb:get-permissions($parent),
    $null := xmldb:set-collection-permissions($collection, $parent-owner, $parent-group, $parent-permissions) return
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
    let $share-with-other-outrcome := if($sharing-collection-with = "other")then
    (
        sharing:share-with-other($collection, ($other-sharing-permissions = "read"), ($other-sharing-permissions = "write"))
    )
    else
    (
        (: dont share with other :)
        sharing:share-with-other($collection, false(), false())
    ),
    
    
    (: group :)
    $share-with-group-outcome := if($sharing-collection-with = "other")then
    (
        (: group:)
        
        (: check if owner of a group before modifying a group :)
        
        (: TODO:)
        true()
    )
    else
    (
        (: dont share with group :)
        sharing:share-with-group($collection, false(), false())
    )
    return
        if($share-with-other-outrcome and $share-with-group-outcome)then
            <status id="sharing">done</status>
        else
            <status id="sharing">invalid permissions</status>
};

(:~
:
: @ resource-id has the format db-document-path#node-id e.g. /db/mods/eXist/exist-articles.xml#1.36
:)
declare function op:remove-resource($resource-id as xs:string) as element(status) {
    
    let $path := substring-before($resource-id, "#"),
    $id := substring-after($resource-id, "#") return
    
        update delete util:node-by-id(doc($path), $id),
    
    
    <status id="removed">{$resource-id}</status>
};

(:~
:
: @ resource-id has the format db-document-path#node-id e.g. /db/mods/eXist/exist-articles.xml#1.36
:)
declare function op:move-resource($resource-id as xs:string, $destination-collection as xs:string) as element(status) {
    
    let $path := substring-before($resource-id, "#"),
    $id := substring-after($resource-id, "#"),
    $resource := util:node-by-id(doc($path), $id),
    $destination-resource-name := replace($path, ".*/", ""),
    $destination-path := concat($destination-collection, "/", $destination-resource-name) return
    
    let $mods-destination := if(doc-available($destination-path))then
        (
            doc($destination-path)/mods:modsCollection
        )
        else
        (
            doc(
                xmldb:store($destination-collection, $destination-resource-name,
                    <modsCollection xmlns="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.loc.gov/mods/v3 ../../webapp/WEB-INF/entities/mods-3-3.xsd"/>
                )
            )/mods:modsCollection
        )
    return
    (
        update insert util:node-by-id(doc($path), $id) into $mods-destination,
        update delete util:node-by-id(doc($path), $id),
        
        <status id="moved" from="{$resource-id}">{$destination-path}</status>
    )
};

declare function op:get-sharing-group-members($groupId as xs:string) as element(members){
    <members>
    {
        for $group-member in sharing:get-group-members($groupId) return
            <member>{$group-member}</member>
    }
    </members>        
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
    else
        op:unknown-action($action)