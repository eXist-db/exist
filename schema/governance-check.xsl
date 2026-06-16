<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:g="http://exist-db.org/ns/schema-governance"
    xmlns:exsl="http://exslt.org/common"
    extension-element-prefixes="exsl"
    exclude-result-prefixes="g exsl">

    <xsl:output method="xml" indent="yes"/>
    <xsl:param name="pom-uri"/>
    <xsl:include href="governance-pairs.xsl"/>

    <xsl:variable name="ctx" select="/g:ctx"/>
    <xsl:variable name="workspace" select="$ctx/@workspace"/>
    <xsl:variable name="changed" select="$ctx/g:changed"/>

    <xsl:variable name="paired">
        <xsl:for-each select="$sets">
            <schema>
                <xsl:call-template name="schema-path">
                    <xsl:with-param name="set" select="."/>
                </xsl:call-template>
            </schema>
        </xsl:for-each>
    </xsl:variable>

    <xsl:template match="/">
        <xsl:variable name="errors">
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
                    <xsl:if test="$changed[@path = $schema or @path = $template]">
                        <xsl:call-template name="fail-if-unchanged">
                            <xsl:with-param name="schema" select="$schema"/>
                            <xsl:with-param name="what">
                                <xsl:choose>
                                    <xsl:when test="$changed[@path = $schema and @path = $template]">Schema and template</xsl:when>
                                    <xsl:when test="$changed[@path = $schema]">Schema</xsl:when>
                                    <xsl:otherwise>
                                        <xsl:value-of select="concat('Template ', $template)"/>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:if>
                </xsl:for-each>
            </xsl:for-each>
            <xsl:for-each select="$changed[starts-with(@path, 'schema/') and contains(@path, '.xsd')
                and not(starts-with(@path, 'schema/governance-'))]">
                <xsl:variable name="sp" select="@path"/>
                <xsl:if test="not(exsl:node-set($paired)/schema[. = $sp])">
                    <xsl:call-template name="fail-if-unchanged">
                        <xsl:with-param name="schema" select="$sp"/>
                        <xsl:with-param name="what" select="'Schema'"/>
                    </xsl:call-template>
                </xsl:if>
            </xsl:for-each>
        </xsl:variable>
        <g:report>
            <xsl:attribute name="status">
                <xsl:choose>
                    <xsl:when test="count(exsl:node-set($errors)/g:error) &gt; 0">failed</xsl:when>
                    <xsl:otherwise>passed</xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:copy-of select="exsl:node-set($errors)/g:error"/>
        </g:report>
    </xsl:template>

    <xsl:template name="fail-if-unchanged">
        <xsl:param name="schema"/>
        <xsl:param name="what"/>
        <xsl:variable name="old" select="normalize-space($ctx/g:old-version[@path = $schema])"/>
        <xsl:variable name="head" select="normalize-space(string(document(concat($workspace, '/', $schema))/*[local-name()='schema']/@version))"/>
        <xsl:choose>
            <xsl:when test="not($head)">
                <g:error path="{$schema}" message="Missing xs:schema/@version (see schema/README.md)"/>
            </xsl:when>
            <xsl:when test="not($old)"/>
            <xsl:when test="$old = $head">
                <g:error path="{$schema}">
                    <xsl:attribute name="message">
                        <xsl:value-of select="$what"/>
                        <xsl:text> changed but @version is still "</xsl:text>
                        <xsl:value-of select="$head"/>
                        <xsl:text>". Bump xs:schema/@version (see schema/README.md).</xsl:text>
                    </xsl:attribute>
                </g:error>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
