xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";

let $title := 'Tabs for Code Table Report'

let $content :=
<div class="content">
  <p>For each code table, this report shows what tabs in the main form use this code table.</p>
  <ol>
  {for $code-table-name in collection($mods:code-table-collection)//code-table-name/text()
     order by $code-table-name
     return
        <li>
           <b>Code Table Name = {$code-table-name}</b><br/>
           { mods:tabs-for-code-table($code-table-name) }
        </li>
  }
  </ol>
</div>

return style:assemble-page($title, $content)