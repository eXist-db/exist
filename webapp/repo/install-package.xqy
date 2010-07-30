xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";


let $install := 1 (: repo:install($url) :)
return
<html>
    <body>
        {$exist:controller} installed: {$install} .<br/>
        <i>note: you will need to restart eXist for changes to take affect</i>
    </body>
</html>