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
		<script type='text/javascript' src='jquery'/>
		<script type='text/javascript' src='scripts/menu.js'/>
		<link rel="stylesheet" href="css/menu_style.css" type="text/css" media="screen" />
	</head>
    <body>
    	<div>{app:title($id)}</div>
    	<div>Name: {xdb:get-current-user()}</div>
    	<div>ID: {xdb:get-current-user-attribute('id')}</div>
    	<div>Email: {xdb:get-current-user-attribute('email')}</div>
    	<div>Country: {xdb:get-current-user-attribute('country')}</div>
    	<div>Language: {xdb:get-current-user-attribute('language')}</div>
    	<br/>
    	{app:menuSubapplications($id)}
    	<br/>
    	<br/>
    	{app:tableSubapplications($id)}
    </body>
</html>
