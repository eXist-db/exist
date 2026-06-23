<?xml version="1.0" encoding="UTF-8"?>
<!-- Generates this module's test conf.xml from canonical, see schema/generate-conf-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../../../schema/generate-conf-fixture.xsl"/>

    <xsl:param name="keep-modules" as="xs:string*" select="(
        'http://exist-db.org/xquery/spatial',
        'http://exist-db.org/xquery/util'
    )"/>

    <xsl:param name="keep-indexes" as="xs:string*" select="('spatial-index')"/>

    <!-- Canonical comments spatial-index out by default; supply the live element ourselves. -->
    <xsl:param name="extra-index-modules" as="element()*">
        <module xmlns="" id="spatial-index" connectionTimeout="10000" flushAfter="300"
            class="org.exist.indexing.spatial.GMLHSQLIndex"/>
    </xsl:param>

</xsl:stylesheet>
