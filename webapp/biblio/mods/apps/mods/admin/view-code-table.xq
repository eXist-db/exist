xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

let $page-title := 'View of Code Table'

let $data-collection := concat($style:db-path-to-app, '/code-tables')
let $code-tables := collection($data-collection)/code-table
let $id := request:get-parameter('id', '')
let $code-table := $code-tables[code-table-name/text()=$id]
let $items := $code-table/items/item
let $count := count($items)

let $content := 
    <div class="content">
       
       <p>Code Table Collection: {$data-collection}</p>
       <p>{$count} Items in Code Table</p>
       <table>
          <thead>
             <tr>
                <th class="span-3">Data Element</th>
                <th class="span-16">Value</th>
             </tr>
          </thead>
          <tbody>
             <tr>
                <th class="span-3">Code Table Name</th>
                <td>{$code-table/code-table-name/text()}</td>
              </tr>
              <tr>              
                <th class="span-3">Description</th>
                <td class="span-16">{$code-table/description/text()}</td>
              </tr>
              <tr>              
                <th class="span-3">Status</th>
                <td class="span-16">{$code-table/status/text()}</td>
              </tr>
              <tr>
                <th class="span-3">Data Source</th>
                <td class="span-16">{$code-table/data-source/text()}</td>
             </tr>
             </tbody>
      </table>
      
      <table>
          <thead>
             <tr>
                <th class="span-5">Label</th>
                <th class="span-4">Numeric ID</th>
                <th class="span-4">Value</th>
             </tr>
          </thead>
          <tbody>
       {
         for $item in $items
            let $label := $item/label/text()
            order by $label
            return
               <tr>
                  <td>{$label}</td>
                  <td>{$item/numeric-id/text()}</td>
                  <td>{$item/value/text()}</td>
               </tr>
               
      }
      </tbody>
      </table>
      
      <a href="code-table-edit.xq?id={$id}">Edit</a>
      <a style="margin-left: 10px;" href="get-instance.xq?id={$id}">View XML</a>
      
</div>

return style:assemble-page($page-title, $content)

