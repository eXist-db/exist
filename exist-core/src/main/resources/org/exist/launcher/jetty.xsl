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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <xsl:output indent="no" doctype-public="-//Jetty//Configure//EN" doctype-system="http://www.eclipse.org/jetty/configure.dtd"/>

    <xsl:preserve-space elements="*"/>
    <xsl:strip-space elements="Set"/>
    
    <xsl:param name="port">8080</xsl:param>
    <xsl:param name="port.ssl">8443</xsl:param>
    
    <xsl:template match="SystemProperty[@name='jetty.port']"><SystemProperty name="jetty.port" default="{$port}"/></xsl:template>

    <xsl:template match="SystemProperty[@name='jetty.ssl.port']"><SystemProperty name="jetty.ssl.port" default="{$port.ssl}"/></xsl:template>
    
    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>