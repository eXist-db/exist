<?xml version="1.0" encoding="UTF-8"?>
<!-- $Id$ -->
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Convert log4j.xml for use in war-file -->
    <xsl:output method="xml" doctype-system="log4j.dtd"/>
    
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
