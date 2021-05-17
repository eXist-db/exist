<?xml version="1.0" encoding="UTF-8"?>
<!--

    eXist-db Open Source Native XML Database
    Copyright (C) 2001 The eXist-db Authors

    info@exist-db.org
    http://www.exist-db.org

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">
    <xsl:output method="xml" omit-xml-declaration="no" doctype-public="-//Jetty//Configure//EN" doctype-system="http://www.eclipse.org/jetty/configure_9_3.dtd" indent="yes"/>
    <xsl:template match="Set[@name eq 'monitoredDirName']">
        <xsl:copy><xsl:copy-of select="@*"/><xsl:copy-of select="Property[@name eq 'jetty.base']"/>/etc/jetty/<xsl:copy-of select="Property[@name eq 'jetty.deploy.monitoredDir']"/></xsl:copy>
    </xsl:template>
    <xsl:template match="Set[@name eq 'defaultsDescriptor']">
        <xsl:copy><xsl:copy-of select="@*"/><xsl:copy-of select="Property[@name eq 'jetty.home']"/>/etc/jetty/webdefault.xml</xsl:copy>
    </xsl:template>
    <xsl:template match="Set[@name eq 'war' and SystemProperty/Default/Property[@name eq 'jetty.home']]">
        <xsl:copy><xsl:copy-of select="@*"/><xsl:copy-of select="SystemProperty/Default/Property[@name eq 'jetty.home']"/>/etc/<xsl:value-of select="tokenize(SystemProperty/Default/text(),'/')[last() - 1]"/></xsl:copy>
    </xsl:template>
    <xsl:template match="Property[@name = ('jetty.sslContext.keyStorePath', 'jetty.sslContext.trustStorePath')]">
        <xsl:copy><xsl:copy-of select="@*[local-name(.) ne 'default']"/><xsl:attribute name="default" select="'etc/jetty/keystore.p12'"/></xsl:copy>
    </xsl:template>
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>