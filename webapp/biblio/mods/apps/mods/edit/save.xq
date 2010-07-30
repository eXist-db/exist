xquery version "1.0";

(: XQuery script to save a new MODS record from an incomming HTTP POST :)

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";
declare namespace mods="http://www.loc.gov/mods/v3";

let $title := 'MODS Record Save Confirmation'

let $app-collection := $style:db-path-to-app
let $data-collection := $style:db-path-to-app-data
let $save-file := concat($style:db-path-to-app, '/edit/save.xml')
 
(: this is where the form "POSTS" documents to this XQuery using the POST method of a submission :)
let $item := request:get-data()

let $doc := doc($save-file)/data


(: in the incoming has any part then we update it in the document :)
let $s1 := if ($item//part1) then update replace $doc/part1 with $item/part1 else ()
let $s2 := if ($item//part2) then update replace $doc/part2 with $item/part2 else ()
let $s3 := if ($item//part3) then update replace $doc/part3 with $item/part3 else ()
let $s4 := if ($item//part4) then update replace $doc/part4 with $item/part4 else ()
let $s5 := if ($item//part5) then update replace $doc/part5 with $item/part5 else ()

let $content :=
<div class="content">
  
  
  <tabs>
  <a href="edit-1.xq" class="tab1">Citation</a>
  <a href="edit-2.xq" class="tab2">Description</a>
  <a href="edit-3.xq" class="tab3">Contents</a>
  <a href="edit-4.xq" class="tab4">Relationships</a>
  <a href="edit-5.xq" class="tab5">Administration</a>
  </tabs>
  <br/><br/>
  
  Save Status = <span class="success">OK</span>
  
  
</div>

return style:assemble-page($title, $content)

