xquery version "1.0";

declare namespace op="http://exist-db.org/xquery/biblio/operations";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace response="http://exist-db.org/xquery/response";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare function op:create-collection($parent as xs:string, $name as xs:string) as element(status) {
    let $collection := xmldb:create-collection($parent, $name) return
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
    else
        op:unknown-action($action)