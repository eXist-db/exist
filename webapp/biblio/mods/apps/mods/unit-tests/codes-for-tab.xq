xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare option exist:serialize "method=xml media-type=text/xml indent=yes";

(: This module will load all the code tables for a specific tab of the MODS form.
   It calculates which tabs are needed by looking through the XForms body files
   in the edit/body collection of the mods editor.
   
   Input parameter: the tab ID
   Output: a list of all the code tables used by the tab in XML
   
   Author: Dan McCreary
   
   :)

let $tab-id := request:get-parameter('tab-id', '')

let $check-tab := if (not($tab-id))
  then
        <error>
           <message>Tab ID is a required parameter.</message>
        </error>
else return

let $code-table-collection := concat($style:web-path-to-app, '/code-tables/')
let $xforms-body-collection := concat($style:web-path-to-app, '/edit/body/')

return
<code-tables>
   <code-table-collection>{$code-table-collection}</code-table-collection>
   <form-body-collection>{$form-body-collection}</form-body-collection>
   {doc(concat($code-table-collection, 'dublin-code-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'marc-genre-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'title-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'name-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'name-part-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'language-2-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'language-3-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'language-3-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'subject-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'role-short-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'name-title-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'display-label-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'script-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'transliteration-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-encoding-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-point-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-keydate-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-qualifier-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'record-identifier-source-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'code-text-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'description-standard-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'type-of-resource-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'yes-no-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'genre-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'genre-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'note-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'continent-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'geographic-code-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'temporal-encoding-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'classification-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'identifier-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'location-unittype-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'physical-location-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'physical-location-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'url-access-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'url-usage-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'form-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'form-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'internet-media-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'digital-origin-type-codes.xml'))/code-table}
</code-tables>