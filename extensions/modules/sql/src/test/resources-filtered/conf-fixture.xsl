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

    <!-- Canonical's sql module element has no children; supply the parameterized version via
         extra-modules. keep-modules stays empty (base default) so the bare canonical entry
         isn't also kept, which would produce a duplicate registration. -->
    <xsl:param name="extra-modules" as="element()*">
        <module xmlns="" uri="http://exist-db.org/xquery/sql" class="org.exist.xquery.modules.sql.SQLModule">
            <parameter name="pool.1.name" value="pool-1"/>
            <parameter name="pool.1.properties.dataSourceClassName" value="org.h2.jdbcx.JdbcDataSource"/>
            <parameter name="pool.1.properties.dataSource.url" value="jdbc:h2:mem:test-pool-1"/>
            <parameter name="pool.1.properties.dataSource.user" value="sa"/>
            <parameter name="pool.1.properties.dataSource.password" value="sa"/>
        </module>
    </xsl:param>

</xsl:stylesheet>
