xquery version "1.0";

import module namespace templates="http://exist-db.org/xquery/templates" at "templates.xql";

declare option exist:serialize "method=html5 media-type=text/html";

declare variable $modules :=
    <modules>
        <module prefix="config" uri="http://exist-db.org/xquery/apps/config" at="config.xql"/>
        <module prefix="app" uri="http://exist-db.org/xquery/app" at="app.xql"/>
    </modules>;

let $content := request:get-data()/element()
return
    templates:apply($content, $modules, ())