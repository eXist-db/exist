<?xml version="1.0" encoding="UTF-8"?>
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
