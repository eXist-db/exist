xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";

let $page-title := 'MODS Record Delete Confirmation'

let $id := request:get-parameter("id", "")

let $app-collection := $style:db-path-to-app
let $data-collection := concat($app-collection, '/data')
let $doc := collection($data-collection)/mods:mods[@ID=$id]

(: this deletes the file but will not remove MODS records in collections :)
let $file-name := concat($id, '.xml')
let $file-path := concat($data-collection, '/', $file-name)

(: Uncomment this if you do not have admin rights :)
let $login := xmldb:login($data-collection, 'admin', 'admin123')
(: TODO - check for locks before we delete
   xmldb:document-has-lock($data-collection, $file)
:)
let $remove :=
   if ( doc($file-path) )
      then xmldb:remove($data-collection, $file-name)
      else update delete $doc

let $content :=
<div class="content">
    <p>MODS Record {$id} has been removed from the system.</p>
    
    <br/>
    <a class="success" href="../index.xq">(Back to MODS Home)</a>
</div>
return
    style:assemble-page($page-title, $content)
