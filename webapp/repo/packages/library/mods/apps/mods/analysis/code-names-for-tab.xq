xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace xf = "http://www.w3.org/2002/xforms";
declare option exist:serialize "method=xml media-type=text/xml indent=yes";

(: code-names-for-tab.xq
   This Query takes an input URL parameter of a tab id and returns just the names of all
   the code tables references by that tab.  Used mostly for debugging and consistency checks.
   
   Created for the MODS form.
   
   It calculates which tabs are needed by looking through the XForms body files
   in the edit/body collection of the mods editor.
   
   Input parameter: the tab ID
   Output: a list of all the code table names used by the tab
   Output format: XML
   
   Author: Dan McCreary
   Date: October. 2010
   
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
<code-table-names>
   <tab-id>{$tab-id}</tab-id>
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

      return
            <code-table-name>{$code-table-name}</code-table-name>
   }
</code-table-names>