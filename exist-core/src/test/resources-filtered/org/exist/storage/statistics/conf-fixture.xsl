<?xml version="1.0" encoding="UTF-8"?>
<!-- Generates this module's test conf.xml from canonical, see schema/generate-conf-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../../../../../schema/generate-conf-fixture.xsl"/>

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

    <!-- Canonical comments index-stats out by default; supply the live element ourselves. -->
    <xsl:param name="extra-index-modules" as="element()*">
        <module xmlns="" id="index-stats" file="stats.dbx" class="org.exist.storage.statistics.IndexStatistics"/>
    </xsl:param>

</xsl:stylesheet>
