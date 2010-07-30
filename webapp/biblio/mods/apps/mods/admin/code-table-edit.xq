xquery version "1.0";
import module namespace style = "http://www.danmccreary.com/library" at "../../../modules/style.xqm";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";
(: Code Table Editor :)

let $title := 'Code Table Editor'
let $code-table := request:get-parameter('id', '')

let $data-collection := concat($style:db-path-to-app, '/code-tables')
let $code-tables := collection($data-collection)/code-table
let $id := request:get-parameter('id', '')
let $new := request:get-parameter('new', '')

let $file :=
if ($new = 'true')
   then 'new-instance'
   else concat('get-instance.xq?id=', $id)

let $model :=
<xf:model>
    <xf:instance xmlns="" id="my-instance" src="{$file}"/>
    <xf:submission id="save" method="post" action="{if ($new='true') then ('save-new-code-table.xq') else ('update-code-table.xq')}" replace="all"/>
</xf:model>

let $content :=
<div class="content">

    <div class="block-form">
     <xf:input ref="code-table-name">
        <xf:label>Code Table Name (id)</xf:label>
     </xf:input>
     <br/>
     <xf:input ref="code-table-name">
        <xf:label>Code Table Describtion</xf:label>
     </xf:input>
     <br/>
     <xf:input ref="status">
        <xf:label>Status</xf:label>
     </xf:input>
     <br/>
     <xf:select1 ref="status">
        <xf:label>Status</xf:label>
        <xf:item>
          <xf:label>Draft</xf:label>
          <xf:value>draft</xf:value>
        </xf:item>
        <xf:item>
          <xf:label>Review</xf:label>
          <xf:value>review</xf:value>
        </xf:item>
        <xf:item>
          <xf:label>Finished</xf:label>
          <xf:value>finished</xf:value>
        </xf:item>
        <xf:item>
          <xf:label>Dummy - ignore</xf:label>
          <xf:value>dummy</xf:value>
        </xf:item>
     </xf:select1>
    </div>
    
   <xf:input ref="code-table-name">
       <xf:label>Data Source</xf:label>
    </xf:input>

    <h1>Code Table Edit</h1>

        <table>
            <thead>
                <tr>
                    <th class="Label">Label</th>
                    <th class="Value">Value</th>
                    <th class="Description">Classifier Description</th>
                </tr>
            </thead>
        </table>
        <br/>
        <xf:repeat nodeset="instance('my-instance')/items/item" id="item-repeat">
            <xf:input ref="label" class="Label inline-control"/>
            <xf:input ref="value" class="Value inline-control"/>
            <xf:input ref="description" class="Description inline-control"/>
            <xf:trigger>
                <xf:label>Delete Item</xf:label>
                <xf:delete nodeset="instance('my-instance')/items/item[index('item-repeat')]" ev:event="DOMActivate"/>
            </xf:trigger>
        </xf:repeat>

    <xf:trigger>
        <xf:label>Add New Item</xf:label>
        <xf:action ev:event="DOMActivate">
            <xf:insert nodeset="instance('my-instance')/items/item" at="last()" position="after"/>
            <!-- set the value to the item count -->
            <xf:setvalue ref="instance('my-instance')/items/item[last()]/label" value=""/>
            <xf:setvalue ref="instance('my-instance')/items/item[last()]/value" value=""/>
            <xf:setvalue ref="instance('my-instance')/items/item[last()]/description" value=""/>
        </xf:action>
    </xf:trigger>
    <xf:trigger>
        <xf:label>Delete Item</xf:label>
        <xf:delete nodeset="instance('my-instance')/items/item[index('item-repeat')]" ev:event="DOMActivate"/>
    </xf:trigger>
    <br/>
    <xf:submit submission="save">
        <xf:label>Save</xf:label>
    </xf:submit>
</div>

return style:assemble-form($title, (), (), $model, $content, true())
