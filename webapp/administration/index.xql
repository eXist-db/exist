xquery version "1.0";
(: $Id$ :)
(:
    Database Administration panel
:)

declare namespace admin = "http://exist-db.org/administration";

import module namespace app = "http://exist-db.org/application" at "application.xqm";

declare namespace xdb = "http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes omit-xml-declaration=no";

<html>
    <head>
		<script type='text/javascript' src='../scripts/jquery/jquery-1.3.2.min.js'></script>
		<script type='text/javascript' src='scripts/menu.js'></script>
		<link rel="stylesheet" href="css/menu_style.css" type="text/css" media="screen" />
	</head>
    <body>
    	{app:menuSubapplications("aea0e743-f7eb-400c-a0f0-61d8436ca59e")}
    	<br/>
    	{app:title("aea0e743-f7eb-400c-a0f0-61d8436ca59e")}
    	<br/>
    	{app:tableSubapplications("aea0e743-f7eb-400c-a0f0-61d8436ca59e")}
    	<br></br>
    	<div>FUTURE DATABASE ADMINISTRATION PANEL</div>
    	<div>{xdb:get-current-user()}</div>
    </body>
</html>