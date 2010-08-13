xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace xf = "http://www.w3.org/2002/xforms";
declare option exist:serialize "method=xml media-type=text/xml indent=yes";

(: codes-for-tab.xq
   This module will load all the code tables for a specific tab of a large multi-part form.
   Created for the MODS form.
   It calculates which tabs are needed by looking through the XForms body files
   in the edit/body collection of the mods editor.
   
   Input parameter: the tab ID
   Output: a list of all the code tables used by the tab in XML
   
   Author: Dan McCreary
   Date: Aug. 2010
   
   :)

let $tab-id := request:get-parameter('tab-id', '')

(: TODO check for required tab-id parameter and make sure that the tab is a valid tab ID
let $check-tab := if ( string-length($tab-id) < 1 )
  then
        <error>
           <message>Tab ID is a required parameter.</message>
        </error>
else
:)

let $code-table-collection := concat($style:web-path-to-app, '/code-tables/')
let $xforms-body-collection := concat($style:web-path-to-app, '/edit/body')
let $codes-found := collection($xforms-body-collection)/*[@tab-id=$tab-id]//xf:itemset
let $count := count($codes-found)

return
<code-tables>
   <code-table-collection>{$code-table-collection}</code-table-collection>
   <xforms-body-collection>{$xforms-body-collection}</xforms-body-collection>
   <tab-id>{$tab-id}</tab-id>
   <code-table-count>{$count}</code-table-count>
   {for $code-table in $codes-found
      let $nodeset := string($code-table/@nodeset)
      let $after := substring-after($nodeset, "code-table-name='")
      let $code-table-name := substring-before($after, "']/items/item")
      let $file-path := concat($code-table-collection, $code-table-name, 's.xml')
      let $code-table := doc($file-path)
      return
         $code-table
   }
</code-tables>