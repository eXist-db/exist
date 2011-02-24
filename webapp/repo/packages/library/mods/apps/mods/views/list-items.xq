xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

declare default element namespace "http://www.loc.gov/mods/v3";

let $page-title := 'Listing of MODS Test Records'

let $data-collection := $style:db-path-to-app-data

let $content := 
    <div class="content">
       <p>Collection: {$data-collection}</p>
       <ol>{
         for $item in collection($data-collection)//mods
            (: if we don't find a mods identifier then use the file name :)
            let $id :=
               if ($item/@ID) then string($item/@ID)
               else substring-before(util:document-name($item), '.xml')
            let $title := if($item/titleInfo[1]/title[1]/text())
            then
            $item/titleInfo[1]/title[1]/text()
            else
            '(no title)'
            order by $title
            return
               <li>
               <a href="view-item.xq?id={$id}">{$title}</a>
               ({$id})
               <a href="get-instance.xq?id={$id}">XML</a>
               
               </li>
               
      }</ol>
</div>

return style:assemble-page($page-title, $content)

