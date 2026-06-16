<?xml version="1.0" encoding="UTF-8"?>
<!-- Given changed paths, emit schema paths that need base @version (one per line). -->
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:g="http://exist-db.org/ns/schema-governance"
    xmlns:exsl="http://exslt.org/common"
    extension-element-prefixes="exsl"
    exclude-result-prefixes="g exsl">

    <xsl:output method="text" encoding="UTF-8"/>
    <xsl:include href="governance-pairs.xsl"/>

    <xsl:variable name="changed" select="/g:changed/g:path"/>

    <xsl:variable name="paired">
        <xsl:for-each select="$sets">
            <xsl:variable name="set" select="."/>
            <xsl:variable name="schema">
                <xsl:call-template name="schema-path">
                    <xsl:with-param name="set" select="$set"/>
                </xsl:call-template>
            </xsl:variable>
            <xsl:for-each select="$set/*[local-name()='includes']/*[local-name()='include']">
                <xsl:variable name="template">
                    <xsl:call-template name="template-path">
                        <xsl:with-param name="set" select="$set"/>
                        <xsl:with-param name="include" select="."/>
                    </xsl:call-template>
                </xsl:variable>
                <xsl:if test="$changed[. = $schema or . = $template]">
                    <schema><xsl:value-of select="$schema"/></schema>
                </xsl:if>
            </xsl:for-each>
        </xsl:for-each>
    </xsl:variable>

    <xsl:variable name="all">
        <xsl:copy-of select="exsl:node-set($paired)/schema"/>
        <xsl:for-each select="$changed[starts-with(., 'schema/') and contains(., '.xsd')
            and not(starts-with(., 'schema/governance-'))]">
            <xsl:variable name="sp" select="."/>
            <xsl:if test="not(exsl:node-set($paired)/schema[. = $sp])">
                <schema><xsl:value-of select="$sp"/></schema>
            </xsl:if>
        </xsl:for-each>
    </xsl:variable>

    <xsl:template match="/">
        <xsl:for-each select="exsl:node-set($all)/schema[not(. = preceding::schema)]">
            <xsl:value-of select="."/>
            <xsl:text>&#10;</xsl:text>
        </xsl:for-each>
    </xsl:template>

</xsl:stylesheet>
