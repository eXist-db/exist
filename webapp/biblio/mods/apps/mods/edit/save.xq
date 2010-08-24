xquery version "1.0";

(: XQuery script to save a new MODS record from an incomming HTTP POST :)

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
  
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";
declare namespace mods="http://www.loc.gov/mods/v3";

let $title := 'MODS Record Save'

(: this service takes an incomming POST and saves the appropriate records :)
(: note that in this version, the incomming @ID is required :)
(: the user must have write and update access to the data collection :)

let $app-collection := $style:db-path-to-app
let $data-collection := $style:db-path-to-app-data

(: this is where the form "POSTS" documents to this XQuery using the POST method of a submission :)
let $item := request:get-data()

(: check to see if we have an indentifier in the incomming post :)
let $incomming-id := $item/@ID

(: if we do not have an ID then throw an error :) 
return
if ( string-length($incomming-id) = 0 )
   then
        <error>
          <message class="warning">ERROR! Attempted to save a record with no ID specified.</message>
        </error>
   else

(: else we are doing an update to an existing file :)

let $file-to-update := concat($incomming-id, '.xml')
let $file-path := concat($data-collection, '/', $file-to-update)

(: uncomment the following line in for testing if you have not run the security setup tools :)
let $login := xmldb:login($style:db-path-to-app-data, 'admin', 'admin123')
 
(: this is the document on disk to be updated :)
let $doc := doc($file-path)/mods:mods

(: If the incoming has any part then we update it in the document 

Note that is has the side effect of adding the mods prefix in the data files.  Semantically this
means the same thing but I don't know of a way to tell eXist to always use a default namespace
when doing an update.  :)

(: TODO figure out some way to pass the element name to an XQuery function and then do an eval on the update :)

(: this checks to see if we have a titleInfo in the save.  If we do then it goes through each one and
it that is in the data it does a replace, else it does an insert. :)

let $titleInfo :=
   if ($item/mods:titleInfo)
     then 
        for $new-title-info at $count in $item/mods:titleInfo
           return
              if ($doc/mods:titleInfo[$count])
              then
                 update replace $doc/mods:titleInfo[$count] with $new-title-info
              else
                 update insert $item/mods:titleInfo[2] following $doc/mods:titleInfo[$count -1]
   else ()

let $name :=
   if ($item/mods:name)
      then
         for $new-name-info at $count in $item/mods:name
           return
              if ($doc/mods:name[$count])
              then
                 update replace $doc/mods:name[$count] with $new-name-info
              else
                 update insert $item/mods:name[2] following $doc/mods:name[$count -1]
      else ()

let $originInfo := if ($item/mods:originInfo) then update replace $doc/mods:originInfo with $item/mods:originInfo else ()

let $physicalDescription := if ($item/mods:physicalDescription) then update replace $doc/mods:physicalDescription with $item/mods:physicalDescription else ()

let $targetAudience := if ($item/mods:targetAudience) then update replace $doc/mods:targetAudience with $item/mods:targetAudience else ()

let $language := if ($item/mods:language) then update replace $doc/mods:language with $item/mods:language else ()

let $typeOfResource := if ($item/mods:typeOfResource) then update replace $doc/mods:typeOfResource with $item/mods:typeOfResource else ()

let $genre := if ($item/mods:genre) then update replace $doc/mods:genre with $item/mods:genre else ()

let $subject := if ($item/mods:subject) then update replace $doc/mods:subject with $item/mods:subject else ()

let $classification := if ($item/mods:classification) then update replace $doc/mods:classification with $item/mods:classification else ()

let $abstract := if ($item/mods:abstract) then update replace $doc/mods:abstract with $item/mods:abstract else ()

let $table-of-contents := if ($item/mods:table-of-contents) then update replace $doc/mods:table-of-contents with $item/mods:table-of-contents else ()

let $note := if ($item/mods:note) then update replace $doc/mods:note with $item/mods:note else ()

let $related := if ($item/mods:related) then update replace $doc/mods:related with $item/mods:related else ()

let $identifier := if ($item/mods:identifier) then update replace $doc/mods:identifier with $item/mods:identifier else ()

let $record-info := if ($item/mods:record-info) then update replace $doc/mods:record-info with $item/mods:record-info else ()

let $access-condition := if ($item/mods:access-condition) then update replace $doc/mods:access-condition with $item/mods:access-condition else ()


return
<results>
  <message>Update Status = OK titles = {count($item/mods:titleInfo)}</message>
</results>