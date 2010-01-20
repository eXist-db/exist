xquery version "1.0";

declare option exist:serialize "method=xhtml media-type=text/html";

let $a := 0
return
<html>
	<head>
		<title>DB Browser</title>
		<script src='scripts/jquery.js' type='text/javascript'/>
		<script src='scripts/ui.core.js' type='text/javascript'/>
		<script src='scripts/jquery.cookie.js' type='text/javascript'/>
		<script src='scripts/jquery.dynatree.js' type='text/javascript'/>
		<script src='scripts/main.js' type='text/javascript'/>
		<script src='scripts/jquery.dataTables.min.js' type='text/javascript'/>

		<link href='styles/ui.dynatree.css' rel='stylesheet' type='text/css'/>
		<link href='styles/scroll.css' rel='stylesheet' type='text/css'/>
		<link href='styles/demo_table.css' rel='stylesheet' type='text/css'/>
	</head>
		
	<body onload="load()">
		<div class="guide" id="collectionsTree"/>
		<div class="scroll" id="resourcesTable"/>
	</body>
</html>