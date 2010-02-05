xquery version "1.0";

import module namespace app = "http://exist-db.org/application" at "application.xqm";

let $baseURL : = concat(request:get-context-path(), $exist:root,$exist:controller, '/')
return 
if ($exist:path eq 'login.xql') then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>
else if (starts-with($exist:path, '/css/')) then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>
else if (not (xmldb:is-authenticated())) then
	if ($exist:path eq '') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="{baseURL}"/>
		</dispatch>
	else
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{concat($baseURL, "login.xql")}"/>
		</dispatch>

else if ($exist:path eq '/') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="index.xql"/>
	</dispatch>
else
	let $app := $exist:resource
	return
	if (not($app eq "")) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="{concat(
				request:get-context-path(), $exist:root, 
				$exist:controller, "/", app:path($app))}"/>
		</dispatch>
	else
		(: everything else is passed through :)
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
			<cache-control cache="yes"/>
		</ignore>