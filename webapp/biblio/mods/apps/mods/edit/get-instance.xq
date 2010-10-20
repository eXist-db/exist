xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace mods = "http://www.loc.gov/mods/v3";

(: get-instance.xq - get the instance data to load into the edit XForms application.
This version of get-instance can prune the mods record to only load the instance data that is needed for a given tab.

Note that the instance that this script returns MUST include an ID for saving.

There are many sub-parts to the form.  Each part needs to map the right part of an instance into one tab
:)

declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

(: This is the document we are going to edit if we are not creating a new record :)
let $id := request:get-parameter('id', '')
let $new := request:get-parameter('new', '')

(: TODO - check for required id parameter.  In this case we need either an id or new. :)

(: This is the ID of the tab but we just use tab in the URL.  If no tab-id is
   specified then we use the title tab.  :)
let $tab-id := request:get-parameter('tab-id', 'title')
let $debug := request:get-parameter('debug', 'false')

(: The data collection passed in the URL :)
let $collection := request:get-parameter('data', ())

let $app-collection := $style:db-path-to-app
let $data-collection :=
    if ($collection) then
        $collection
    else
        $style:db-path-to-app-data

(: If we are creating a new form, then just get the part of the new-instance.  Else get the data from the correct document
   in the mods data collection that has the correct collection id :)
let $full-instance :=
   if ($new = 'true' or $id = 'new' or $id='')
      then doc(concat($style:db-path-to-app, '/edit/new-instance.xml'))/mods:mods
      else collection($data-collection)//mods:mods[@ID = $id]

(: open the tab databse so for a given tab, we go into the tab database and get the right path :)
let $tab-data := doc(concat($style:db-path-to-app, '/edit/tab-data.xml'))/tabs

(: get the tab data for this tab. :)
let $tab-data := $tab-data/tab[tab-id = $tab-id]

(: get a list of all the XPath expressions to include in this instance used by the form :)
let $paths := $tab-data/path/text()

(: build up a string of prefix:element pairs for doing an eval :)
let $path-string :=
string-join(
    for $path in $paths
    return 
      concat('mods:', $path)
  , ', ')

(: now get the eval string ready for use :)
let $eval-string := concat('$full-instance/', '(', $path-string, ')')

return
<mods:mods ID="{$id}">

  { (: this is used for debuggin only.  Just add the debug=true to the URL and it will be added to the output :)
  if ($debug = 'true')
    then <debug>
      <id>{$id}</id>
      <tab-id>{$tab-id}</tab-id>
      <instance>{$full-instance}</instance>
      <path-string>{$path-string}</path-string>
      <eval-string>{$eval-string}</eval-string>
    </debug> else ()
  }
  
  { (: this is where we run the query that gets just the data we need for this tab :)
  util:eval($eval-string)}
  
  </mods:mods>
   
