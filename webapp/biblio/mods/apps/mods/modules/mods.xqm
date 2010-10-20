xquery version "1.0";

module namespace mods = "http://www.loc.gov/mods/v3";

declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

declare variable $mods:tabs-file := '/db/org/library/apps/mods/edit/tab-data.xml';
declare variable $mods:body-file-collection := '/db/org/library/apps/mods/edit/body';
declare variable $mods:code-table-collection := '/db/org/library/apps/mods/code-tables';

(: Display all the tabs in a div using triggers. :)
declare function mods:simple-tabs($tab-id as xs:string, $id as xs:string) as node()  {

    (: we get a sequence of tab records from the tab database :)
    let $tabs-data := doc($mods:tabs-file)/tabs/tab
    
    return
    <div class="tabs">{
       for $tab in $tabs-data
       return
         <xf:trigger appearance="minimal" class="{$tab/tab-id/text()} {if ($tab-id = $tab/tab-id/text()) then 'selected' else()}">
            <xf:label>{$tab/label/text()}</xf:label>
            <xf:action ev:event="DOMActivate">
                <xf:send submission="save-submission"/>
                <xf:load resource="edit.xq?tab-id={$tab/tab-id/text()}&amp;id={$id}" show="replace"/>
            </xf:action>
         </xf:trigger>
    }</div>
};

(: Display all the tabs in a div using triggers. :)
declare function mods:tabs($tab-id as xs:string, $id as xs:string, $show-all as xs:boolean, $data-collection as xs:string) as node()  {

    (: we get a sequence of tab records from the tab database :)
    let $tabs-data := doc($mods:tabs-file)/tabs/tab
    
    (: get a list of all the categories that have visible tabs :)
    let $all-categories := distinct-values($tabs-data/category/text())
    
   (: only get categories that have at least one visible sub-category :)
    let $visible-categories :=
       if ($show-all)
        then $all-categories
        else 
        for $category in $all-categories
            let $count-of-visible-subcategories :=
                count( $tabs-data[category/text() = $category and default-visibility/text() = 'show'] ) 
            return 
               if ($count-of-visible-subcategories > 0)
                 then $category
                 else ()
    
    (: note that in the submission below that the show-all= URL param is toggled :)
    
    return
<div class="tabs">
    
    <xf:trigger class="link">
        <xf:label class="xforms-group-label-centered-general">
           {if ($show-all)
              then 'Show Default Tabs'
              else 'Show All Tabs'
           }
        </xf:label><br/>
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save-submission"></xf:send>
            <xf:load resource="edit.xq?tab-id={$tab-id}&amp;id={$id}&amp;show-all={not($show-all)}&amp;collection={$data-collection}" show="replace">
            </xf:load>
        </xf:action>
    </xf:trigger>

      
    <table class="tabs">
       <tr>{
            for $category in $visible-categories
            let $cat-count := count( $tabs-data[category/text() = $category] )
            let $cat-def-count := count( $tabs-data[category/text() = $category and default-visibility/text() = 'show'] )
            let $colspan := if ($show-all) then $cat-count else $cat-def-count
            return
               if ( $cat-count > 0)
                  then
                    <td class="tab" style="text-align: center;">
                          {attribute {'colspan'} {$colspan} }
                          {$category}
                   </td>
               else ()
       }</tr>
       
        <tr>{
           for $tab in $tabs-data
           return
           if ($tab/default-visibility/text() = 'show' or $show-all)
              then
           <td  style="background-color:{$tab/color/text()};">
             <xf:trigger
                appearance="minimal"
                class="{$tab/tab-id/text()} {if ($tab-id = $tab/tab-id/text()) then 'selected' else()}"
             >
                <xf:label>{$tab/label/text()}</xf:label>
                <xf:action ev:event="DOMActivate">
                    <xf:send submission="save-submission"/>
                    <xf:load resource="edit.xq?tab-id={$tab/tab-id/text()}&amp;id={$id}&amp;show-all={$show-all}&amp;collection={$data-collection}" show="replace"/>
                </xf:action>
             </xf:trigger>
           </td>
           else ()
       }</tr>
    </table>
</div>
};

(: Display all the tabs in a table using triggers. :)
declare function mods:tabs-table($selected-tab-id as xs:string, $show-all as xs:boolean) as node()  {

    let $tabs-data := doc($mods:tabs-file)/tabs/tab
    
    (: get a list of all the categories that have visible tabs :)
    let $all-categories := distinct-values($tabs-data/category/text())
    
    (: only get categories that have at least one visible sub-category :)
    let $visible-categories :=
       if ($show-all)
        then $all-categories
        else 
        for $category in $all-categories
            let $count-of-visible-subcategories :=
                count( $tabs-data[category/text() = $category and default-visibility/text() = 'show'] ) 
            return 
               if ($count-of-visible-subcategories > 0)
                 then $category
                 else ()
    
    return
    <div class="tabs-table">
       {if ($show-all)
              then <a href="tab-report.xq?show-all=false">Show Default</a>
              else <a href="tab-report.xq?show-all=true">Show All</a>
        }
          <table class="tabs">
          <tr>{
            for $category in $visible-categories
            let $cat-count := count( $tabs-data[category/text() = $category] )
            let $cat-def-count := count( $tabs-data[category/text() = $category and default-visibility/text() = 'show'] )
            let $colspan := if ($show-all) then $cat-count else $cat-def-count
            return
               if ( $cat-count > 0)
                  then
                    <td class="tab" text-align="center" style="text-align: center;">
                          {attribute {'colspan'} {$colspan} }
                          {$category} ({$cat-count}, {$cat-def-count})
                   </td>
               else ()
          }</tr>
           
           <tr>{
             for $category in $visible-categories
                return
                   for $sub-tab in $tabs-data[category/text() = $category]
                      return
                           if ($sub-tab/default-visibility/text() = 'show' or $show-all)
                           then
                           <td class="tab" style="text-align: center; border:solid black 1px; background-color: {$sub-tab/color/text()};"
                           
                           ><a href="../edit/edit.xq?tab-id={$sub-tab/tab-id/text()}&amp;id=new">{$sub-tab/label/text()}</a></td>
                           else ()
           }</tr>
          
           </table>
     </div>
};

(: given a specific code table name, list all the tabs that use this code table :)
declare function mods:tabs-for-code-table($code-table-name as xs:string) {
let $itemsets := collection($mods:body-file-collection)//xf:itemset[contains(@nodeset, $code-table-name)]
return
  if ($itemsets)
   then
    <ol>
    {for $itemset in $itemsets
       return
       
         <li>Tab: {substring-before(util:document-name($itemset), '.xml')} Label: {substring-before($itemset/../*:label/text(), ':') }</li>
    }
    </ol>
   else
      <div><br/><span class="error">Code Table Not Referenced</span><br/></div>

};