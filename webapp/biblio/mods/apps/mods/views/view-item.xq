xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace html = "http://www.w3.org/1999/xhtml";

declare function local:name-value-to-label($value as xs:string*) as xs:string {
   let $lookup-table-path := concat($style:db-path-to-app, '/code-tables/element-attribute-names.xml')
   let $items := doc($lookup-table-path)//item
   let $label := xs:string($items[value=$value]/label/text())
   return
     if ($label) then $label else $value
};

declare function local:element-to-nested-table($element, $depth as xs:integer) {
   if (exists ($element/(@*|*)))
     then 
     <table border="1">
     {if ($depth=1)
         then attribute {'class'} {concat('span-', 23-$depth)}
         else attribute {'class'} {concat('span-', '10')} }
        {if (exists($element/text()))
         then <tr class="text">
                  <th></th>
                  <td>{$element/text()}</td>
              </tr>
         else ()
       }
       {for $attribute in $element/@*
       return
          <tr class="attribute" >
             <th>{local:name-value-to-label(name($attribute))}</th>
             <td>{string($attribute)}</td>
          </tr>
       }
       {for $node in $element/*
       return 
          <tr>
          {if ($depth) then attribute {'class'} {concat('element span-', 4-$depth)} else () }
             <th>{local:name-value-to-label(name($node))}</th> 
             <td>{local:element-to-nested-table($node, $depth+1)}</td>
          </tr>       
        }
    </table>
    else
       if (starts-with($element/text(), 'http://'))
          then <a href="{$element/text()}">{$element/text()}</a>
          else $element/text()
  };

let $style :=
<style type="text/css"><![CDATA[
  table {border: solid black 1px;}
]]></style>

let $id := request:get-parameter('id', '')

let $page-title := 'View Mods Record'

let $edit-controls :=
   <div class="edit-controls">
      <a href="../edit/edit.xq?id={$id}">Edit</a>
      <a href="../edit/delete-confirm.xq?id={$id}">Delete</a>
      <a href="../views/get-instance.xq?id={$id}">XML</a>
   </div>

(: check for required parameters :)
return

if (not($id))
    then (
    <error>
        <message>Parameter "id" is missing.  This argument is required for this web service.</message>
    </error>)
    else
      let $app-collection := $style:db-path-to-app
      let $data-collection := concat($app-collection, '/data')
return

let $content := 
    <div class="content">
    {$edit-controls}
   {let $item := collection($data-collection)//mods:mods[@ID = $id]
      return
        local:element-to-nested-table($item, 1)
   }
   {$edit-controls}
   </div>
   
return style:assemble-page($page-title, $style, $content)