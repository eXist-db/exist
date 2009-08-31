xquery version "1.0";
(: $Id$ :)
(:
    Main module of the database administration interface.
:)

declare namespace admin = "http://exist-db.org/xquery/admin-interface";

declare namespace request = "http://exist-db.org/xquery/request";
declare namespace session = "http://exist-db.org/xquery/session";
declare namespace util = "http://exist-db.org/xquery/util";
declare namespace xdb = "http://exist-db.org/xquery/xmldb";

import module namespace status = "http://exist-db.org/xquery/admin-interface/status" at "status.xqm";
import module namespace browse = "http://exist-db.org/xquery/admin-interface/browse" at "browse.xqm";
import module namespace users = "http://exist-db.org/xquery/admin-interface/users" at "users.xqm";
import module namespace xqueries = "http://exist-db.org/xquery/admin-interface/xqueries" at "xqueries.xqm";
import module namespace shut = "http://exist-db.org/xquery/admin-interface/shutdown" at "shutdown.xqm";
import module namespace setup = "http://exist-db.org/xquery/admin-interface/setup" at "setup.xqm";
import module namespace rev="http://exist-db.org/xquery/admin-interface/revisions" at "versions.xqm";
import module namespace backup="http://exist-db.org/xquery/admin-interface/backup" at "backup.xqm";
import module namespace prof="http://exist-db.org/xquery/profiling" at "trace.xqm";

declare option exist:serialize "method=xhtml media-type=text/html";

(: 
    Display the version, SVN revision and user info in the top right corner 
:)
declare function admin:info-header() as element()
{
    <div class="info">
        <ul>
            <li>Version: { util:system-property( "product-version" ) }</li>
            <li>SVN Revision: { util:system-property( "svn-revision" ) }</li>
            <li>Build: {util:system-property("product-build")}</li>
            <li>User: { xdb:get-current-user() }</li>
        </ul>
    </div>
};

(:
    Select the page to show. Every page is defined in its own module 
:)
declare function admin:panel() as element()
{
    let $panel := request:get-parameter("panel", "status") return
        if($panel eq "browse") then
        (
            browse:main()
        )
        else if($panel eq "users") then
        (
            users:main()
        )
         else if($panel eq "xqueries") then
        (
            xqueries:main()
        )
        else if($panel eq "shutdown") then
        (
            shut:main()
        )
        else if($panel eq "setup") then
        (
            setup:main()
        )
		else if ($panel eq "revisions") then
		(
			rev:main()
		)
		else if ($panel eq "backup") then
		    backup:main()
	    else if ($panel eq "trace") then
	        prof:main()
        else
        (
            status:main()
        )
};

(:~  
    Display the login form.
:)
declare function admin:display-login-form() as element()
{
    <div class="panel">
        <div class="panel-head">Login</div>
        <p>This is a protected resource. Only registered database users can log
        in. If you have not set up any users, login as "admin" and leave the
        password field empty. Note that the "guest" user is not permitted access.</p>

        <form action="{session:encode-url(request:get-uri())}" method="post">
            <table class="login" cellpadding="5">
                <tr>
                    <th colspan="2" align="left">Please Login</th>
                </tr>
                <tr>
                    <td align="left">Username:</td>
                    <td><input name="user" type="text" size="20"/></td>
                </tr>
                <tr>
                    <td align="left">Password:</td>
                    <td><input name="pass" type="password" size="20"/></td>
                </tr>
                <tr>
                    <td colspan="2" align="left"><input type="submit"/></td>
                </tr>
            </table>
        </form>
    </div>
};

(: main entry point :)
let $isLoggedIn :=  if(xdb:get-current-user() eq "guest")then
    (
        (: is this a login attempt? :)
        if(request:get-parameter("user", ()) and not(empty(request:get-parameter("pass", ()))))then
        (
            if(request:get-parameter("user", ()) eq "guest")then
            (
                (: prevent the guest user from accessing the admin webapp :)
                false()
            )
            else
            (
                (: try and log the user in :)
                xdb:login( "/db", request:get-parameter("user", ()), request:get-parameter("pass", () ), true() )
            )
        )
        else
        (
            (: prevent the guest user from accessing the admin webapp :)
            false()
        )
    )
    else
    (
        (: if we are already logged in, are we logging out - i.e. set permissions back to guest :)
        if(request:get-parameter("logout",()))then
        (
        	let $null  := xdb:login("/db", "guest", "guest") 
            let $inval := session:invalidate()
            
            return false()
        )
        else
        (
             (: we are already logged in and we are not the guest user :)
            true()
        )
    )
return
    <html>
        <head>
            <title>eXist Database Administration</title>
            <link type="text/css" href="admin.css" rel="stylesheet"/>
			<link type="text/css" href="styles/prettify.css" rel="stylesheet"/>
			<link type="text/css" href="../scripts/yui/yui-skin.css" rel="stylesheet"/>
            <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
			<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
			<script type="text/javascript" src="scripts/prettify.js"/>
			<script type="text/javascript" src="../scripts/yui/yui-combined2.7.0.js"></script>
			<script type="text/javascript" src="scripts/admin.js"></script>	
        </head>
        <body class="yui-skin-sam">
            <div class="header">
                {admin:info-header()}
                <img src="logo.jpg"/>
            </div>
            
            <div class="content">
                <div class="guide">
                    <div class="guide-title">Select a Page</div>
                    <ul>
                        <li><a href="..">Home</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=status">System Status</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=browse">Browse Collections</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=users">User Management</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=xqueries">View Running Jobs</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=setup">Examples Setup</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=backup">Backups</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=trace">Query Profiling</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=shutdown">Shutdown</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?logout=yes">Logout</a></li>
                    </ul>
                    <div class="userinfo">
                        Logged in as: {xdb:get-current-user()}
                    </div>
                </div>
                {
                    if($isLoggedIn)then
                    (
                        admin:panel()
                    )
                    else
                    (
                        admin:display-login-form()
                    )
                }
            </div>
        </body>
    </html>
