xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";

let $host :=  request:get-hostname()
let $name :=  request:get-parameter('name','')
let $name := replace($url, '^.*/([^/]+)$', '$1')
let $install := repo:install(concat('http://', $host,':8080',$url))

return
<html>
    <body>
         {$name} removed: {$install} .<br/>
        <i>note: you will need to restart eXist for changes to take affect</i>
    </body>
</html>