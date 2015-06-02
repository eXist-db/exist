<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- $Id$ -->
    <!-- Convert log4j.xml for use in war-file -->
    <xsl:output method="xml"/>
    
    <xsl:template match="Properties/Property[@name='logs']">
        <Property name="logs">../logs</Property>
    </xsl:template>

    <xsl:template match="*|@*|node()|comment()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()|comment()"/>
        </xsl:copy>
    </xsl:template>

</xsl:transform>
