xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";
import module namespace config = "http://exist-db.org/mods/config" at "../config.xqm";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xforms="http://www.w3.org/2002/xforms";
declare namespace ev="http://www.w3.org/2001/xml-events";
declare namespace xlink="http://www.w3.org/1999/xlink";

declare function xf:get-temp-collection() {
    let $temp-collection := collection($config:mods-temp-collection)
    return
        $config:mods-temp-collection
};

let $title := 'MODS Record Editor'

(: Check if a host is specified for related item. :)
let $host := request:get-parameter('host', ())

(: Get the tab-id parameter. If no tab is specified, default to the compact-a tab. :)
let $tab-id := request:get-parameter('tab-id', 'compact-a')
(: Display the label attached to the tab to the user :)
let $tab-data := concat($style:db-path-to-app, '/edit/tab-data.xml')
let $bottom-tab-label := doc($tab-data)/tabs/tab[tab-id=$tab-id]/label 

(: If a document type is specified, then we will need to use that instance as the template. :)
let $record-id := request:get-parameter('id', '')
let $record-data := concat($style:db-path-to-app, '/temp/', $record-id,'.xml')

(: Get the type parameter which shows the record template. Get the relevant label and hint for this. :)
let $type-request := request:get-parameter('type', '')
let $type-data := concat($style:db-path-to-app, '/code-tables/document-type-codes.xml')
let $type-label := doc($type-data)//item[value = $type-request]/label
let $type-hint := doc($type-data)//item[value = $type-request]/hint

let $target-collection := request:get-parameter('collection', '')
let $temporary-collection := xf:get-temp-collection()

(: Get id parameter. Default to "new" if empty. :)
let $id-param := request:get-parameter('id', 'new')
(: Check to see if we have an id. :)
let $new-record := 
        if ($id-param = '' or $id-param = 'new')
        then true()
        else false()        
(: If we do not have an incoming ID or if the record is new, then create an ID with util:uuid(). :)
let $id :=
   if ($new-record)
   then concat("uuid-", util:uuid())
   else $id-param

