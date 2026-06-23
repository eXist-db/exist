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
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA

-->
<!--
    Shared base for generating test/sample controller-config.xml fixtures from the standalone-webapp
    variant of controller-config.xml (exist-jetty-config/src/main/resources/standalone-webapp/WEB-INF/
    controller-config.xml), a deliberately minimal alternative to the full webapp/WEB-INF canonical,
    not derived from it. A per-fixture stylesheet imports this one and redeclares the xsl:param
    defaults below to select which forward rules that fixture's test routing needs; everything else
    is copied unchanged via the identity template.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xpath-default-namespace="http://exist.sourceforge.net/NS/exist"
    exclude-result-prefixes="xsl xs">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <!--
        Per-fixture overrides. A fixture stylesheet redeclares any of these with its own
        xsl:param/select; the importing stylesheet's value wins over this base's default.
    -->

    <!-- forward/@pattern values to keep; default is all 4 the standalone-webapp template has. -->
    <xsl:param name="keep-forwards" as="xs:string*" select="('/rest', '/xmlrpc', '/webdav/', '/restxq/')"/>

    <!-- Override the REST forward's own @pattern (e.g. restxq needs '/(rest|servlet)/'); empty means keep as-is. -->
    <xsl:param name="rest-forward-pattern" as="xs:string?" select="()"/>

    <!-- Override the whole <root> element group; empty means keep the template's own roots. -->
    <xsl:param name="root-elements" as="element()*" select="()"/>

    <!-- Identity transform: copy everything from the template unless overridden below. -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Keep only the forward rules this fixture's test routing actually exercises. -->
    <xsl:template match="forward[not(@pattern = $keep-forwards)]"/>

    <xsl:template match="forward[@pattern = '/rest']/@pattern[exists($rest-forward-pattern)]">
        <xsl:attribute name="pattern" select="$rest-forward-pattern"/>
    </xsl:template>

    <xsl:template match="root[exists($root-elements) and not(preceding-sibling::root)]">
        <xsl:copy-of select="$root-elements"/>
    </xsl:template>

    <xsl:template match="root[exists($root-elements) and preceding-sibling::root]"/>

</xsl:stylesheet>
