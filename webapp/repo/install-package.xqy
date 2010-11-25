xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";

let $host :=  request:get-hostname()
let $url :=  request:get-parameter('url','')
let $name := replace($url, '^.*/([^/]+)$', '$1')
let $install := repo:install(concat('http://', $host,':8080',$url))

return
<html>
    <body>
         {$name} installed: {$install} .<br/>
        <i>note: you will need to restart eXist for changes to take effect</i>
    </body>
</html>