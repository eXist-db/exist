xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";

let $page-title := 'MODS Record Delete Confirmation'

let $id := request:get-parameter("id", "")

let $app-collection := $style:db-path-to-app
let $data-collection := concat($app-collection, '/data')
let $doc := collection($data-collection)/mods:mods[@ID=$id]

let $content :=
<div class="content">
        <h1>Are you sure you want to delete this MODS Record?</h1>
        
        <b>ID: </b>{$id}<br/>
        <b>Title: </b>{$doc//mods:title/text()}<br/>
        <br/>
        <br/>
        <a class="notice" href="delete.xq?id={$id}">Yes - Delete This MODS Record</a>
        <br/><br/>
        <br/>
         <a class="success" href="../index.xq">Cancel (Back to MODS Home)</a>
         <br/><br/>
</div>
return
    style:assemble-page($page-title, $content)
