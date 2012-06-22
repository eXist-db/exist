xquery version "1.0";

import module namespace login="http://exist-db.org/xquery/app/wiki/session" at "modules/login.xql";

declare variable $exist:path external;
declare variable $exist:resource external;
declare variable $exist:prefix external;
declare variable $exist:controller external;

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <redirect url="index.html"/>
    </dispatch>

(:
 : Login a user via AJAX. Just returns a 401 if login fails.
 :)
else if ($exist:resource = 'login') then
    let $loggedIn := login:set-user("org.exist.login", ())
    return (
        util:declare-option("exist:serialize", "method=json"),
        if (exists($loggedIn)) then
            <status>{$loggedIn[@name="org.exist.login.user"]/@value/string()}</status>
        else (
            response:set-status-code(401),
            <status>fail</status>
        )
    )

else if ($exist:resource eq "index.html") then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <set-header name="Cache-Control" value="max-age=3600"/>
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
                {login:set-user("org.exist.login", ())}
                <set-header name="Cache-Control" value="no-cache"/>
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
            {login:set-user("org.exist.login", ())}
            <set-header name="Cache-Control" value="no-cache"/>
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
                {login:set-user("org.exist.login", ())}
                <set-header name="Cache-Control" value="no-cache"/>
	            <set-attribute name="xquery.module-load-path" value="{$base}"/>
            </forward>
    </dispatch>

else if (ends-with($exist:path, ".xql")) then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        {login:set-user("org.exist.login", ())}
        <set-header name="Cache-Control" value="no-cache"/>
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
