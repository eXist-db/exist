xquery version "1.0";
(: $Id$ :)
(:
    Database Administration panel
:)

declare namespace admin = "http://exist-db.org/administration";

import module namespace app = "http://exist-db.org/application" at "application.xqm";

declare namespace xdb = "http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes omit-xml-declaration=no";

let $id := "aea0e743-f7eb-400c-a0f0-61d8436ca59e"
return
<html>
    <head>
		<script type='text/javascript' src='../scripts/jquery/jquery-1.3.2.min.js'></script>
		<script type='text/javascript' src='scripts/menu.js'></script>
		<link rel="stylesheet" href="css/menu_style.css" type="text/css" media="screen" />
	</head>
    <body>
    	<div>{xdb:get-current-user()}</div>
    	<br/>
    	{app:menuSubapplications($id)}
    	<br/>
    	{app:title($id)}
    	<br/>
    	{app:tableSubapplications($id)}
    </body>
</html>