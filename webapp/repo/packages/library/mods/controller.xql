xquery version "1.0";

if (ends-with($exist:resource, '.xq')) then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<set-attribute name="exist:root" value="{$exist:root}"/>
		<set-attribute name="exist:path" value="{$exist:path}"/>
	</dispatch>
	
else if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="apps/mods/search/index.xml"/>
	</dispatch>
	
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </dispatch>