xquery version "1.0";

(: XQuery script to save a new Code table :)

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

let $title := 'Code Table Update Confirmation'

let $data-collection := concat($style:db-path-to-app, '/code-tables')
 
(: this is where the form "POSTS" documents to this XQuery using the POST method of a submission :)
let $item := request:get-data()
 
(: get the new file name from witin the POST :)

(: get the next ID from the next-id.xml file :)
let $id := $item//code-table-name/text() 
let $file := concat($id, 's.xml')

(: this logs you into the collection :)
let $login := xmldb:login($data-collection, 'admin', '')

(: this creates the new file with a still-empty id element :)
let $store := xmldb:store($data-collection, $file, $item)

let $content := 
    <div>
        <p>Code Table {$id} has been saved.</p>
        <div class="edit-controls">
            <a href="code-table-edit.xq?id={$id}" title="Edit {$id}">Edit</a>
            <a style="margin-left: 20px;" href="get-instance.xq?id={$id}" title="View {$id}">View XML</a>
        </div>
    </div>

return style:assemble-page($title, $content)
