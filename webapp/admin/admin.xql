xquery version "1.0";

import module namespace status="http://exist-db.org/xquery/admin-interface/status" at "status.xqm";
import module namespace browse="http://exist-db.org/xquery/admin-interface/browse" at "browse.xqm";

declare namespace admin="http://exist-db.org/xquery/admin-interface";

declare function admin:info-header() as element() {
    <div class="info">
        <ul>
            <li>Version: {util:system-property("product-version")}</li>
            <li>Build: {util:system-property("product-build")}</li>
        </ul>
    </div>
};

declare function admin:main() as element() {
    let $panel := request:request-parameter("panel", ())
    return
        if($panel eq "browse") then
            browse:main()
        else
            status:main()
};

<html>
    <head>
        <title>eXist Database Administration</title>
        <link type="text/css" href="admin.css" rel="stylesheet"/>
    </head>
    <body>
        <div class="header">
            {admin:info-header()}
            <img src="logo.jpg"/>
        </div>
        
        <div class="content">
            <div class="guide">
                <div class="guide-title">Select an Area</div>
                <ul>
                    <li><a href="?panel=status">System Status</a></li>
                    <li><a href="?panel=browse">Browse Collections</a></li>
                </ul>
            </div>
            {admin:main()}
        </div>
    </body>
</html>
