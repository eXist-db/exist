xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

declare namespace exist="http://exist.sourceforge.net/NS/exist";

declare function local:service-request($src as element(exist:forward), $attrs as element(exist:set-attribute)*, $view-preprocessors as element(exist:forward)*) {
    
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            {$attrs}
            {$src}
        	<view>
        	   
        	   {$view-preprocessors}
        		
        		<forward servlet="XSLTServlet">
        			<set-attribute name="xslt.stylesheet" value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
        			<set-attribute name="xslt.output.media-type" value="text/html"/>
        			<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
        			<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
        		</forward>
        	</view>
        	<cache-control cache="no"/>
        </dispatch>
};
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
    <exist:set-attribute name="xquery.user" value="{$user}"/>,
    <exist:set-attribute name="xquery.password" value="{$password}"/>
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
        else if (not(empty($sessionCredentials))) then
            local:set-credentials($sessionCredentials[1], $sessionCredentials[2])
        else
            ()
};

if($exist:path eq "/")then(
    let $login := local:set-user() return
        if($login) then
            local:service-request(<exist:forward servlet="JMXServlet"/>, $login,
                 <exist:forward servlet="XSLTServlet">
             		<set-attribute name="xslt.stylesheet" value="{$exist:root}/xquery/status/status.xslt"/>
             		<set-attribute name="xslt.output.media-type" value="text/html"/>
             		<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
             		<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
             	</exist:forward>
            )
        else
            local:service-request(<exist:forward url="login.xml"/>, (), ())
) else if (matches($exist:path, '(styles/syntax|scripts/syntax/|logo.jpg|default-style2.css|curvycorners.js)')) then (
    let $newPath := replace($exist:path, '^.*((styles/|scripts/|logo).*)$', '/$1') return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            <forward url="{$newPath}"/>
            <cache-control cache="yes"/>
        </dispatch>
) else (
    <ignore xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </ignore>
)