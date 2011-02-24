xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace mods="http://www.loc.gov/mods/v3";

let $title := 'MODS Tab Report'

let $show-all := request:get-parameter('show-all', 'false')

let $file-path := concat($style:db-path-to-app, '/edit/tab-data.xml')
let $doc := doc($file-path)/tabs

(: get a list of all the categories that have visible tabs :)
let $all-categories := distinct-values($doc/tab/category/text())

(: only get categories that have at least one visible sub-category :)
let $visible-categories :=
   if ($show-all = 'true')
    then $all-categories
    else 
    for $category in $all-categories
        let $count-of-visible-subcategories :=
            count( $doc/tab[category/text() = $category and default-visibility/text() = 'show'] ) 
        return 
           if ($count-of-visible-subcategories > 0)
             then $category
             else ()

let $path-count := count($doc/tab/path/text())

let $content :=
<div class="content">

<style type="text/css"><![CDATA[
   .tab {
     -moz-border-radius: .5em .5em 0em 0em;
     font-weight: bold;
   }
]]></style>

file = {$file-path}
    <table class="span-18">
       <thead>
          <tr>
            <th class="span-1">#</th>
            <th class="span-2">Tab Grouping</th>
            <th class="span-4">Tab Label</th>
            <th class="span-4">Tab ID</th>
            <th class="span-2">Color</th>
            <th class="span-2">Default</th>
            <th class="span-8">Path Restrictions</th>
          </tr>
       </thead>
         <tbody>
         {
          for $tab at $count in $doc//tab
          return 
            <tr>
               <td>{$count}</td>
               <td>{$tab/category/text()}</td>
               <td>{$tab/label/text()}</td>
               <td>{$tab/tab-id/text()}</td>
               
               <td>
                  {attribute {'style'} { concat('background-color: ', $tab/color/text()) } }
               {$tab/color/text()}
               </td>
               
               <td>{$tab/default-visibility/text()}</td>
               
               <td>{string-join(
                     $tab/path/text()
                     , ', ')}
              </td>
            </tr>
      }</tbody>
      </table>
      
      <h2>Metrics</h2>
      Tabs: {count($doc/tab)}<br/>
      Default Visible Tabs: {count($doc/tab[default-visibility='show'])}<br/>
      Default Hidden Tabs: {count($doc/tab[default-visibility='hide'])}<br/>
      Level 1 Path References: {$path-count}<br/>
      
      <h2>Tabs Mockup</h2>
      
      {if ($show-all = 'true')
          then <a href="tab-report.xq?show-all=false">Show Default</a>
          else <a href="tab-report.xq?show-all=true">Show All</a>
      }
      <table class="tabs">
      <tr style="border:solid black 1px;">
      {
      for $category in $visible-categories
      let $cat-count := count( $doc//tab[category/text() = $category] )
      let $cat-def-count := count( $doc//tab[category/text() = $category and default-visibility/text() = 'show'] )
      let $colspan := if ($show-all = 'true') then $cat-count else $cat-def-count
      return
         if ( $cat-count > 0)
            then
              <td class="tab" text-align="center" style="text-align: center; border:solid black 1px;">
                    {attribute {'colspan'} {$colspan} }
                    {$category} ({$cat-count}, {$cat-def-count})
             </td>
         else ()
      }
       </tr>
       
       <tr>
         {
         for $category in $visible-categories
            return
               for $sub-tab in $doc//tab[category/text() = $category]
                  return
                       if ($sub-tab/default-visibility/text() = 'show' or $show-all = 'true')
                       then
                       <td class="tab" style="text-align: center; border:solid black 1px; background-color: {$sub-tab/color/text()};"
                       
                       >{ $sub-tab/label/text() }</td>
                       else ()
         }
        </tr>
      
       </table>
      
</div>

return style:assemble-page($title, $content)