xquery version "1.0";

declare namespace todo="http://exist-db.org/demo/todo";
declare namespace xf="http://www.w3.org/2002/xforms";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace request="http://exist-db.org/xquery/request";

declare option exist:serialize "method=xml media-type=text/html";

declare function todo:add-uuid($data as element(), $uuid as xs:string) {
    element { node-name($data) } {
        attribute id { $uuid },
        $data/@*[local-name(.) ne 'id'], $data/node()
    }
};

declare function todo:delete($uuid as xs:string) {
    xdb:remove("/db/todo", concat($uuid, ".xml"))
};

declare function todo:initialize() {
    xdb:create-collection("/db", "todo")
};

declare function todo:load-instance() {
    let $instances := collection("/db/todo")/todo
    return
        if ($instances) then
            <items>{$instances}</items>
        else
            <items>
            </items>
};
 
let $posted := request:get-data()
return
    if (not($posted)) then
        todo:load-instance()
    else if ($posted instance of element(action)) then
        let $action := $posted/@name
        return
            if ($action eq 'init') then
                todo:initialize()
            else if ($action eq 'delete') then
                todo:delete($posted/@uuid)
            else ()
    else
        let $log := util:log("DEBUG", ("Posted: ", $posted/@id))
        let $data := if ($posted/@id != '') then $posted else todo:add-uuid($posted, util:uuid())
        let $stored := xmldb:store("/db/todo", concat($data/@id, ".xml"), $data)
        return
            <edit>{doc($stored)/*}</edit>