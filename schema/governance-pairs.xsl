<?xml version="1.0" encoding="UTF-8"?>
<!-- Shared: pom.xml validationSets → template/schema paths. -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:param name="pom-uri"/>

    <xsl:variable name="sets"
        select="document($pom-uri)/*[local-name()='project']/*[local-name()='build']
            /*[local-name()='plugins']/*[local-name()='plugin']
            [*[local-name()='artifactId']='xml-maven-plugin']
            /*[local-name()='executions']/*[local-name()='execution']
            [*[local-name()='id']='validate-canonical-instances']
            /*[local-name()='configuration']/*[local-name()='validationSets']
            /*[local-name()='validationSet']"/>

    <xsl:template name="rel">
        <xsl:param name="p"/>
        <xsl:value-of select="substring-after($p, '${project.basedir}/')"/>
    </xsl:template>

    <xsl:template name="schema-path">
        <xsl:param name="set"/>
        <xsl:call-template name="rel">
            <xsl:with-param name="p" select="$set/*[local-name()='systemId']"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="template-path">
        <xsl:param name="set"/>
        <xsl:param name="include"/>
        <xsl:variable name="dir">
            <xsl:call-template name="rel">
                <xsl:with-param name="p" select="$set/*[local-name()='dir']"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:value-of select="concat($dir, '/', $include)"/>
    </xsl:template>

</xsl:stylesheet>
