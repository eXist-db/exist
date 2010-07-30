xquery version "1.0";

import module namespace style = "http://www.danmccreary.com/library" at "../../../modules/style.xqm";

let $app-collection := $style:db-path-to-app
let $id := request:get-parameter('id', '')
let $file := concat($app-collection, '/code-tables/', $id, 's.xml')
let $item := doc($file)/code-table

return $item