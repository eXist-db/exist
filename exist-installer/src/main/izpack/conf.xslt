<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        version="2.0">
    <xsl:param name="dataDir" as="xs:string" required="yes"/>
    <xsl:output omit-xml-declaration="no" indent="yes" encoding="UTF-8" method="xml"/>
    <xsl:template match="db-connection[@files]">
        <xsl:copy>
            <xsl:copy-of select="@*[local-name() ne 'files']"/><xsl:attribute name="files" select="$dataDir"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="recovery[@journal-dir]">
        <xsl:copy>
            <xsl:copy-of select="@*[local-name() ne 'journal-dir']"/><xsl:attribute name="journal-dir" select="$dataDir"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>