xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";

declare function local:name-value-to-label($value as xs:string*) as xs:string {
   let $lookup-table-path := concat($style:db-path-to-app, '/code-tables/element-attribute-names.xml')
   let $items := doc($lookup-table-path)//item
   let $label := xs:string($items[value=$value]/label/text())
   return
     if ($label) then $label else $value
};

let $lookup-table-path := concat($style:db-path-to-app, '/code-tables/element-attribute-names.xml')
let $items := doc($lookup-table-path)//item

return
<results>{
for $item in $items
return
  <label>{ local:name-value-to-label($item/value/text()) }</label>
}</results>