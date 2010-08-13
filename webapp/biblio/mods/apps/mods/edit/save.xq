xquery version "1.0";

(: XQuery script to save a new MODS record from an incomming HTTP POST :)

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";
  
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
let $titleInfo := if ($item//titleInfo) then update replace $doc/titleInfo with $item/titleInfo else ()
let $name := if ($item/name) then update replace $doc/name with $item/name else ()
let $originInfo := if ($item/originInfo) then update replace $doc/originInfo with $item/originInfo else ()
let $physicalDescription := if ($item/physicalDescription) then update replace $doc/physicalDescription with $item/physicalDescription else ()
let $targetAudience := if ($item/targetAudience) then update replace $doc/targetAudience with $item/targetAudience else ()
let $language := if ($item/language) then update replace $doc/part5 with $item/part5 else ()
let $typeOfResource := if ($item/typeOfResource) then update replace $doc/part5 with $item/part5 else ()
let $genre := if ($item/genre) then update replace $doc/part5 with $item/part5 else ()
let $subject := if ($item/subject) then update replace $doc/subject with $item/subject else ()
let $classification := if ($item/classification) then update replace $doc/classification with $item/classification else ()
let $abstract := if ($item/abstract) then update replace $doc/abstract with $item/abstract else ()
let $table-of-contents := if ($item/table-of-contents) then update replace $doc/table-of-contents with $item/table-of-contents else ()
let $note := if ($item/note) then update replace $doc/note with $item/note else ()
let $related := if ($item/related) then update replace $doc/related with $item/related else ()
let $identifier := if ($item/identifier) then update replace $doc/identifier with $item/identifier else ()
let $record-info := if ($item/record-info) then update replace $doc/record-info with $item/record-info else ()
let $access-condition := if ($item/access-condition) then update replace $doc/access-condition with $item/access-condition else ()

let $content :=
<div class="content">

  <message>Save Status = OK</message>
  
  {mods:tabs-table('title', true()) }
  
</div>

return $content

