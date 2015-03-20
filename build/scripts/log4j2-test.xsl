<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs"
    version="1.0">
    
    <xsl:output indent="yes" encoding="UTF-8"/>
    
    <!-- strip output to STDOUT -->
    <xsl:template match="Logger[@name = 'org.exist.jetty.JettyStart' or @name = 'org.exist.jetty.StandaloneServer']">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="node()[local-name(.) = 'AppenderRef'][@ref != 'STDOUT']"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
    
</xsl:stylesheet>