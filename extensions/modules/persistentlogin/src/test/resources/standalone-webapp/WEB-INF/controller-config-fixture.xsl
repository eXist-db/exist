<?xml version="1.0" encoding="UTF-8"?>
<!-- Generates this module's test controller-config.xml, see schema/generate-controller-config-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../../../../../schema/generate-controller-config-fixture.xsl"/>

    <xsl:param name="keep-forwards" as="xs:string*" select="('/rest', '/xmlrpc')"/>

</xsl:stylesheet>
