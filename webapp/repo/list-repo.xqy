xquery version "1.0";

import module namespace repo="http://exist-db.org/xquery/repo";

<html>
    <body>
        The following packages are installed:<br/>
        <ul>
        {for $package in repo:list()
            return
            <li>{$package}</li>
        }
        </ul>
        <i>note: you will need to restart eXist for module to be used</i>
    </body>
</html>