<?xml version="1.0"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fe="http://apache.org/cocoon/fragmentextractor/2.0">
	
	<xsl:template match="//fe:fragment">
		<img src="welcome-svg-images/{@fragment-id}.png" border="0"/>
	</xsl:template>
	
	<xsl:template match="@*|*|text()|processing-instruction()" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="@*|*|text()|processing-instruction()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>
