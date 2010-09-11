xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

let $page-title := 'Create New Document From Template'

let $app-collection := $style:db-path-to-app
let $code-tables := concat($app-collection, '/code-tables')
let $document-path := concat($code-tables, '/document-type-codes.xml')
let $code-table := doc($document-path)/code-table

let $content := 
<div class="content">
   <p>Document Types File: {$document-path}</p>
   <ol>{
     for $item in $code-table//item
        let $label := $item/label/text()
        order by $label
        return
           <li>
             <a href="../edit/edit.xq?type={$item/value/text()}">{$label}</a>
           </li>
  }</ol>
</div>

return style:assemble-page($page-title, $content)
