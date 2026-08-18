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
    Generates this module's runtime conf.xml from canonical, see schema/generate-conf-fixture.xsl.

    This replaces the previous approach of transforming
    extensions/indexes/indexes-integration-tests/src/test/resources-filtered/conf.xml (adding the
    plain range index on top): that file was never a real static file, only the *input* to that
    module's own fixture codegen, so this module's build silently produced no conf.xml at all
    once indexes-integration-tests switched to the same canonical-fixture mechanism used here
    (every benchmark failed instantly with DatabaseConfigurationException). Going straight to
    canonical avoids depending on another module's private fixture input a second time.

    Unlike the standard test-scoped fixtures (src/test/resources-filtered/conf-fixture.xsl,
    generated to target/generated-test-resources), this module's benchmarks live under
    src/main/java and are packaged into the shaded benchmarks jar, so the generated conf.xml
    needs to land on the MAIN resources path instead. See the module's own pom.xml for the
    non-standard xml-maven-plugin wiring (generate-resources phase, main <resources>).
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="urn:exist-db:codegen:generate-conf-fixture"/>

    <xsl:param name="keep-modules" as="xs:string*" select="(
        'http://exist-db.org/xquery/lucene',
        'http://exist-db.org/xquery/ngram',
        'http://exist-db.org/xquery/range',
        'http://exist-db.org/xquery/inspection',
        'http://exist-db.org/xquery/response',
        'http://exist-db.org/xquery/securitymanager',
        'http://exist-db.org/xquery/system',
        'http://exist-db.org/xquery/util',
        'http://exist-db.org/xquery/xmldb',
        'http://www.w3.org/2005/xpath-functions/array',
        'http://www.w3.org/2005/xpath-functions/map'
    )"/>

    <xsl:param name="keep-indexes" as="xs:string*" select="('lucene-index', 'ngram-index', 'range-index')"/>

</xsl:stylesheet>
