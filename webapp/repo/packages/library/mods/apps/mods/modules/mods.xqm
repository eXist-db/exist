xquery version "1.0";

module namespace mods = "http://www.loc.gov/mods/v3";

declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

declare variable $mods:tabs-file := '/db/org/library/apps/mods/edit/tab-data.xml';
declare variable $mods:body-file-collection := '/db/org/library/apps/mods/edit/body';
declare variable $mods:code-table-collection := '/db/org/library/apps/mods/code-tables';


(: Display the tabs in a div using triggers that hide or show sub-tabs and tabs. :)
declare function mods:tabs($tab-id as xs:string, $id as xs:string, $show-level as xs:integer, $data-collection as xs:string) as node()  {

(: get the show-level param from the URL; if empty, set it to 1. :)
let $show-level := xs:integer(request:get-parameter("show-level", 1))
let $type := request:get-parameter("type", '')

(: get a sequence of tab entries from the tab database :)
let $tabs-data := doc($mods:tabs-file)/tabs/tab

(: get a sequence of all the top tabs :)
let $all-categories := distinct-values($tabs-data/category/text())

(: get top tabs that have at least one visible sub-tab :)
let $visible-categories :=
    if ($show-level = 3) 
    then
        for $category in $all-categories
        let $count-of-visible-subcategories := count($tabs-data[category/text() = $category and show-level = $show-level]) 
            return 
                if ($count-of-visible-subcategories > 0)
                then $category
                else ()
    else 
        if ($show-level = 2) 
        then
            for $category in $all-categories
            let $count-of-visible-subcategories := count($tabs-data[category/text() = $category and show-level = $show-level]) 
                return 
                    if ($count-of-visible-subcategories > 0)
                    then $category
                    else ()
        else 
           for $category in $all-categories
           let $count-of-visible-subcategories :=
               count($tabs-data[category/text() = $category and show-level = $show-level]) 
               return 
                   if ($count-of-visible-subcategories > 0)
                   then $category
                   else ()

(: note that in the submission below the show-level progresses from 1 to 2 to 3 to 1 through the URL. :)

let $show-level-1-subtab := $tabs-data[show-level = 1][1]/tab-id
let $show-level-2-subtab := $tabs-data[show-level = 2][1]/tab-id
let $show-level-3-subtab := $tabs-data[show-level = 3][1]/tab-id
let $show-level-4-subtab := $tabs-data[show-level = 4][1]/tab-id

return
<div class="tabs">
    <xf:trigger class="toggle-button">
        <xf:label class="xforms-group-label-centered-general">
            {
            (: set the label for the next show-level. :)
            if ($show-level = 1)
            then 'Show Citation Forms'
            else 
                if ($show-level = 2)
                then 'Show Content Description Forms'
                else
                    if ($show-level = 3)
                    then 'Show Remaining Forms'
                    else 'Show Basic Input Forms'
            }
        </xf:label>
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save-submission"></xf:send>
            <!--When clicking on the show-level button, circle the show-level values through 1, 2, and 3, and select the first sub-tab for each show-level. -->
            <xf:load resource="edit.xq?tab-id={
            if ($show-level = 1)
                then $show-level-2-subtab
                else 
                    if ($show-level = 2)
                    then $show-level-3-subtab
                    else
                        if ($show-level = 3)
                        then $show-level-4-subtab
                        else $show-level-1-subtab
            }&amp;id={$id}&amp;show-level={
                if ($show-level = 1)
                then 2
                else 
                    if ($show-level = 2)
                    then 3
                    else 
                        if ($show-level = 3)
                        then 4
                        else 1
                }&amp;type={$type}&amp;collection={$data-collection}" show="replace">
            </xf:load>
        </xf:action>
    </xf:trigger>
    
    <table class="tabs">
        <tr>
            {
            for $category in $visible-categories
            let $category-count := count($tabs-data[category/text() = $category])
            let $colspan := count($tabs-data[category/text() = $category and show-level = $show-level])
            return
            if ($category-count > 0)
            then
            <td style="{if ($tabs-data[category = $category]/tab-id = $tab-id) then "background:white" else "background:#EDEDED"}">
                {attribute{'colspan'}{$colspan}}
                <span class="tab-text">{$category}</span>
            </td>
            else ()
            }
            </tr>            
            {
            <tr>
            <td style="height:.1em;border:0;margin:0" colspan="{count($tabs-data[show-level = $show-level])}"> 
            </td>
            </tr>
            }
            
            <tr>
            {
            for $tab in $tabs-data[show-level = $show-level]
            return
            <td style="{if ($tab-id = $tab/tab-id/text()) then "background:white;border-bottom-color:white;color:#3681B3;" else "background:#EDEDED"}">
                <xf:trigger appearance="minimal">
                    <xf:label><div class="label" style="{if ($tab-id = $tab/tab-id/text()) then "color:#3681B3;font-weight:bold;" else "color:gray"}">{$tab/label/text()}</div></xf:label>
                    <xf:action ev:event="DOMActivate">
                        <xf:send submission="save-submission"/>
                        <!--When clicking on the sub-tabs, keep the show-level the same. -->
                        <xf:load resource="edit.xq?tab-id={$tab/tab-id/text()}&amp;id={$id}&amp;show-level={$show-level}&amp;type={$type}&amp;collection={$data-collection}" show="replace"/>
                    </xf:action>
                </xf:trigger>
            </td>
            }
            </tr>
        
    </table>
</div>
};