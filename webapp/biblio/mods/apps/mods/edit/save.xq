xquery version "1.0";

(: XQuery script to save a new MODS record from an incoming HTTP POST :)

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
  
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";
declare namespace mods="http://www.loc.gov/mods/v3";

let $title := 'MODS Record Save'

(: this service takes an incoming POST and saves the appropriate records :)
(: note that in this version, the incoming @ID is required :)
(: the user must have write and update access to the data collection :)

let $app-collection := $style:db-path-to-app
let $data-collection := $style:db-path-to-app-data

(: this is where the form "POSTS" documents to this XQuery using the POST method of a submission :)
let $item := request:get-data()

(: check to see if we have an indentifier in the incoming post :)
let $incoming-id := $item/@ID

(: if we do not have an ID then throw an error :) 
return
if ( string-length($incoming-id) = 0 )
   then
        <error>
          <message class="warning">ERROR! Attempted to save a record with no ID specified.</message>
        </error>
   else

(: else we are doing an update to an existing file :)

let $file-to-update := concat($incoming-id, '.xml')
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

(: This checks to see if we have a titleInfo in the saved document.  
If we do then it first deletes the titleInfo in the saved document.
Next it goes through each titleInfo in the incoming record and inserts it in the saved document. 
If name (the "next" element in the canonical order) occurs in the saved document, the titleInfo is inserted before name, maintaining order in the document.
If name does not occur, titleInfo is inserted at the end of the saved document.:)

let $titleInfo :=
   if ($item/mods:titleInfo)
     then
        (
        update delete $doc/mods:titleInfo,
            if ($doc/mods:name)
            then
                update insert $item/mods:titleInfo preceding $doc/mods:name
            else
                update insert $item/mods:titleInfo into $doc
         )
   else ()

let $name :=
   if ($item/mods:name)
      then
        (
        update delete $doc/mods:name,
              if ($doc/mods:originInfo)
              then
                 update insert $item/mods:name preceding $doc/mods:originInfo
              else
                 update insert $item/mods:name into $doc
         )
      else ()
      
let $originInfo :=
   if ($item/mods:originInfo)
      then
        (
        update delete $doc/mods:originInfo,
              if ($doc/mods:part)
              then
                 update insert $item/mods:originInfo preceding $doc/mods:part
              else
                 update insert $item/mods:originInfo into $doc
         )
      else ()

let $part :=
   if ($item/mods:part)
      then
        (
        update delete $doc/mods:part,
              if ($doc/mods:physicalDescription)
              then
                 update insert $item/mods:part preceding $doc/mods:physicalDescription
              else
                 update insert $item/mods:part into $doc
         )
      else ()

let $physicalDescription :=
   if ($item/mods:physicalDescription)
      then
        (
        update delete $doc/mods:physicalDescription,
              if ($doc/mods:targetAudience)
              then
                 update insert $item/mods:physicalDescription preceding $doc/mods:targetAudience
              else
                 update insert $item/mods:physicalDescription into $doc
         )
      else ()


let $targetAudience :=
   if ($item/mods:targetAudience)
      then
        (
        update delete $doc/mods:targetAudience,
              if ($doc/mods:language)
              then
                 update insert $item/mods:targetAudience preceding $doc/mods:language
              else
                 update insert $item/mods:targetAudience into $doc
         )
      else ()
      
let $language :=
   if ($item/mods:language)
      then
        (
        update delete $doc/mods:language,
              if ($doc/mods:typeOfResource)
              then
                 update insert $item/mods:language preceding $doc/mods:typeOfResource
              else
                 update insert $item/mods:language into $doc
         )
      else ()

let $typeOfResource :=
   if ($item/mods:typeOfResource)
      then
        (
        update delete $doc/mods:typeOfResource,
              if ($doc/mods:genre)
              then
                 update insert $item/mods:typeOfResource preceding $doc/mods:genre
              else
                 update insert $item/mods:typeOfResource into $doc
         )
      else ()

let $genre :=
   if ($item/mods:genre)
      then
        (
        update delete $doc/mods:genre,
              if ($doc/mods:subject)
              then
                 update insert $item/mods:genre preceding $doc/mods:subject
              else
                 update insert $item/mods:genre into $doc
         )
      else ()

let $subject :=
   if ($item/mods:subject)
      then
        (
        update delete $doc/mods:subject,
              if ($doc/mods:classification)
              then
                 update insert $item/mods:subject preceding $doc/mods:classification
              else
                 update insert $item/mods:subject into $doc
         )
      else ()      


let $classification :=
   if ($item/mods:classification)
      then
        (
        update delete $doc/mods:classification,
              if ($doc/mods:abstract)
              then
                 update insert $item/mods:classification preceding $doc/mods:abstract
              else
                 update insert $item/mods:classification into $doc
         )
      else ()      


let $abstract :=
   if ($item/mods:abstract)
      then
        (
        update delete $doc/mods:abstract,
              if ($doc/mods:tableOfContents)
              then
                 update insert $item/mods:abstract preceding $doc/mods:tableOfContents
              else
                 update insert $item/mods:abstract into $doc
         )
      else ()      

let $tableOfContents :=
   if ($item/mods:tableOfContents)
      then
        (
        update delete $doc/mods:tableOfContents,
              if ($doc/mods:note)
              then
                 update insert $item/mods:tableOfContents preceding $doc/mods:note
              else
                 update insert $item/mods:tableOfContents into $doc
         )
      else ()      


let $note :=
   if ($item/mods:note)
      then
        (
        update delete $doc/mods:note,
              if ($doc/mods:relatedItem)
              then
                 update insert $item/mods:note preceding $doc/mods:relatedItem
              else
                 update insert $item/mods:note into $doc
         )
      else ()

let $relatedItem :=
   if ($item/mods:relatedItem)
      then
        (
        update delete $doc/mods:relatedItem,
              if ($doc/mods:identifier)
              then
                 update insert $item/mods:relatedItem preceding $doc/mods:identifier
              else
                 update insert $item/mods:relatedItem into $doc
         )
      else ()


let $identifier :=
   if ($item/mods:identifier)
      then
        (
        update delete $doc/mods:identifier,
              if ($doc/mods:recordInfo)
              then
                 update insert $item/mods:identifier preceding $doc/mods:recordInfo
              else
                 update insert $item/mods:identifier into $doc
         )
      else ()

let $recordInfo :=
   if ($item/mods:recordInfo)
      then
        (
        update delete $doc/mods:recordInfo,
              if ($doc/mods:accessCondition)
              then
                 update insert $item/mods:recordInfo preceding $doc/mods:location
              else
                 update insert $item/mods:recordInfo into $doc
         )
      else ()

let $location :=
   if ($item/mods:location)
      then
        (
        update delete $doc/mods:location,
              if ($doc/mods:location)
              then
                 update insert $item/mods:location preceding $doc/mods:accessCondition 
              else
                 update insert $item/mods:location into $doc
         )
      else ()

let $accessCondition :=
   if ($item/mods:accessCondition)
      then
        (
        update delete $doc/mods:accessCondition,
                 update insert $item/mods:accessCondition into $doc
         )
      else ()

return
<results>
  <message>Update Status = OK titles = {count($item/mods:titleInfo)}</message>
</results>