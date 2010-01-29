xquery version "1.0";
(: $Id$ :)
(:
    Database Administration panel
:)

declare namespace app = "http://exist-db.org/application";
declare namespace xdb = "http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes omit-xml-declaration=no";

declare function local:_applications($application as xs:string, $path as xs:string) as element()* {
	let $apps := //app:package/app:application/app:plug-to-application[@ref=$application]/..
	return
		if (count($apps) eq 0) then
			()
		else
			<ul>
			{
				for $application in $apps
				return
					<li>
					<a href="{concat($path, xs:string($application/app:path))}">
					{xs:string($application/app:title)}
					</a>
					{local:_applications(xs:string($application/@id), concat($path, xs:string($application/app:path)))}
				</li>
			}
		</ul>
};

declare function local:applications($application as xs:string) as element()* {
	for $application in //app:package/app:application/app:plug-to-application[@ref=$application]/..
	return
		<li>
			<a href="{xs:string($application/app:path)}">
				{xs:string($application/app:title)}
			</a>
			{local:_applications(xs:string($application/@id), xs:string($application/app:path))}
		</li>
};


<html>
    <head>
		<script type='text/javascript' src='../scripts/jquery/jquery-1.3.2.min.js'></script>
		<script type='text/javascript' src='scripts/menu.js'></script>
		<link rel="stylesheet" href="css/menu_style.css" type="text/css" media="screen" />
	</head>
    <body>
    	<ul id="nav">
    	{local:applications("aea0e743-f7eb-400c-a0f0-61d8436ca59e")}
    	</ul>
    	<br></br>
    	<br></br>
    	<br></br>
    	<div>FUTURE DATABASE ADMINISTRATION PANEL</div>
    	<div>{xdb:get-current-user()}</div>
    </body>
</html>