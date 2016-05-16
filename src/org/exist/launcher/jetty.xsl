<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <xsl:output indent="no"/>
    <xsl:preserve-space elements="*"/>
    <xsl:strip-space elements="Set"/>
    
    <xsl:param name="port">8080</xsl:param>
    <xsl:param name="port.ssl">8443</xsl:param>
    
    <xsl:template match="SystemProperty[@name='jetty.port']"><SystemProperty name="jetty.port" default="{$port}"/></xsl:template>

    <xsl:template match="SystemProperty[@name='jetty.port.ssl']"><SystemProperty name="jetty.port.ssl" default="{$port.ssl}"/></xsl:template>
    
    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>