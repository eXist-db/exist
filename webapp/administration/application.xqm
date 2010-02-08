xquery version "1.0";
(: $Id$ :)
(:
    Application's general functions.
:)

module namespace app="http://exist-db.org/application";

declare function app:node($id as xs:string) as element() {
	let $app := collection("/db")//app:package/app:application[@id=$id]
	return
		if (count($app) eq 1) then
			$app
		else if (count($app) = 0) then
			<error xmlns="http://exist-db.org/application">
				<title>Not found (id = {$id})</title>
				<path/>
			</error>
		else
			<error xmlns="http://exist-db.org/application">
				<title>Multi on id = {$id}</title>
				<path/>
			</error>
};

declare function app:title($application as xs:string) as xs:string {
	let $app := app:node($application)
	return
		xs:string($app/app:title)
};

declare function app:path($application as xs:string) as xs:string {
	let $app := app:node($application)
	return
		xs:string($app/app:title)
};

declare function local:menuSubapplications($application as xs:string, $path as xs:string) as element()* {
	let $apps := collection("/db")//app:package/app:application/app:plug-to-application[@ref=$application]/..
	let $menu := for $application in $apps
				return
				<li>
					<a href="{concat($path, xs:string($application/app:path))}">
					{xs:string($application/app:title)}
					</a>
					{local:menuSubapplications(xs:string($application/@id), concat($path, xs:string($application/app:path)))}
				</li>
	
	return
		if (count($apps) eq 0) then
			()
		else if ($path eq "") then
			<ul id="nav">{$menu}</ul>
		else
			<ul>{$menu}</ul>
};

declare function app:menuSubapplications($application as xs:string) as element()* {
   	local:menuSubapplications($application, "")
};

declare function app:tableSubapplications($application as xs:string) as element()* {
	let $apps := collection("/db")//app:package/app:application/app:plug-to-application[@ref=$application]/..
	return
		<table>
			<tr>
			{
			for $application in $apps
				return
				<td>
					<a href="{xs:string($application/app:path)}">
					<img src="{concat($application/app:path,$application/app:logo/text())}"/>
					</a>
				</td>
			}
			</tr>
		</table>
};
