<?xml version="1.0" encoding="UTF-8"?>
<!-- $Id$ -->
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Convert log4j2.xml for use in war-file -->
    <xsl:output method="xml"/>
  
    <xsl:template match="Property[@name='logs']">
        <Property name="logs">${web:rootDir}/WEB-INF/logs</Property>
    </xsl:template>
    
    <xsl:template match="category[@name='org.mortbay']">
    </xsl:template>

    <xsl:template match="appender/param[@name='File']">
        <param name="File" value="loggerdir/{substring-after(@value,'logs/')}"/>
    </xsl:template>

    <xsl:template match="*|@*|node()|comment()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()|comment()"/>
        </xsl:copy>
    </xsl:template>

</xsl:transform>
