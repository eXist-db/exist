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
<!-- Generates this module's test conf.xml from canonical, see schema/generate-conf-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../schema/generate-conf-fixture.xsl"/>

    <xsl:param name="keep-modules" as="xs:string*" select="(
        'http://exist-db.org/xquery/console',
        'http://exist-db.org/xquery/inspection',
        'http://exist-db.org/xquery/request',
        'http://exist-db.org/xquery/response',
        'http://exist-db.org/xquery/securitymanager',
        'http://exist-db.org/xquery/session',
        'http://exist-db.org/xquery/system',
        'http://exist-db.org/xquery/transform',
        'http://exist-db.org/xquery/util',
        'http://exist-db.org/xquery/validation',
        'http://exist-db.org/xquery/websocket',
        'http://exist-db.org/xquery/xmldb',
        'http://www.w3.org/2005/xpath-functions/array',
        'http://www.w3.org/2005/xpath-functions/map',
        'http://www.w3.org/2005/xpath-functions/math'
    )"/>

    <xsl:param name="catalog-uri" as="xs:string?"
        select="'${project.build.testOutputDirectory}/org/exist/validation/catalog.xml'"/>

    <xsl:param name="content-file-pool-size" as="xs:string?" select="'-1'"/>

    <!-- XQSuite isn't a canonical builtin module; it's registered by hand in test fixtures. -->
    <xsl:param name="extra-modules" as="element()*">
        <module xmlns="" uri="http://exist-db.org/xquery/xqsuite"
            src="resource:org/exist/xquery/lib/xqsuite/xqsuite.xql"/>
    </xsl:param>

</xsl:stylesheet>
