xquery version "1.0";

declare option exist:serialize "method=xhtml media-type=text/html";

let $a := 0
return
<html>
	<head>
		<title>DB Browser</title>
		<script type='text/javascript' src='jquery' />
		<script type='text/javascript' src='jquery.ui.full' />
		<script type='text/javascript' src='jquery.cookie' />
		<script type='text/javascript' src='jquery.dynatree' />
		<script type='text/javascript' src='jquery.dataTables' />

		<script type='text/javascript' src='scripts/main.js' />

		<link rel='stylesheet' type='text/css' href='jquery.dynatree/css' />
		<link rel='stylesheet' type='text/css' href='styles/scroll.css' />
		<link rel='stylesheet' type='text/css' href='styles/demo_table.css' />
	</head>
		
	<body onload="load()">
		<div class="guide" id="collectionsTree"/>
		<div class="scroll" id="resourcesTable"/>
	</body>
</html>