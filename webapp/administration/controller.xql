xquery version "1.0";

import module namespace app = "http://exist-db.org/application" at "application.xqm";
import module namespace pckg = "http://exist-db.org/packages" at "packages/module.xqm";

let $firstRun := pckg:firstRunImport()
let $baseURL : = concat($exist:context, $exist:root, $exist:controller, '/')
let $URL : = concat($exist:root, $exist:controller, '/')
return 
if ($exist:path eq 'login.xql') then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>
else if (not (xmldb:is-authenticated())) then
	if ($exist:path eq '') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="{$baseURL}"/>
		</dispatch>
	else
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{concat($URL, "login.xql")}"/>
		</dispatch>

else if ($exist:path eq '/') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="index.xql"/>
	</dispatch>
else
	let $app := $exist:resource
	return
	if (ends-with($app, (".xql",".js",".css",".png",".ico",".gif"))) then
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
			<cache-control cache="yes"/>
		</ignore>
	else if (not($app eq "")) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="{concat($baseURL, app:primaryFile($app, "aea0e743-f7eb-400c-a0f0-61d8436ca59e"))}"/>
		</dispatch>
	else
		() (: TODO: restrict :)