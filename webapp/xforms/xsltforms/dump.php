<html xmlns="http://www.w3.org/1999/xhtml">
	<head>
		<title>HTTP XML POST Dump</title>
	</head>
	<body>
		<h1>HTTP XML POST Dump</h1>
		<h2>Raw Data :</h2>
		<?php
		$body = file_get_contents("php://input");
		echo strlen($body);
		echo " bytes: <br/>";
		echo "<pre>$body</pre>";
		if(substr($body,0,9) == "postdata=") {
			$body = urldecode(substr($body,strpos($body,"=")+1));
		}
		$xml = new DOMDocument(); 
		$xml->loadXML($body); 
		$xslt = new XSLTProcessor(); 
		$xsl = new DOMDocument();
		$indent = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\"><xsl:output method=\"xml\" indent=\"yes\" encoding=\"UTF-8\"/><xsl:template match=\"@*|node()\"><xsl:copy-of select=\".\"/></xsl:template></xsl:stylesheet>";
		$xsl->loadXML($indent); 
		$xslt->importStylesheet($xsl);
		$result = $xslt->transformToXml($xml);
		$result = substr($result, strpos($result,"?>")+3);
		echo "<h2>Indented XML :</h2><pre>".htmlspecialchars($result, ENT_QUOTES)."</pre>"; 
		?>
	</body>
</html>
