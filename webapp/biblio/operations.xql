xquery version "1.0";

declare namespace op="http://exist-db.org/xquery/biblio/operations";

declare function op:create-collection($parent as xs:string) {
    let $name := request:get-parameter("name", ())
    let $collection := xmldb:create-collection($parent, $name)
    return
        <status id="created">{$collection}</status>
};

let $action := request:get-parameter("action", ())
let $collection := request:get-parameter("collection", ())
return
    if ($action eq 'create-collection') then
        op:create-collection($collection)
    else (
        response:set-status-code(403),
        <p>Unknown action: {$action}.</p>
    )