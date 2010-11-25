xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";

let $install := repo:install('http://127.0.0.1:8080/exist/repo/packages/functx-1.0.xar')
return
<html>
    <body>
        Functx 1.0 installed: {$install} .<br/>
        <i>note: you will need to restart eXist for changes to take effect</i>
    </body>
</html>