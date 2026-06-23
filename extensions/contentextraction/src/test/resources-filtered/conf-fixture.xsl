<?xml version="1.0" encoding="UTF-8"?>
<!-- Generates this module's test conf.xml from canonical, see schema/generate-conf-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../../schema/generate-conf-fixture.xsl"/>

    <xsl:param name="keep-modules" as="xs:string*" select="(
        'http://exist-db.org/xquery/contentextraction',
        'http://exist-db.org/xquery/inspection',
        'http://exist-db.org/xquery/response',
        'http://exist-db.org/xquery/system',
        'http://exist-db.org/xquery/util',
        'http://exist-db.org/xquery/xmldb',
        'http://www.w3.org/2005/xpath-functions/map'
    )"/>

</xsl:stylesheet>
