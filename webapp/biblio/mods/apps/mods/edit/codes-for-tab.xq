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
let $debug := xs:boolean(request:get-parameter('debug', 'false'))

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
let $itemsets := collection($xforms-body-collection)/*[@tab-id=$tab-id]//xf:itemset
let $code-table-names :=
   for $itemset in $itemsets
      let $nodeset := string($itemset/@nodeset)
      let $after := substring-after($nodeset, "code-table-name='")
      let $code-table-name := substring-before($after, "']/items/item")
      order by $code-table-name
      return $code-table-name
let $distinct-code-table-names := distinct-values($code-table-names)
let $itemset-count := count($itemsets)
let $count-distinct := count($distinct-code-table-names)

return
<code-tables>

   { if ($debug)
     then
     <debug>
       <code-table-collection>{$code-table-collection}</code-table-collection>
       <xforms-body-collection>{$xforms-body-collection}</xforms-body-collection>
       <tab-id>{$tab-id}</tab-id>
       <itemset-count>{$itemset-count}</itemset-count>
       <code-table-name-count>{$count-distinct}</code-table-name-count>
       <distinct-code-table-names>{$distinct-code-table-names}</distinct-code-table-names>
     </debug>
     else ()
   }
   
   {for $code-table-name in $distinct-code-table-names
      
      let $file-path := concat($code-table-collection, $code-table-name, 's.xml')
      let $code-table := doc($file-path)
      return
         <code-table>
            <code-table-name>{$code-table-name}</code-table-name>
            <items>
            {for $item in $code-table//item
            return
               $item
            }
            </items>
         </code-table>
   }
</code-tables>