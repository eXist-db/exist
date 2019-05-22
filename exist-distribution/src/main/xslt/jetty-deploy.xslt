<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">
    <xsl:output method="xml" omit-xml-declaration="no" doctype-public="-//Jetty//Configure//EN" doctype-system="http://www.eclipse.org/jetty/configure_9_3.dtd"/>
    <xsl:template match="Set[@name eq 'monitoredDirName']">
        <xsl:copy><xsl:copy-of select="@*"/><xsl:copy-of select="Property[@name eq 'jetty.base']"/>/etc/jetty/<xsl:copy-of select="Property[@name eq 'jetty.deploy.monitoredDir']"/></xsl:copy>
    </xsl:template>
    <xsl:template match="Set[@name eq 'defaultsDescriptor']">
        <xsl:copy><xsl:copy-of select="@*"/><xsl:copy-of select="Property[@name eq 'jetty.home']"/>/etc/jetty/webdefault.xml</xsl:copy>
    </xsl:template>
    <xsl:template match="Set[@name eq 'war']">
        <xsl:copy><xsl:copy-of select="@*"/><xsl:copy-of select="SystemProperty/Default/Property[@name eq 'jetty.home']"/>/etc/<xsl:value-of select="tokenize(SystemProperty/Default/text(),'/')[last() - 1]"/></xsl:copy>
    </xsl:template>
    <xsl:template match="Property[@name = ('jetty.sslContext.keyStorePath', 'jetty.sslContext.trustStorePath')]">
        <xsl:copy><xsl:copy-of select="@*[local-name(.) ne 'default']"/><xsl:attribute name="default" select="'etc/jetty/keystore'"/></xsl:copy>
    </xsl:template>
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>