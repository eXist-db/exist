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
    <set-attribute name="xquery.user" value="{$user}"/>,
    <set-attribute name="xquery.password" value="{$password}"/>
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
        else if ($user) then
            let $loggedIn := xdb:login("/db", $user, $password)
            return
                local:set-credentials($user, $password)
        else if ($sessionCredentials) then
            local:set-credentials($sessionCredentials[1], $sessionCredentials[2])
        else
            ()
};

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.xml"/>
	</dispatch>

else if ($exist:resource eq 'index.xml') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        {local:set-user()}
        <view>
            <forward url="style.xql"/>
    	</view>
    </dispatch>
    
(: 
	jQuery module demo: tags in the jquery namespace are expanded
	by style-jquery.xql
:)
else if ($exist:resource eq 'jquery.xml') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        {local:set-user()}
        <view>
            <forward url="style.xql"/>
            <forward url="style-jquery.xql"/>
    	</view>
    </dispatch>

(:
	Error handling: faulty.xql will trigger an XQuery error which
	will be handled by error-handler.xql
:)
else if ($exist:resource eq 'faulty.xql') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        {local:set-user()}
        <view>
            <forward url="style.xql"/>
    	</view>
        <error-handler>
            <forward url="error-handler.xql"/>
            <forward url="style.xql"/>
        </error-handler>
    </dispatch>

(:  Protected resource: user is required to log in with valid credentials.
    If the login fails or no credentials were provided, the request is redirected
    to the login.xml page. :)
else if ($exist:resource eq 'protected.xml') then
    let $login := local:set-user()
    return
        if ($login) then
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                {$login}
                <view>
                    <forward url="style.xql"/>
                </view>
            </dispatch>
        else
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="login.xml"/>
                <view>
                  <forward url="style.xql"/>
                </view>
            </dispatch>
    
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </dispatch>
