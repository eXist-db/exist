<?php
	header("Content-Type: text/html");
	header("Access-control: allow <*>");
	echo "<?xml version=\"1.0\"?>";
	echo "<html xmlns=\"http://www.w3.org/1999/xhtml\">";
	echo "<head>";
	echo "<title>Results from echo.php</title>";
	echo "</head>";
	echo "<body>";
	echo "<h1>Form posted data</h1>";
	$posteddata = file_get_contents("php://input");
	$posteddata = str_replace("&", "&amp;", $posteddata);
	$posteddata = str_replace("<", "&lt;", $posteddata);
	$posteddata = str_replace(">", "&gt;", $posteddata);
	echo "<pre>$posteddata</pre>";
	echo "<h1>Environment variables</h1>";
	echo "<pre>";
	$env = array_merge($_SERVER, $_ENV);
	ksort($env);
	foreach($env as $key => $value) {
		echo "$key=$value\n";
	}
	echo "</pre>";
	echo "</body>";
	echo "</html>";
?>
