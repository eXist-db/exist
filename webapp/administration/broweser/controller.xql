xquery version "1.0";

if ($exist:path eq '/') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.xql"/>
	</dispatch>
else
	(: everything else is passed through :)
	<ignore xmlns="http://exist.sourceforge.net/NS/exist">
		<cache-control cache="yes"/>
	</ignore>