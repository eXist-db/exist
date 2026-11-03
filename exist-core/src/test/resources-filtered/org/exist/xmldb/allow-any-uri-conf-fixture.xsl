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
<!--
    DbStore2Test fixture: xmldb with allowAnyUri=true (canonical defaults to false).
    Other modules are SPI-registered. See schema/generate-conf-fixture.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="urn:exist-db:codegen:generate-conf-fixture"/>

    <!-- keep-modules empty so canonical's xmldb (allowAnyUri=false) is not also kept. -->
    <xsl:param name="extra-modules" as="element()*">
        <module xmlns="" uri="http://exist-db.org/xquery/xmldb"
            class="org.exist.xquery.functions.xmldb.XMLDBModule">
            <parameter name="allowAnyUri" value="true"/>
        </module>
    </xsl:param>

</xsl:stylesheet>
