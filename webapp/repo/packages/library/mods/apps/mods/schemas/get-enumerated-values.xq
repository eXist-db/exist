xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare namespace xs="http://www.w3.org/2001/XMLSchema";

let $title := 'Get Schema Enumerated Values'
let $file-path := concat($style:web-path-to-app, '/schemas/mods-3-4.xsd')
let $schema := doc($file-path)/xs:schema

let $content :=
<code-tables>
  <file>{$file-path}</file>
  { (: for all the simple types that have a restriction and an enumeration :)
    for $type in $schema//xs:simpleType[xs:restriction/xs:enumeration]
    return
    <code-table>
       <code-table-name>{string($type/@name)}</code-table-name>
       <items>{
          for $enum in $type/xs:restriction/xs:enumeration
          return
          <item>
             <label>{string($enum/@value)}</label>
             <value>{string($enum/@value)}</value>
          </item>
       }</items>
    </code-table>
  }
</code-tables>

return $content
