xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

(:~
    Retrieve current user credentials from HTTP session
:)
declare function local:credentials-from-session() as xs:string* {
    (session:get-attribute("myapp.user"), session:get-attribute("myapp.password"))
};

(:~
    Store user credentials to session for future use. Return an XML
    fragment to pass user and password to the query.
:)
declare function local:set-credentials($user as xs:string, $password as xs:string?) as element()+ {
    session:set-attribute("myapp.user", $user), 
    session:set-attribute("myapp.password", $password),
    <set-attribute xmlns="http://exist.sourceforge.net/NS/exist"
		name="xquery.user" value="{$user}"/>,
    <set-attribute xmlns="http://exist.sourceforge.net/NS/exist"
		name="xquery.password" value="{$password}"/>
};

(:~
    Check if login parameters were passed in the request. If yes, try to authenticate
    the user and store credentials into the session. Clear the session if parameter
    "logout" is set.
    
    The function returns an XML fragment to be included into the dispatch XML or
    the empty set if the user could not be authenticated or the
    session is empty.
:)
declare function local:set-user() as element()* {
    session:create(),
    let $logout := request:get-parameter("logout", ())
    let $user := request:get-parameter("user", ())
    let $password := request:get-parameter("password", ())
    let $sessionCredentials := local:credentials-from-session()
    return
        if ($logout) then
            session:invalidate()
        else if ($user and $user != "guest") then
            let $loggedIn := xdb:login("/db", $user, $password)
            return
				if ($loggedIn) then
                	local:set-credentials($user, $password)
				else
					util:log("DEBUG", ("Could not log in ", $user, $password))
        else if (exists($sessionCredentials)) then
            local:set-credentials($sessionCredentials[1], $sessionCredentials[2])
        else
            ()
};

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    {
        let $loggedIn := local:set-user()
        return
            if (exists($loggedIn)) then
		      <forward servlet="JMXServlet"/>
		    else
		      <forward url="login.html"/>
    }
	</dispatch>
	
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </dispatch>
