xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xdb="http://exist-db.org/xquery/xmldb";

declare variable $database-uri as xs:string { "xmldb:exist:///db" };
declare variable $redirect-uri as xs:anyURI { xs:anyURI("session.xql") };

declare function local:login($user as xs:string) as element()?
{
    let $pass := request:request-parameter("pass", ""),
        $login := xdb:authenticate($database-uri, $user, $pass)
    return
        if ($login) then (
            request:set-session-attribute("user", $user),
            request:set-session-attribute("password", $pass),
            request:redirect-to(request:encode-url($redirect-uri))
        ) else
            <p>Login failed! Please retry.</p>
};

declare function local:do-login() as element()?
{
    let $user := request:request-parameter("user", ())
    return
        if ($user) then
            local:login($user)
        else ()
};

request:invalidate-session(),
request:create-session(),
<html>
    <head>
        <title>Login</title>
        <link rel="stylesheet" type="text/css" href="login.css"/>
    </head>
    <body>
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
        { local:do-login() }
        <p>
			<small>View <a href="login.xql?_source=yes">source code</a></small>
		</p>
    </body>
</html>
