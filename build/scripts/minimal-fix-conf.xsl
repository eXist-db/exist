<?xml version="1.0" encoding="UTF-8"?>
<!-- $Id$ -->
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output method="xml"/>

<xsl:template match="cluster/@journalDir">
<xsl:attribute name="journalDir"><xsl:value-of select="substring-after(.,'webapp/WEB-INF/')"/></xsl:attribute>
</xsl:template>

<xsl:template match="db-connection/@files">
<xsl:attribute name="files"><xsl:value-of select="substring-after(.,'webapp/WEB-INF/')"/></xsl:attribute>
</xsl:template>

<xsl:template match="recovery/@journal-dir">
<xsl:attribute name="journal-dir"><xsl:value-of select="substring-after(.,'webapp/WEB-INF/')"/></xsl:attribute>
</xsl:template>

<xsl:template match="catalog/@uri">
<xsl:attribute name="uri">
<xsl:choose>
<xsl:when test="contains(.,'${WEBAPP_HOME}/WEB-INF/')">${EXIST_HOME}/<xsl:value-of select="substring-after(.,'${WEBAPP_HOME}/WEB-INF/')"/></xsl:when>
<xsl:otherwise><xsl:value-of select="."/></xsl:otherwise>
</xsl:choose>
</xsl:attribute>
</xsl:template>


<xsl:template match="@*|node()">
    <xsl:copy>
        <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
</xsl:template>

</xsl:transform>
