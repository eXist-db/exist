xquery version "1.0";
(: $Id$ :)
(:
    Main module of the database administration interface.
:)

declare namespace admin="http://exist-db.org/xquery/admin-interface";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace status="http://exist-db.org/xquery/admin-interface/status" at "status.xqm";
import module namespace browse="http://exist-db.org/xquery/admin-interface/browse" at "browse.xqm";
import module namespace users="http://exist-db.org/xquery/admin-interface/users" at "users.xqm";
import module namespace shut="http://exist-db.org/xquery/admin-interface/shutdown" at "shutdown.xqm";
import module namespace setup="http://exist-db.org/xquery/admin-interface/setup" at "setup.xqm";

(: 
    Display the version and user info in the top right corner 
:)
declare function admin:info-header($user as xs:string) as element() {
    <div class="info">
        <ul>
            <li>Version: {util:system-property("product-version")}</li>
            <li>Build: {util:system-property("product-build")}</li>
            <li>User: { $user}</li>
        </ul>
    </div>
};

(:
    Select the page to show. Every page is defined in its own module 
:)
declare function admin:panel($user as xs:string, $pass as xs:string?) as element() {
    let $panel := request:get-parameter("panel", "status")
    return
        if($panel eq "browse") then
            browse:main($user, $pass)
        else if($panel eq "users") then
            users:main($user, $pass)
        else if($panel eq "shutdown") then
            shut:main($user, $pass)
        else if($panel eq "setup") then
            setup:main()
        else
            status:main()
};

(:
    Main function: display login form if no credentials have been supplied
    or logout is selected.
    
    $credentials is either an empty sequence or a pair (user, password).
:)
declare function admin:main($credentials as xs:string*) as element()+ {
    if( empty($credentials) or (count($credentials)!=2) ) then
        admin:display-login-form()
    else
        admin:panel($credentials[1], $credentials[2])
};

(:~  
    Display the login form.
:)
declare function admin:display-login-form() as element() {
    <div class="panel">
        <div class="panel-head">Login</div>
        <p>This is a protected resource. Only registered database users can log
        in. If you have not set up any users, login as "admin" and leave the
        password field empty. For testing purposes, you may also log in as
        "guest" with password "guest".</p>

        <form action="{session:encode-url(request:get-uri())}">
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

(:~  Try to authenticate the user name and password from
    the current HTTP session. Returns a pair of (user, password)
    on success, an empty sequence otherwise.
:)
declare function admin:checkUser() as xs:string* {
    let $user := session:get-attribute("user") ,
        $pass := session:get-attribute("password"),
        $login := xdb:authenticate("xmldb:exist:///db", $user, $pass)
    return
        if($login) then
            ($user, $pass)
        else
            ()
};

(:~
    Process user and password passed from the login form.
    Returns a pair of (user, password) if the credentials are
    valid, an empty sequence if not.
:)
declare function admin:doLogin($user as xs:string) as xs:string* {
    let $pass := request:get-parameter("pass", ""),
        $login := session:set-current-user($user, $pass)
    return
        if($login) then
            ($user, $pass)
        else
            ()
};

(:~  
    Authenticate the user 
:)
declare function admin:login() as xs:string* {
    let $userParam := request:get-parameter("user", ())
    return
        if($userParam) then
            admin:doLogin($userParam)
        else
            admin:checkUser()
};


session:create(),
let $logout := request:get-parameter("logout", ()),
    $s := if($logout) then session:invalidate() else session:create(),
    $credentials := admin:login(),
    $user := if(exists($credentials)) then $credentials[1] else "not logged in"
return
    <html>
        <head>
            <title>eXist Database Administration</title>
            <link type="text/css" href="admin.css" rel="stylesheet"/>
        </head>
        <body>
            <div class="header">
                {admin:info-header($user)}
                <img src="logo.jpg"/>
            </div>
            
            <div class="content">
                <div class="guide">
                    <div class="guide-title">Select a Page</div>
                    <ul>
                        <li><a href="..">Home</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=status">System Status</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=browse">Browse Collections</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=users">Manage Users</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=setup">Examples Setup</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?panel=shutdown">Shutdown</a></li>
                        <li><a href="{session:encode-url(request:get-uri())}?logout=yes">Logout</a></li>
                    </ul>
                    <div class="userinfo">
                        Logged in as: {$user}
                    </div>
                </div>
                {admin:main($credentials)}
            </div>
        </body>
    </html>