xquery version "1.0";

declare option exist:serialize "method=xml";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

let $home := util:system-property("exist.home")
let $sep := util:system-property("file.separator")
let $tempDir := concat($home, $sep, "test", $sep, "temp")
let $stored := xdb:store-files-from-pattern("/db/webtest", $tempDir, "**/*.xml", "text/xml", true())
return
<html>
    <head>
        <title>XML:DB module functions test</title>
    </head>
    <body>
        <p id="count">{count($stored)}</p>
	    <p id="source">{$tempDir}</p>
        <p id="stored">{$stored}</p>
    </body>
</html>