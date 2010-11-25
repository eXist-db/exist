xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";

let $name :=  request:get-parameter('name','')
let $remove := repo:remove($name)

return
<html>
    <body>
         {$name} removed: {$remove} .<br/>
        <i>note: you will need to restart eXist for changes to take effect</i>
    </body>
</html>