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
<!-- Generates this module's test controller-config.xml, see schema/generate-controller-config-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="urn:exist-db:codegen:generate-controller-config-fixture"/>

    <xsl:param name="keep-forwards" as="xs:string*" select="('/rest', '/restxq/')"/>

    <!-- This test routes both /rest and /servlet to the same servlet, unlike the template's plain /rest. -->
    <xsl:param name="rest-forward-pattern" as="xs:string?" select="'/(rest|servlet)/'"/>

    <!-- This test serves the default app from the filesystem, not from /db, unlike the template's roots. -->
    <xsl:param name="root-elements" as="element()*">
        <root xmlns="http://exist.sourceforge.net/NS/exist" pattern="/apps" path="xmldb:exist:///db/apps"/>
        <root xmlns="http://exist.sourceforge.net/NS/exist" pattern=".*" path="/"/>
    </xsl:param>

</xsl:stylesheet>