(: If we are creating a new record, then we need to call get-instance.xq with new=true to tell it to get the entire template :)
let $create-new-from-template :=
   if ($new-record)
      then (
         (: Copy the template into data and store it with the ID as file name. :)
         let $template-path := concat($style:db-path-to-app, '/edit/instances/', $type-request, '.xml')
         let $template := doc($template-path)
         let $new-record-file-name := concat($id, '.xml')
         (: store it in the right location :)
         let $stored := xmldb:store($temporary-collection, $new-record-file-name, $template)
         
         (: Get the remaining parameters. :)
         let $languageOfResource := request:get-parameter("languageOfResource", "")
         let $scriptOfResource := request:get-parameter("scriptOfResource", "")
         let $transliterationOfResource := request:get-parameter("transliterationOfResource", "")
         let $languageOfCataloging := request:get-parameter("languageOfCataloging", "")
         let $scriptOfCataloging := request:get-parameter("scriptOfCataloging", "")
         let $scriptTypeOfResource := doc("/db/org/library/apps/mods/code-tables/language-3-type-codes.xml")/code-table/items/item[value = $languageOfResource]/data(scriptClassifier)
         let $scriptTypeOfCataloging := doc("/db/org/library/apps/mods/code-tables/language-3-type-codes.xml")/code-table/items/item[value = $languageOfCataloging]/data(scriptClassifier)
         
         let $doc := doc($stored)
         
         (: Note that we can not use "update replace" if we want to keep the default namespace. :)
         return (
            (: Update record with ID attribute. :)
            update value $doc/mods:mods/@ID with $id
            ,
            (: Save language and script of resource. :)
            let $language-insert:=
                <mods:language>
                    <mods:languageTerm authority="iso639-2b" type="code">
                        {$languageOfResource}
                    </mods:languageTerm>
                    <mods:scriptTerm authority="iso15924" type="code">
                        {$scriptOfResource}
                    </mods:scriptTerm>
                </mods:language>
            return
            update insert $language-insert into $doc/mods:mods
            ,
            (: Save creation date and language and script of cataloguing :)
            let $recordInfo-insert:=
                <mods:recordInfo lang="eng" script="Latn">
                    <mods:recordContentSource authority="marcorg">DE-16-158</mods:recordContentSource>
                    <mods:recordCreationDate encoding="w3cdtf">
                        {current-date()}
                    </mods:recordCreationDate>
                    <mods:recordChangeDate encoding="w3cdtf"/>
                    <mods:languageOfCataloging>
                        <mods:languageTerm authority="iso639-2b" type="code">
                            {$languageOfCataloging}
                        </mods:languageTerm>
                        <mods:scriptTerm authority="iso15924" type="code">
                            {$scriptOfCataloging}
                    </mods:scriptTerm>
                    </mods:languageOfCataloging>
                </mods:recordInfo>            
            return
            update insert $recordInfo-insert into $doc/mods:mods
            ,
            (: Save name of user collection, name of template used, script type and transliteration scheme used into mods:extension. :)
            update insert
                <extension xmlns="http://www.loc.gov/mods/v3" xmlns:e="http://www.asia-europe.uni-heidelberg.de/">
                    <e:collection>{$target-collection}</e:collection>
                    <e:template>{$type-request}</e:template>
                    <e:scriptTypeOfResource>{$scriptTypeOfResource}</e:scriptTypeOfResource>
                    <e:scriptTypeOfCataloging>{$scriptTypeOfCataloging}</e:scriptTypeOfCataloging>
                    <e:transliterationOfResource>{$transliterationOfResource}</e:transliterationOfResource>                    
                </extension>
            into $doc/mods:mods
            ,
            if ($host) 
            then 
            (
                update value doc($stored)/mods:mods/mods:relatedItem/@xlink with $host
                ,
                update value doc($stored)/mods:mods/mods:relatedItem/@type with "host"
            )
            else ()
         )
      ) else 
            if (not(doc-available(concat($temporary-collection, '/', $id, '.xml')))) 
            then xmldb:copy($target-collection, $temporary-collection, concat($id, '.xml'))
            else ()

(: this is the string we pass to instance id='save-data' src attribute :)
let $instance-src :=  
    concat('get-instance.xq?tab-id=', $tab-id, '&amp;id=', $id, '&amp;data=', $temporary-collection)

let $user := xmldb:get-current-user()

let $body-collection := concat($style:db-path-to-app, '/edit/body')

(: This is the part of the form that belongs to the tab called. :)
let $form-body := collection($body-collection)/div[@tab-id = $tab-id]

