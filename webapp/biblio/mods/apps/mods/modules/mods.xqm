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
declare function mods:tabs1($tab-num as xs:integer) as node()  {
<div class="tabs">

    <xf:trigger appearance="minimal" class="tab1 {if ($tab-num = 1) then 'selected' else()}">
        <xf:label>Title</xf:label>
        {if ($tab-num = 1) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-1.xq" show="replace"/>
        </xf:action>}
    </xf:trigger>
    
     <xf:trigger appearance="minimal" class="tab2 {if ($tab-num = 2) then 'selected' else()}">
        <xf:label>Origin</xf:label>
        {if ($tab-num = 2) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-2.xq" show="replace"/>
        </xf:action>}
    </xf:trigger>

     <xf:trigger appearance="minimal" class="tab3 {if ($tab-num = 3) then 'selected' else()}">
        <xf:label>Names</xf:label>
        {if ($tab-num = 3) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-3.xq" show="replace"/>
        </xf:action>}
    </xf:trigger>
    
    <xf:trigger appearance="minimal" class="tab2  {if ($tab-num = 2) then 'selected' else()}">
        <xf:label>Origin </xf:label>
        {if ($tab-num = 2) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-2.xq" show="replace"/>
        </xf:action>
        }
    </xf:trigger>
    
    
    <xf:trigger appearance="minimal" class="tab3 {if ($tab-num = 3) then 'selected' else()}">
        <xf:label>Contents</xf:label>
        {if ($tab-num = 3) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-3.xq" show="replace"/>
        </xf:action>
        }
    </xf:trigger>
    <xf:trigger appearance="minimal" class="tab4 {if ($tab-num = 4) then 'selected' else()}">
        <xf:label>Relationships</xf:label>
        {if ($tab-num = 4) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-4.xq" show="replace"/>
        </xf:action>
        }
    </xf:trigger>
    <xf:trigger appearance="minimal" class="tab5 {if ($tab-num = 5) then 'selected' else()}">
        <xf:label>Administrative</xf:label>
        {if ($tab-num = 5) then () else
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save"/>
            <xf:load resource="edit-5.xq" show="replace"/>
        </xf:action>
        }
    </xf:trigger>
</div>
};

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
