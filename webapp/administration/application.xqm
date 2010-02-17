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

declare function app:title($id as xs:string) as xs:string {
	let $app := app:node($id)
	return
		xs:string($app/app:title)
};

declare function app:path($id as xs:string) as xs:string {
	let $app := app:node($id)
	return
		xs:string($app/app:path)
};

declare function app:pathUp($id as xs:string, $masterId as xs:string) as xs:string {
	if ($masterId eq $id) then ""
	else
		let $app := app:node($id)
		return
		if (name($app) eq "error") then "" (: TODO: how process errors? :) 
		else
			let $parent := if ($app/app:plug-to-application) then app:pathUp($app/app:plug-to-application/@ref, $masterId) else ""
			return
				concat($parent, $app/app:path)
};

declare function app:pathUpLocal($file as node()) as xs:string {
	let $folder := $file/parent::app:folder
	return
	if (count($folder) eq 1) then
		concat(app:pathUpLocal($folder), "/", xs:string($file/@name))
	else
		xs:string($file/@name)
};

declare function app:primaryFile($parent as xs:string, $resource as xs:string, $onId as xs:string) as xs:string {
	let $apps := app:split($parent)
	let $appID := $apps[last()]
	let $app := app:node($appID)
	return
	if (name($app) eq "error") then
		let $app := app:node($resource)
		return
		if (name($app) eq "error") then
			app:error("notFound",$appID)
		else if ($app/app:primaryFile) then
			concat(app:pathUp($resource, $onId), $app/app:primaryFile/text())
		else
			app:error("noPrimaryFile",$appID)
	
	else if (($app/app:primaryFile) and ($resource eq "")) then
		concat(app:pathUp($appID, $onId), $app/app:primaryFile/text())
	
	else if ($resource) then
		let $file := $app//app:file[@name eq $resource]
		let $alias := $app//app:file[@alias eq $resource]
		return
		if (count($file) eq 1) then 
			concat(app:pathUp($appID, $onId), app:pathUpLocal($file))
		else if (count($alias) eq 1) then
			concat(app:pathUp($appID, $onId), app:pathUpLocal($alias))
		else if ((count($file) eq 0) and (count($alias) eq 0)) then 
			app:error("notFound",$appID,$resource)
		else
			app:error("multiFiles",$appID,$resource)
	else
		app:error("notFound",$appID,$resource)
};

declare function local:menuSubapplications($id as xs:string, $path as xs:string) as element()* {
	let $apps := collection("/db")//app:package/app:application/app:plug-to-application[@ref=$id]/..
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

declare function app:tableSubapplications($id as xs:string) as element()* {
	let $apps := collection("/db")//app:package/app:application/app:plug-to-application[@ref=$id]/..
	return
		<table>
			<tr>
			{
			for $app in $apps
				return
				<td>
					<a href="{xs:string($app/app:path)}">
					<img src="{concat($app/app:path,$app/app:logo/text())}"/>
					</a>
				</td>
			}
			</tr>
		</table>
};

declare function app:split($str as xs:string) as xs:string* {
	let $before := substring-before($str,"/")
	return
	if (string-length($before) eq 0) then $str
	else ($before, app:split(substring($str, string-length($before)+2)))
};

declare function app:error($errorID as xs:string, $appID as xs:string) as xs:string {
	concat("error/",$errorID,"?application=",$appID)
};

declare function app:error($errorID as xs:string, $appID as xs:string, $resource as xs:string) as xs:string {
	concat("error/",$errorID,"?application=",$appID,"&amp;resource=",$resource)
};
