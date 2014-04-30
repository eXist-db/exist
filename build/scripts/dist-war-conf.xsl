<?xml version="1.0" encoding="UTF-8"?>
<!-- $Id$ -->
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Convert conf.xml for use in war-file -->
    <xsl:output method="xml"/>

    <xsl:template match="db-connection/@files">
        <xsl:attribute name="files">data</xsl:attribute>
    </xsl:template>

    <xsl:template match="recovery/@journal-dir">
        <xsl:attribute name="journal-dir">data</xsl:attribute>
    </xsl:template>

    <xsl:template match="catalog/@file">
        <xsl:attribute name="file">catalog.xml</xsl:attribute>
    </xsl:template>

    <xsl:template match="*|@*|node()|comment()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()|comment()"/>
        </xsl:copy>
    </xsl:template>

</xsl:transform>
