xquery version "1.0";

import module namespace style = "http://www.danmccreary.com/library" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

let $title := 'MODS Record Editor'
let $new := request:get-parameter('new', '')
let $id := request:get-parameter('id', 'new')
let $tab := request:get-parameter('tab', '')
let $user := xmldb:get-current-user()
return
(: check for required parameters :)
if (not($new or $id))
    then (
    <error>
        <message>The parameters "new" and "id" are both missing. One of these two parameters is required for viewing this form.</message>
    </error>)
    else
    let $file := if ($new)
        then concat('get-instance.xq?new=true&amp;tab=', $tab)
        else concat('../views/get-instance.xq?id=', $id)

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
       <xf:instance xmlns="" id="code-tables" src="all-codes.xq"/>
       
       <xf:instance xmlns="" id="save-results">
          <data/>
       </xf:instance>
       
            
       <xf:submission id="save" method="post"
          ref="save-data"
          action="{if ($new='true') then ('save-new.xq') else ('update.xq')}" replace="instance"
          instance="save-results"/>
    </xf:model>

let $content :=
<div class="content">
Loading tab: {$tab}<br/>
Body Collection: {$body-collection}<br/>
{mods:tabs($tab, $id)}
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
    
    <a href="../views/get-instance.xq?id={$id}">View XML</a>
</div>

return style:assemble-form($title, attribute {'mods:dummy'} {'dummy'}, $style, $model, $content, true())
