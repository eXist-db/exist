xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";

let $title := 'MODS Record Editor'

(: get REST URL parameters :)
let $id-param := request:get-parameter('id', 'new')
let $show-all-string := request:get-parameter('show-all', 'false')
let $show-all := if ($show-all-string = 'true') then true() else false()

(: if no tab is specified, we default to the title tab :)
let $tab-id := request:get-parameter('tab-id', 'title')

(: check to see if we have a  :)
let $new := if ($id-param = '' or $id-param = 'new')
        then true()
        else false()
        
(: if we do not have an incomming ID or it the ID is new then create one to use 
   Note that for testing you can use the first five chars of the UUID substring(util:uuid(), 1, 5)
:)
let $id :=
   if ($new)
        then util:uuid()
        else $id-param

(: if we are creating a new record then we need to call get-instance.xq with new=true to tell it to get the entire template :)
let $instance-src :=
   if ($new)
      then (
         (: copy the template into data and update it with a new UUID :)
         let $template-path := concat($style:db-path-to-app, '/edit/new-instance.xml')
         let $template := doc($template-path)
         let $new-file-name := concat($id, '.xml')
         (: uncomment the following line in for testing if you are not admin :)
         let $login := xmldb:login($style:db-path-to-app-data, 'admin', 'admin123')
         let $store := xmldb:store($style:db-path-to-app-data, $new-file-name, $template)
         let $new-file-path := concat($style:db-path-to-app-data, '/', $new-file-name)
         
         (: note that we can not use "update replace" if we want to keep the default namespace :)
         let $update-id := update value doc($new-file-path)/mods:mods/@ID with $id
         return concat('../data/', $id, '.xml')
         )
      else concat('get-instance.xq?tab-id=', $tab-id, '&amp;id=', $id)

let $user := xmldb:get-current-user()

let $body-collection := concat($style:db-path-to-app, '/edit/body')

(: this is the part of the form that we need for this tab :)
let $form-body := collection($body-collection)/div[@tab-id = $tab-id]

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
       <xf:instance xmlns="http://www.loc.gov/mods/v3" src="{$instance-src}" id="save-data"/>

       <xf:instance xmlns="http://www.loc.gov/mods/v3" src="insert-templates.xml" id='insert-templates'/>
       
       <xf:instance xmlns="" id="code-tables" src="codes-for-tab.xq?tab-id={$tab-id}"/>
       
       <xf:instance xmlns="" id="save-results">
          <data>
             <message>Form loaded OK.</message>
          </data>
       </xf:instance>
                  
       <xf:submission id="save-submission" method="post"
          ref="instance('save-data')"
          action="save.xq" replace="instance"
          instance="save-results">
       </xf:submission>
       
       <xf:submission id="echo-post-submission" method="post"
          ref="instance('save-data')"
          action="echo-post.xq" replace="all">
       </xf:submission>
       
    </xf:model>

let $content :=
<div class="content">
    
    <a href="../index.xq">Home</a> id: {$id} tab: {$tab-id}<br/>
    
    {mods:tabs($tab-id, $id, $show-all)}
    
    Body Elements = {count($form-body/*/*)}<br/>
    
    <xf:submit submission="save-submission">
        <xf:label>Save</xf:label>
    </xf:submit>
    
    <!-- import the correct form body for this tab -->
    {$form-body}
    
    <br/>
    <xf:submit submission="save-submission">
        <xf:label>Save</xf:label>
    </xf:submit>

    <xf:submit submission="echo-post-submission">
        <xf:label>Echo Post (no save)</xf:label>
    </xf:submit>
    
    <div class="debug">
        <xf:output value="count(instance('save-data')/*)">
           <xf:label>Root Element Count: </xf:label>
        </xf:output>
        <br/>
        <xf:output ref="instance('save-results')//message ">
           <xf:label>Message: </xf:label>
        </xf:output>
        
        <xf:output ref="instance('save-results')//mods:message ">
           <xf:label>MODS Message: </xf:label>
        </xf:output>
    </div>
    
    <a href="../views/get-instance.xq?id={$id}">View FUll XML</a> -
    <a href="get-instance.xq?id={$id}&amp;tab-id={$tab-id}">View Tab XML</a>
</div>

return style:assemble-form($title, attribute {'mods:dummy'} {'dummy'}, $style, $model, $content, true())
