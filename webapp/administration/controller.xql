xquery version "1.0";

if ($exist:path eq 'login.xql') then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>
else if (starts-with($exist:path, '/css/')) then
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>
else if (not (xmldb:is-authenticated())) then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="login.xql"/>
	</dispatch>

else if ($exist:path eq '/') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.xql"/>
	</dispatch>
else
	(: everything else is passed through :)
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>