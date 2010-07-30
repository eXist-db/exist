xquery version "1.0";

import module namespace style = "http://www.danmccreary.com/library" at "../../../modules/style.xqm";
declare namespace mods="http://www.loc.gov/mods/v3";

let $title := 'Analysis of first level elements of MODS record'

let $file-path := concat($style:db-path-to-app, '/edit/new-instance.xml')
let $doc := doc($file-path)/mods:mods

let $content :=
<div class="content">

file = {$file-path}
    <table>
       <thead>
          <tr>
            <th>Count</th>
            <th>Element Name</th>
          </tr>
       </thead>
         <tbody>
         {
          for $element at $count in $doc/*
          return 
            <tr><td>{$count}</td>
            <td>{$element/name()}</td> </tr>
      }</tbody>
      </table>
</div>

return style:assemble-page($title, $content)