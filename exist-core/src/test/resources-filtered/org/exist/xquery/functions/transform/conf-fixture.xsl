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

    <xsl:import href="urn:exist-db:codegen:generate-conf-fixture"/>

    <xsl:param name="keep-modules" as="xs:string*" select="(
        'http://exist-db.org/xquery/inspection',
        'http://exist-db.org/xquery/request',
        'http://exist-db.org/xquery/response',
        'http://exist-db.org/xquery/securitymanager',
        'http://exist-db.org/xquery/session',
        'http://exist-db.org/xquery/system',
        'http://exist-db.org/xquery/transform',
        'http://exist-db.org/xquery/util',
        'http://exist-db.org/xquery/validation',
        'http://exist-db.org/xquery/xmldb',
        'http://www.w3.org/2005/xpath-functions/array',
        'http://www.w3.org/2005/xpath-functions/map',
        'http://www.w3.org/2005/xpath-functions/math'
    )"/>

    <xsl:param name="catalog-uri" as="xs:string?"
        select="'${project.build.testOutputDirectory}/org/exist/validation/catalog.xml'"/>

    <!-- These tests need functx autodeployed; canonical's AutoDeploymentTrigger is dropped by
         the base stylesheet (it assumes a full webapp), so it's re-added here, scoped to functx. -->
    <xsl:param name="extra-triggers" as="element()*">
        <trigger xmlns="" class="org.exist.repo.AutoDeploymentTrigger">
            <parameter name="ignore-autodeploy-system-property" value="true"/>
            <parameter name="dir" value="${{project.build.testOutputDirectory}}/functx"/>
        </trigger>
    </xsl:param>

</xsl:stylesheet>
