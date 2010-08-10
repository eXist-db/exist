xquery version "1.0";

module namespace mods = "http://www.loc.gov/mods/v3";

declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

declare variable $mods:tabs-file := '/db/org/library/apps/mods/edit/tab-data.xml';

(: tab bar for multi-part forms :)

(: you just pass in the tab id you are using

Call like this:
  mods:tabs(3)
  
Where 3 is the tab number.
:)


declare function mods:tabs($tab-id as xs:string, $id as xs:string) as node()  {

    (: we get a sequence of tab records from the tab database :)
    let $tabs-data := doc($mods:tabs-file)/tabs/tab
    
    return
    <div class="tabs">{
       for $tab in $tabs-data
       return
         <xf:trigger appearance="minimal" class="{$tab/tab-id/text()} {if ($tab-id = $tab/tab-id/text()) then 'selected' else()}">
            <xf:label>{$tab/label/text()}</xf:label>
            <xf:action ev:event="DOMActivate">
                <xf:send submission="save"/>
                <xf:load resource="edit.xq?tab={$tab/tab-id/text()}&amp;id={$id}" show="replace"/>
            </xf:action>
         </xf:trigger>
    }</div>
};
