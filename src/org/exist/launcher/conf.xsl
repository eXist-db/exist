<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:param name="cacheSize"/>
    <xsl:param name="collectionCache"/>
    <xsl:param name="dataDir"/>

    <xsl:template match="db-connection/@cacheSize">
        <xsl:attribute name="cacheSize"><xsl:value-of select="$cacheSize"/>M</xsl:attribute>
    </xsl:template>

    <xsl:template match="db-connection/@collectionCache">
        <xsl:attribute name="collectionCache"><xsl:value-of select="$collectionCache"/>M</xsl:attribute>
    </xsl:template>

    <xsl:template match="db-connection/@files">
        <xsl:attribute name="files">
            <xsl:value-of select="$dataDir"/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="recovery/@journal-dir">
        <xsl:attribute name="journal-dir">
            <xsl:value-of select="$dataDir"/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>