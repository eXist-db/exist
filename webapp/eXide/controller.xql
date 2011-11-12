xquery version "1.0";

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
    <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="xquery.user" value="{$user}"/>,
    <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="xquery.password" value="{$password}"/>
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
    let $user := request:get-parameter("user", ())
    let $password := request:get-parameter("password", ())
    let $sessionCredentials := local:credentials-from-session()
    return
        if ($user) then
            let $loggedIn := xmldb:login("/db", $user, $password)
            return
                if ($loggedIn) then
                    local:set-credentials($user, $password)
                else
                    ()
        else if (exists($sessionCredentials)) then
            local:set-credentials($sessionCredentials[1], $sessionCredentials[2])
        else
            ()
};

declare function local:logout() as element() {
    session:invalidate(),
    <ok/>
};

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <redirect url="index.html"/>
    </dispatch>

(:
 : Login a user via AJAX. Just returns a 401 if login fails.
 :)
else if ($exist:resource eq 'login') then
    let $loggedIn := local:set-user()
    return
        if ($loggedIn) then
            <ok/>
        else (
            response:set-status-code(401),
            <fail/>
        )

else if ($exist:resource eq "logout") then
    local:logout()

else if ($exist:resource eq "index.html") then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <view>
            <forward url="modules/view.xql"/>
        </view>
    </dispatch>

else if ($exist:resource eq 'execute') then
    let $query := request:get-parameter("qu", ())
    let $base := request:get-parameter("base", ())
    let $startTime := util:system-time()
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
	<!-- Query is executed by XQueryServlet -->
            <forward servlet="XQueryServlet">
                {local:set-user()}
                <!-- Query is passed via the attribute 'xquery.source' -->
                <set-attribute name="xquery.source" value="{$query}"/>
                <!-- Results should be written into attribute 'results' -->
                <set-attribute name="xquery.attribute" value="results"/>
		        <set-attribute name="xquery.module-load-path" value="{$base}"/>
                <clear-attribute name="results"/>
                <!-- Errors should be passed through instead of terminating the request -->
                <set-attribute name="xquery.report-errors" value="yes"/>
                <set-attribute name="start-time" value="{util:system-time()}"/>
            </forward>
            <view>
            <!-- Post process the result: store it into the HTTP session
               and return the number of hits only. -->
            <forward url="modules/session.xql">
               <clear-attribute name="xquery.source"/>
               <clear-attribute name="xquery.attribute"/>
               <set-attribute name="elapsed" 
                   value="{string(seconds-from-duration(util:system-time() - $startTime))}"/>
            </forward>
	</view>
        </dispatch>
        
(: Retrieve an item from the query results stored in the HTTP session. The
 : format of the URL will be /sandbox/results/X, where X is the number of the
 : item in the result set :)
else if (starts-with($exist:path, '/results/')) then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <forward url="../modules/session.xql">
            {local:set-user()}
            <add-parameter name="num" value="{$exist:resource}"/>
        </forward>
    </dispatch>

else if ($exist:resource eq "outline") then
    let $query := request:get-parameter("qu", ())
    let $base := request:get-parameter("base", ())
	return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
	        <!-- Query is executed by XQueryServlet -->
            <forward url="modules/outline.xql">
                {local:set-user()}
	        <set-attribute name="xquery.module-load-path" value="{$base}"/>
            </forward>
    </dispatch>

else if (ends-with($exist:path, ".xql")) then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        { local:set-user() }
        <set-attribute name="app-root" value="{$exist:prefix}{$exist:controller}"/>
    </dispatch>
    
else if (starts-with($exist:path, "/libs/")) then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <forward url="/{substring-after($exist:path, '/libs/')}" absolute="yes"/>
    </dispatch>
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </dispatch>
