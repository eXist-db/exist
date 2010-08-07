<p:pipeline xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:c="http://www.w3.org/ns/xproc-step"
xmlns:p="http://www.w3.org/ns/xproc" name="pipeline">
<p:input port="source" primary="true">
<p:inline>
<html xmlns="test.t">
<body>
<h1>It Worked</h1>
<p>I was passed through an XSLT stylesheet.</p>
</body>
</html>
</p:inline>
</p:input>
<p:xslt>
<p:input port="stylesheet">
<p:inline>
<xsl:stylesheet xmlns:t="test.t" version="1.0">
<xsl:template match="/">
<html>
<body>
<h1>
<xsl:value-of
select="/t:html/t1:body/t:h1/text()"/>
</h1>
</body>
</html>
</xsl:template>
</xsl:stylesheet>
</p:inline>
</p:input>
</p:xslt>
</p:pipeline>