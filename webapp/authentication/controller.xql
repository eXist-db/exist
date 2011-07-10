xquery version "1.0";

let $action := request:get-parameter("action",())
let $baseURL : = concat($exist:context, $exist:root, $exist:controller, '/')
let $URL : = concat($exist:root, $exist:controller, '/')
return 
if ($action eq 'logout') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		{session:invalidate()}
		<redirect url="{$baseURL}"/>
	</dispatch>

else if ($exist:path eq 'login.xql') then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>

else if (not (sm:is-externally-authenticated())) then
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

else if (starts-with($exist:path, '/error/')) then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>

else if (ends-with($exist:resource, (".xql"))) then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>
else
	()