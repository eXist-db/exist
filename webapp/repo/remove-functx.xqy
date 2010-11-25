xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";

let $install := repo:remove('functx-1.0')
return
<html>
    <body>
        Functx 1.0 has been removed.<br/>
        <i>note: you will need to restart eXist for changes to take effect</i>
    </body>
</html>