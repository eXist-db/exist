xquery version "1.0";

import module namespace status="http://exist-db.org/xquery/admin-interface/status" at "status.xqm";
import module namespace browse="http://exist-db.org/xquery/admin-interface/browse" at "browse.xqm";

declare namespace admin="http://exist-db.org/xquery/admin-interface";
declare namespace xdb="http://exist-db.org/xquery/xmldb";

declare function admin:info-header() as element() {
    <div class="info">
        <ul>
            <li>Version: {util:system-property("product-version")}</li>
            <li>Build: {util:system-property("product-build")}</li>
        </ul>
    </div>
};

declare function admin:panel-select($user as xs:string, $passwd as xs:string) as element() {
    let $panel := request:request-parameter("panel", "status")
    return
        if($panel eq "browse") then
            browse:main($user, $passwd)
        else
            status:main()
};

declare function admin:check-user() as element() {
    let $user := request:get-session-attribute("user") ,
        $pass := request:get-session-attribute("password"),
        $login := xdb:authenticate("xmldb:exist:///db", $user, $pass)
    return
        if(not($login)) then
            admin:display-login-form($user)
        else
            admin:panel-select($user, $pass)
};

declare function admin:do-login($user as xs:string) as element() {
    let $pass := request:request-parameter("pass", ()),
        $login := xdb:authenticate("xmldb:exist:///db", $user, $pass)
    return
        if($login) then (
            request:set-session-attribute("user", $user),
            request:set-session-attribute("password", $pass),
            admin:panel-select($user, $pass)
        ) else
            admin:display-login-form($user)
};

declare function admin:main() as element() {
    let $user := request:request-parameter("user", ()),
        $logout := request:request-parameter("logout", ())
    return
        if($logout) then (
            request:invalidate-session(),
            admin:display-login-form($user)
        ) else if($user) then
            admin:do-login($user)
        else
            admin:check-user()
};

declare function admin:display-login-form($user) as element() {
    <div class="panel">
        <div class="panel-head">Login</div>
        <p>This is a protected resource. Only registered database users can log
        in. If you have not set up any users, login as "admin" and leave the
        password field empty. For testing purposes, you may also log in as
        "guest" with password "guest".</p>

        <form action="{request:encode-url(request:request-uri())}">
            <table class="login" cellpadding="5">
                <tr>
                    <th colspan="2" align="left">Please Login</th>
                </tr>
                <tr>
                    <td align="left">Username:</td>
                    <td><input name="user" type="text" size="20" value="{$user}"/></td>
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
        {request:create-session()}
    </div>
};

request:create-session(),
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
                    <li><a href="{request:encode-url(request:request-uri())}?panel=status">System Status</a></li>
                    <li><a href="{request:encode-url(request:request-uri())}?panel=browse">Browse Collections</a></li>
                </ul>
                <a class="logout" href="{request:encode-url(request:request-uri())}?logout=Logout">Logout</a>
            </div>
            {admin:main()}
        </div>
    </body>
</html>
