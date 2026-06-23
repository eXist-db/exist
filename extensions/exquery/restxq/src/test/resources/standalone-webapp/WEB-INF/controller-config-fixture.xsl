<?xml version="1.0" encoding="UTF-8"?>
<!-- Generates this module's test controller-config.xml, see schema/generate-controller-config-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../../../../../schema/generate-controller-config-fixture.xsl"/>

    <xsl:param name="keep-forwards" as="xs:string*" select="('/rest', '/restxq/')"/>

    <!-- This test routes both /rest and /servlet to the same servlet, unlike the template's plain /rest. -->
    <xsl:param name="rest-forward-pattern" as="xs:string?" select="'/(rest|servlet)/'"/>

    <!-- This test serves the default app from the filesystem, not from /db, unlike the template's roots. -->
    <xsl:param name="root-elements" as="element()*">
        <root xmlns="http://exist.sourceforge.net/NS/exist" pattern="/apps" path="xmldb:exist:///db/apps"/>
        <root xmlns="http://exist.sourceforge.net/NS/exist" pattern=".*" path="/"/>
    </xsl:param>

</xsl:stylesheet>
