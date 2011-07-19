<?xml version="1.0" encoding="UTF-8"?>
<!-- $Id$ -->
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output method="xml" doctype-system='log4j.dtd'/>

<xsl:template match="appender/param[@name='File']">
<param name="File" value="${{exist.home}}/logs/{substring-after(@value,'logs/')}"/>
</xsl:template>

<xsl:template match="@*|node()">
  <xsl:copy>
    <xsl:apply-templates select="@*|node()"/>
  </xsl:copy>
</xsl:template>

</xsl:transform>
