xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
import module namespace mods = "http://www.loc.gov/mods/v3" at "../modules/mods.xqm";

let $title := 'MODS Tabs Table Test'

let $content :=
<div class="content">
   {mods:tabs-table('title', true()) }
</div>

return style:assemble-page($title, $content)