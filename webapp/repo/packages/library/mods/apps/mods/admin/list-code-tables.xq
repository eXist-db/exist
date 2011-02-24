xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

let $page-title := 'Listing of Code Tables'

let $data-collection := concat($style:db-path-to-app, '/code-tables')

let $sorted-code-tables :=
for $code-table in collection($data-collection)//code-table
 order by $code-table/code-table-name/text()
return
   $code-table

let $content := 
    <div class="content">
    
       <p>Collection: {$data-collection}</p>
       
       <p>Count: {count($sorted-code-tables)}</p>
       <table> 
            <thead>
               <tr>
                 <th>Name</th>
                 <th>Description</th>
                 <th>Status</th>
                 <th>XML</th>
               </tr>
            </thead>
       {
         for $item in $sorted-code-tables
            let $id := $item/code-table-name/text()
            let $description := $item/description/text()
            return
               <tr>
                 <td>
                    <a href="view-code-table.xq?id={$id}">{$id}</a>
                 </td>
                 <td>{$description}</td>
                 <td>{$item/status/text()}</td>
                 <td>
                    <a href="get-instance.xq?id={$id}">View XML</a>
                 </td>
              </tr>
               
      }</table>
</div>

return style:assemble-page($page-title, $content)