let $style :=
<style type="text/css"><![CDATA[
@namespace xf url(http://www.w3.org/2002/xforms);]]>
</style>

let $model :=
    <xf:model>
       
       <xf:instance xmlns="http://www.loc.gov/mods/v3" src="{$instance-src}" id="save-data"/>
       
       (: Not used. The more or less full embodiment of the MODS schema, 3.3-3.4. :)
       (: <xf:instance xmlns="http://www.loc.gov/mods/v3" src="insert-templates.xml" id='insert-templates' readonly="true"/>:)
       
       (: Not used. A selection of elements and attributes from the MODS schema used for default records. :)
       (: <xf:instance xmlns="http://www.loc.gov/mods/v3" src="new-instance.xml" id='new-instance' readonly="true"/>:)

       (: Elements for the compact forms. :)
       <xf:instance xmlns="http://www.loc.gov/mods/v3" src="compact-template.xml" id='compact-template' readonly="true"/> 
       
       <xf:instance xmlns="" id="code-tables" src="codes-for-tab.xq?tab-id={$tab-id}" readonly="true"/>
       
       <!-- a title should ideally speaking be required, but having this bind will prevent a tab from being saved when clicking on another tab, if the user has not input a title.--> 
       <!--
       <xf:bind nodeset="instance('save-data')/mods:titleInfo/mods:title" required="true()"/>       
       -->
       
       <xf:submission id="save-submission" method="post"
          ref="instance('save-data')"
          action="save.xq?collection={$temporary-collection}&amp;action=save" replace="instance"
          instance="save-results">
       </xf:submission>
       
       <xf:submission id="save-and-close-submission" method="post"
          ref="instance('save-data')"
          action="save.xq?collection={$temporary-collection}&amp;action=close" replace="instance"
          instance="save-results">
       </xf:submission>
       
       <xf:submission id="cancel-submission" method="post"
          ref="instance('save-data')"
          action="save.xq?collection={$temporary-collection}&amp;action=cancel" replace="instance"
          instance="save-results">
       </xf:submission>

</xf:model>

let $content :=
<div class="content">
    <span class="float-right">
    {
    if ($type-request) then
    ('Editing record of type '
    , 
    <strong>{$type-label}</strong>
    ,
    if ($type-hint) then
    <span class="xforms-hint">
    <span onmouseover="show(this, 'hint', true)" onmouseout="show(this, 'hint', false)" class="xforms-hint-icon"/>
        <div class="xforms-hint-value">
            {$type-hint}
        </div>
    </span>
    else ()
    )
    else
        'Editing record'
    }
        
    with the title<strong><xf:output value="./mods:titleInfo/mods:title"/></strong>,
    on the <strong>{$bottom-tab-label}</strong> tab,
    to be saved in <strong>{$target-collection}</strong>.
    </span>
    
    <!--Here values are passed to the URL.-->
    {mods:tabs($tab-id, $id, 1 (: NB: Not used. :), $target-collection)}

<div class="save-buttons">    
    <xf:submit submission="save-submission">
        <xf:label class="xforms-group-label-centered-general">&#160;Save</xf:label>
    </xf:submit>
    
    <xf:trigger>
        <xf:label class="xforms-group-label-centered-general">&#160;Save and Close</xf:label>
        <xf:action ev:event="DOMActivate">
            <xf:send submission="save-and-close-submission"/>
            <xf:load resource="../search/index.xml?reload=true" show="replace"/>
        </xf:action>
    </xf:trigger>
    
    <xf:trigger>
        <xf:label class="xforms-group-label-centered-general">&#160;Cancel Editing</xf:label>
        <xf:action ev:event="DOMActivate">
            <xf:send submission="cancel-submission"/>
            <xf:load resource="../search/index.xml?reload=true" show="replace"/>
        </xf:action>
     </xf:trigger>
    
    <span class="xforms-hint">
    <span onmouseover="show(this, 'hint', true)" onmouseout="show(this, 'hint', false)" class="xforms-hint-icon"/>
    <div class="xforms-hint-value">
        <p>Every time you click one of the lower tabs, your input is saved. Your input is also saved if you click the current tab. For this reason, there is usually no need to click the &quot;Save&quot; button. </p>
        <p>If you plan to be away from your computer for some time, you can save what you have input so far by clicking the &quot;Save&quot; button. Be aware, however, that you are only logged in for a certain period of time and when your session times out, what you have input cannot be retrieved. If you know that you may not be able to finish a record due to some disturbance, it is best to click &quot;Save and Close&quot; and return to finish the record later.</p>
        <p>When you have finished editing, click the &quot;Save and Close&quot; button. The record is then saved inside the folder you marked before opening the editor.</p>
        <p>After you have closed the editor, you can continue editing the record by finding it and clicking the &quot;Edit Record&quot; button inside the record&apos;s detail view.</p>
        <p>If you wish to discard what you have input and return to the search function, click &quot;Cancel Editing&quot;.</p>
    </div>
    </span>
</div>
    
    <!-- Import the correct form body for the tab called. -->
    {$form-body}
    
    
    <br/>
    <xf:submit submission="save-submission">
        <xf:label class="xforms-group-label-centered-general">Save</xf:label>
    </xf:submit>
    
    <!--
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
    -->
    <!--
    <a href="get-instance.xq?id={$id}&amp;data={$data-collection}">View XML for the whole MODS record</a> -
    <a href="get-instance.xq?id={$id}&amp;tab-id={$tab-id}&amp;data={$data-collection}">View XML for the current tab</a>
    -->
</div>

return style:assemble-form('', attribute {'mods:dummy'} {'dummy'}, $style, $model, $content, false())
