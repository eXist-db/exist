xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";

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

declare function op:update-collection-sharing($collection as xs:string, $restriction as xs:string, $user-group as xs:string?) as element(status) {
    
    let $null := if($restriction eq "user") then
        (
            (: onlu this user can access, so restrict full access to user :)
            let $current-group := xmldb:get-group($collection) return
                xmldb:set-collection-permissions($collection, request:get-attribute("xquery.user"), $current-group, $rwx------)
        )
        else if($restriction eq "group")then
        (
            (: anyone in the group can access, so restrict full access to group :)
            let $current-owner := xmldb:get-owner($collection) return
                xmldb:set-collection-permissions($collection, $current-owner, $user-group, $rwxrwx---)
        )
        else
        (
            (: anyone can access, so allow full access to everyone :)
            let $current-owner := xmldb:get-owner($collection),
            $current-group := xmldb:get-group($collection) return
                xmldb:set-collection-permissions($collection, $current-owner, $current-group, $rwxrwxrwx)
        )
    return
        <status id="sharing">{$restriction}{if($restriction eq "group")then(concat(": ", $user-group))else()}</status>
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
        op:update-collection-sharing($collection, request:get-parameter("restriction", ()), request:get-parameter("userGroup",()))
    else if($action eq "remove-resource")then
        op:remove-resource(request:get-parameter("resource",()))
    else if($action eq "move-resource")then
        op:move-resource(request:get-parameter("resource",()), request:get-parameter("path",()))
    else
        op:unknown-action($action)