xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace xf = "http://www.w3.org/2002/xforms";

(: unit test and browser of the body files for the form :)

let $xforms-body-collection := concat($style:web-path-to-app, '/edit/body')

let $div-tags := collection($xforms-body-collection)/div

let $title := 'Unit Test for XForms Tab Div Tags'

let $content :=
<div class="content">
    <p>To pass the test for each body file must have a root div tag and the @tab-id must be a valid, unique tab-id in the tab-data.xml file.</p>
    <table class="span-18 last">
       <thead>
          <tr>
             <th class="span-4">File</th>
             <th class="span-3">Class Attribute</th>
             <th class="span-3">Tab ID Attribute</th>
             <th class="span-2">PASS/FAIL</th>
             <th class="span-1"> # Code Tables</th>
             <th class="span-1">Code Tables</th>
             <th class="span-1">Edit</th>
             <th class="span-1 last">Instance</th>
          </tr>
       </thead>
       <tbody>
        {for $div in $div-tags
            let $tab-id := string($div/@tab-id)
            let $file-name := util:document-name($div)
            let $collection-name := util:collection-name($div)
            let $file-path := concat($collection-name, '/', $file-name)
            let $itemsets := doc($file-path)/*[@tab-id=$tab-id]//xf:itemset
            let $count-itemsets := count($itemsets)
            order by $file-name
            return
               <tr>
                  <td>{$file-name}</td>
                  <td>{string($div/@class)}</td>
                  <td>{$tab-id}</td>
                  <td>
                    {if (string-length($tab-id) > 1)
                       then (<span class="pass">PASS</span>)
                       else (<span class="warn">FAIL</span>)
                    }
                  </td>
                  <td>{$count-itemsets}</td>
                  <td><a href="../edit/codes-for-tab.xq?tab-id={$tab-id}">codes</a></td>
                  <td><a href="../edit/edit.xq?tab-id={$tab-id}">edit</a></td>
                  <td><a href="../edit/get-instance.xq?tab-id={$tab-id}">instance</a></td>
               </tr>
        }
        </tbody>
    </table>
</div>

return style:assemble-page($title, $content)