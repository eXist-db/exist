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
    Shared base for generating test/sample conf.xml fixtures from the canonical
    exist-distribution/src/main/config/conf.xml. A per-fixture stylesheet imports this one and
    redeclares the xsl:param defaults below (higher import precedence wins) to select which
    builtin modules and index modules that fixture needs; everything else is copied unchanged
    from canonical via the identity template, so fixtures stay current automatically as canonical
    gains new parser/serializer/security defaults.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <!--
        Per-fixture overrides. A fixture stylesheet redeclares any of these with its own
        xsl:param/select; the importing stylesheet's value wins over this base's default.
    -->

    <!--
        xquery/builtin-modules/module@uri values this fixture needs to keep in the output.
        LIVE ELEMENTS ONLY: entries commented out in canonical are not visible to the XPath
        match and cannot be "kept" this way.  For modules absent or commented out in canonical
        (e.g. xqsuite, vector), use $extra-modules instead.
    -->
    <xsl:param name="keep-modules" as="xs:string*" select="()"/>

    <!--
        indexer/modules/module@id values this fixture needs (e.g. 'sort-index').
        LIVE ELEMENTS ONLY: same caveat as $keep-modules — entries commented out in canonical
        (e.g. spatial-index, which needs an optional GML/HSQL dependency) cannot be selected
        here.  Supply the live element via $extra-index-modules for those.
    -->
    <xsl:param name="keep-indexes" as="xs:string*" select="()"/>

    <!--
        db-connection/@files and recovery/@journal-dir path.
        Maven property tokens (${basedir}, ${project.build.directory}, etc.) are safe here:
        the generated conf.xml is further processed by Maven's testResource filtering.
        AVT pitfall: if you embed a Maven token inside a literal-result-element ATTRIBUTE that
        is also an XSLT AVT (curly braces), double each brace so XSLT does not treat them as
        its own expression syntax — e.g. value="${{project.build.testOutputDirectory}}/dir"
        produces ${project.build.testOutputDirectory}/dir after serialisation, which Maven
        then expands.  Plain attribute content (not inside {…}) passes through unchanged.
    -->
    <xsl:param name="data-path" as="xs:string" select="'${basedir}/target/test-data'"/>

    <!-- validation/catalog/@uri; empty means "keep canonical's value". -->
    <xsl:param name="catalog-uri" as="xs:string?" select="()"/>

    <!-- content-file-pool/@size; empty means "keep canonical's value". -->
    <xsl:param name="content-file-pool-size" as="xs:string?" select="()"/>

    <!--
        Extra db-connection/startup/triggers elements to append after canonical's surviving
        triggers (e.g. an AutoDeploymentTrigger scoped to a test-only XAR directory).
        AVT reminder: Maven tokens inside element attribute values that are XSLT AVTs require
        doubled braces — ${{project.build.testOutputDirectory}} → ${project.build.testOutputDirectory}.
    -->
    <xsl:param name="extra-triggers" as="element()*" select="()"/>

    <!--
        Extra indexer/modules/module elements to append.  Use this for index modules that are
        commented out in canonical ($keep-indexes cannot un-comment them — live elements only)
        or for modules whose canonical element needs child configuration that the bare entry
        does not carry.
    -->
    <xsl:param name="extra-index-modules" as="element()*" select="()"/>

    <!--
        Extra xquery/builtin-modules/module elements to append.  Use this for:
        — modules commented out in canonical (e.g. xqsuite, vector) that $keep-modules cannot
          select;
        — modules whose canonical <module> needs child <parameter> elements that the bare
          self-closing canonical entry does not have (e.g. the SQL connection-pool module).
          In that case keep $keep-modules empty so the bare canonical entry is NOT also kept,
          which would produce a duplicate registration.
    -->
    <xsl:param name="extra-modules" as="element()*" select="()"/>

    <!-- Identity transform: copy everything from canonical unless overridden below. -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="db-connection/@files | recovery/@journal-dir">
        <xsl:attribute name="{local-name()}" select="$data-path"/>
    </xsl:template>

    <xsl:template match="catalog/@uri[exists($catalog-uri)]">
        <xsl:attribute name="uri" select="$catalog-uri"/>
    </xsl:template>

    <xsl:template match="content-file-pool/@size[exists($content-file-pool-size)]">
        <xsl:attribute name="size" select="$content-file-pool-size"/>
    </xsl:template>

    <!-- Keep only the builtin XQuery modules this fixture's tests actually exercise. -->
    <xsl:template match="builtin-modules/module[not(@uri = $keep-modules)]"/>

    <xsl:template match="builtin-modules">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xsl:copy-of select="$extra-modules"/>
        </xsl:copy>
    </xsl:template>

    <!-- Keep only the index extension modules this fixture's tests actually exercise. -->
    <xsl:template match="indexer/modules/module[not(@id = $keep-indexes)]"/>

    <xsl:template match="indexer/modules">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xsl:copy-of select="$extra-index-modules"/>
        </xsl:copy>
    </xsl:template>

    <!--
        Canonical's RestXq/AutoDeployment startup triggers assume a full webapp deployment, a
        RESTXQ registry, an autodeploy directory, neither present nor wanted in an isolated test
        fixture, so they're dropped by default; restored per-fixture via $extra-triggers if needed.
    -->
    <xsl:template match="trigger[@class = 'org.exist.extensions.exquery.restxq.impl.RestXqStartupTrigger']"/>
    <xsl:template match="trigger[@class = 'org.exist.repo.AutoDeploymentTrigger']"/>

    <xsl:template match="db-connection/startup/triggers">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xsl:copy-of select="$extra-triggers"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
