<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:g="http://exist-db.org/ns/schema-governance"
    exclude-result-prefixes="g">
    <xsl:output method="text"/>
    <xsl:template match="g:error">
        <xsl:text>::error file=</xsl:text><xsl:value-of select="@path"/>
        <xsl:text>::</xsl:text><xsl:value-of select="@message"/>
        <xsl:text>&#10;</xsl:text>
    </xsl:template>
</xsl:stylesheet>
