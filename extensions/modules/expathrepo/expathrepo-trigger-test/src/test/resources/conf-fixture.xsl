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

    <xsl:import href="../../../../../../../schema/generate-conf-fixture.xsl"/>

    <!-- This test exercises a WAR-relative (not Maven-basedir-relative) data path. -->
    <xsl:param name="data-path" as="xs:string" select="'webapp/WEB-INF/data'"/>

    <!-- A custom, non-canonical example Java module used to test autodeploy/trigger behavior. -->
    <xsl:param name="extra-modules" as="element()*">
        <module xmlns="" uri="https://my-organisation.com/exist-db/ns/app/my-java-module"
            class="org.exist.repo.ExampleModule"/>
    </xsl:param>

</xsl:stylesheet>
