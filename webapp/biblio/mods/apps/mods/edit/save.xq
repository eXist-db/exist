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
   (: test to see if the incomming item has a title :)
   if ($item/mods:titleInfo)
     then
        (
            (: first delete all current on-disk titles :)
            update delete $doc/mods:titleInfo,
            
            (: now we insert all the net titles :)
            for $new-title-info at $count in $item/mods:titleInfo
               return
                  (: if we have an existing title then we just append after the previous, else we place it before the next item :)
                  if ($doc/mods:titleInfo)
                  then 
                     update insert $new-title-info following $doc/mods:titleInfo[$count - 1]
                  else 
                     update insert $new-title-info preceding $doc/mods:name
         )
   else ()

let $name :=
   if ($item/mods:name)
      then
        (
        update delete $doc/mods:name,
        for $new-name-info at $count in $item/mods:name
           return
              if ($doc/mods:name)
              then
                 update insert $new-name-info following $doc/mods:name[$count - 1]
              else
                 update insert $new-name-info preceding $doc/mods:originInfo
         )
      else ()
      
let $originInfo :=
   if ($item/mods:originInfo)
      then
        (
        update delete $doc/mods:originInfo,
        for $new-item at $count in $item/mods:originInfo
           return
              if ($doc/mods:originInfo)
              then
                 update insert $new-item following $doc/mods:originInfo[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:physicalDescription
         )
      else ()

let $physicalDescription :=
   if ($item/mods:physicalDescription)
      then
        (
        update delete $doc/mods:physicalDescription,
        for $new-item at $count in $item/mods:physicalDescription
           return
              if ($doc/mods:physicalDescription)
              then
                 update insert $new-item following $doc/mods:physicalDescription[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:targetAudience
         )
      else ()


let $targetAudience :=
   if ($item/mods:targetAudience)
      then
        (
        update delete $doc/mods:targetAudience,
        for $new-item at $count in $item/mods:targetAudience
           return
              if ($doc/mods:targetAudience)
              then
                 update insert $new-item following $doc/mods:targetAudience[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:language
         )
      else ()
      
let $language :=
   if ($item/mods:language)
      then
        (
        update delete $doc/mods:language,
        for $new-item at $count in $item/mods:language
           return
              if ($doc/mods:language)
              then
                 update insert $new-item following $doc/mods:language[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:typeOfResource
         )
      else ()

let $typeOfResource :=
   if ($item/mods:typeOfResource)
      then
        (
        update delete $doc/mods:typeOfResource,
        for $new-item at $count in $item/mods:typeOfResource
           return
              if ($doc/mods:typeOfResource)
              then
                 update insert $new-item following $doc/mods:typeOfResource[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:genre
         )
      else ()

let $genre :=
   if ($item/mods:genre)
      then
        (
        update delete $doc/mods:genre,
        for $new-item at $count in $item/mods:genre
           return
              if ($doc/mods:genre)
              then
                 update insert $new-item following $doc/mods:genre[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:subject
         )
      else ()

let $subject :=
   if ($item/mods:subject)
      then
        (
        update delete $doc/mods:subject,
        for $new-item at $count in $item/mods:subject
           return
              if ($doc/mods:subject)
              then
                 update insert $new-item following $doc/mods:subject[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:classification
         )
      else ()      


let $classification :=
   if ($item/mods:classification)
      then
        (
        update delete $doc/mods:classification,
        for $new-item at $count in $item/mods:classification
           return
              if ($doc/mods:classification)
              then
                 update insert $new-item following $doc/mods:classification[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:abstract
         )
      else ()      


let $abstract :=
   if ($item/mods:abstract)
      then
        (
        update delete $doc/mods:abstract,
        for $new-item at $count in $item/mods:abstract
           return
              if ($doc/mods:abstract)
              then
                 update insert $new-item following $doc/mods:abstract[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:table-of-contents
         )
      else ()      

let $table-of-contents :=
   if ($item/mods:table-of-contents)
      then
        (
        update delete $doc/mods:table-of-contents,
        for $new-item at $count in $item/mods:table-of-contents
           return
              if ($doc/mods:table-of-contents)
              then
                 update insert $new-item following $doc/mods:table-of-contents[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:note
         )
      else ()      


let $note :=
   if ($item/mods:note)
      then
        (
        update delete $doc/mods:note,
        for $new-item at $count in $item/mods:note
           return
              if ($doc/mods:note)
              then
                 update insert $new-item following $doc/mods:note[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:related
         )
      else ()

let $related :=
   if ($item/mods:related)
      then
        (
        update delete $doc/mods:related,
        for $new-item at $count in $item/mods:related
           return
              if ($doc/mods:related)
              then
                 update insert $new-item following $doc/mods:related[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:identifier
         )
      else ()


let $identifier :=
   if ($item/mods:identifier)
      then
        (
        update delete $doc/mods:identifier,
        for $new-item at $count in $item/mods:identifier
           return
              if ($doc/mods:identifier)
              then
                 update insert $new-item following $doc/mods:identifier[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:record-info
         )
      else ()

let $record-info :=
   if ($item/mods:record-info)
      then
        (
        update delete $doc/mods:record-info,
        for $new-item at $count in $item/mods:record-info
           return
              if ($doc/mods:record-info)
              then
                 update insert $new-item following $doc/mods:record-info[$count - 1]
              else
                 update insert $new-item preceding $doc/mods:access-condition
         )
      else ()

let $access-condition :=
   if ($item/mods:access-condition)
      then
        (
        update delete $doc/mods:access-condition,
        for $new-item at $count in $item/mods:access-condition
           return
              if ($doc/mods:access-condition)
              then
                 update insert $new-item following $doc/mods:access-condition[$count - 1]
              else
                 (: update it into the very end of the document :)
                 update insert $new-item into $doc
         )
      else ()

return
<results>
  <message>Update Status = OK titles = {count($item/mods:titleInfo)}</message>
</results>