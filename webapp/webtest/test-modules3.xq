xquery version "1.0";
(: $Id$ :)

import module namespace testmod="http://exist-db.org/xquery/testmod"
at "xmldb:exist:///db/webtest/testmod.xq";

declare option exist:serialize "method=xhtml";

<html>
    <head>
        <title>XQueryGenerator: import module from database</title>
    </head>
    <body>
        {testmod:hello-world()}
        
        <p id="base-uri">{base-uri()}</p>
    </body>
</html>