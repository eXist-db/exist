xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

(: XQuery update script that will add missing ID attributes to all roots elements in a MODS test data collection :)

declare namespace h="http://www.w3.org/1999/xhtml";

(: Note that this script does NOT used the modes namespace as a default.  This means that all scripts MUST user
the mods: prefix when referencing MODS data sets. :)
declare namespace mods="http://www.loc.gov/mods/v3";

let $page-title := 'Add the ID attribute to all MODS records'

let $data-collection := $style:db-path-to-app-data

(: Find all records that do NOT have an @ID in the root.  This means you can re-run this without errors.  This
script is thus indempotent which means it can be re-run without errors.  :)

(: Note that this checks to see if the length is greater then null. :)
let $mods-records-with-no-id := collection($data-collection)//mods:mods[string-length(@ID) < 1]

let $count := count($mods-records-with-no-id)

let $content := 
    <div class="content">
       <h1>{$page-title}</h1>
       <p>This program will go through all the MODS records in the input collection and add an @ID attribute
       to each mode record.  The file names are not changed.</p>
       <p>Collection: {$data-collection}</p>
       count of records that will be updated= {$count}<br/>
       
       <ol>
       <br/>
       {
         for $item at $count in $mods-records-with-no-id
            let $title := $item/mods:titleInfo/mods:title[1]/text()
            let $file-name := util:document-name($item)
            let $uuid := util:uuid()
            
            (: Here is where the update occurs.  Comment this out for testing.  This does not update, only insert. :)
            let $update := update insert attribute {'ID'} {$uuid} into $item 
            order by $title
            return
               <li>
                  <a href="../views/view-item.xq?id={$uuid}"><b>{$title}</b></a> 
                  file name = <a href="../data/{$file-name}">{$file-name}</a> has been updated
                   
               </li>
               
      }</ol>
</div>

return style:assemble-page($page-title, $content)

