xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace ev="http://www.w3.org/2001/xml-events";
declare namespace xf="http://www.w3.org/2002/xforms";

let $title := 'MODS XForms Body Report'

let $collection-path := concat($style:db-path-to-app, '/edit/body')
let $body-docs := collection($collection-path)/div

let $content :=
<div class="content">
      <table class="body">
         <thead>
           <tr>
              <th>tab-id</th>
              <th>File</th>
              <th>node count</th>
              <th>input count</th>
              <th>select1 count</th>
              <th>repeat count</th>
           </tr>
         </thead>
      {
      for $body in $body-docs
      let $node-count := count( $body//node() )
      let $file := util:document-name($body)
      order by $node-count descending
      return
           <tr>
              <td>{string($body/@class)}</td>
              <td><a href="../edit/body/{$file}">{$file}</a></td>
              <td>{$node-count}</td>
              <td>{count( $body//xf:input )}</td>
              <td>{count( $body//xf:select1 )}</td>
              <td>{count( $body//xf:repeat )}</td>
           </tr>
       }</table>
      
</div>

return style:assemble-page($title, $content)
