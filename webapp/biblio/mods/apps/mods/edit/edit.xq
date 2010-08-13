xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

let $title := 'MODS Record Editor'
let $new := request:get-parameter('new', '')
let $id := request:get-parameter('id', 'new')
let $show-all-string := request:get-parameter('show-all', 'false')
let $show-all := if ($show-all-string = 'true') then true() else false()

(: if no tab is specified, we default to the title tab :)
let $tab := request:get-parameter('tab-id', 'title')
let $user := xmldb:get-current-user()
return

(: check for required parameters :)
let $file := if ($id = '' or $id = 'new')
        then concat('get-instance.xq?new=true&amp;tab-id=', $tab)
        else concat('get-instance.xq?id=', $id, '&amp;tab-id=', $tab)

let $body-collection := concat($style:db-path-to-app, '/edit/body')
let $form-body := collection($body-collection)/div[@class = $tab]

let $style :=
<style type="text/css"><![CDATA[
@namespace xf url(http://www.w3.org/2002/xforms);

.textarea textarea {
    height: 5em;
    width: 900px;
    }
]]>
</style>

let $model :=
    <xf:model>
       <xf:instance xmlns="http://www.loc.gov/mods/v3" src="{$file}" id="save-data"/>
       <xf:instance xmlns="http://www.loc.gov/mods/v3" src="insert-templates.xml" id='insert-templates'/>
       
       <xf:instance xmlns="" id="code-tables" src="codes-for-tab.xq?tab-id={$tab}"/>
       
       <xf:instance xmlns="" id="save-results">
          <data/>
       </xf:instance>
                  
       <xf:submission id="save" method="post"
          ref="save-data"
          action="{if ($new='true') then ('save-new.xq') else ('update.xq')}" replace="instance"
          instance="save-results">
       </xf:submission>
    </xf:model>

let $content :=
<div class="content">
    Loading tab: {$tab}<br/>
    Body Collection: {$body-collection}<br/>
    
    {mods:tabs($tab, $id, $show-all)}
    
    Body Elements = {count($form-body/*/*)}<br/>
    
    {$form-body}
    
    <br/>
    <xf:submit submission="save">
        <xf:label>Save</xf:label>
    </xf:submit>
    
    <div class="debug">
        <xf:output value="count(instance('save-data')/*)">
           <xf:label>Root Element Count: </xf:label>
           
        </xf:output>
    </div>
    
    <a href="get-instance.xq?id={$id}">View XML</a>
</div>

return style:assemble-form($title, attribute {'mods:dummy'} {'dummy'}, $style, $model, $content, true())
