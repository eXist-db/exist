<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:bf="http://betterform.sourceforge.net/xforms"
    exclude-result-prefixes="bf xsl">

    <xsl:param name="root" select="'../../../../../../src/main/xforms'"/>

    <!--
    Simple Stylesheet to assemble XForms documents from markup found in other files.

    Syntax for includes:
    <bf:include src="[path]#[id]/>

    where [path] is the relative path to the file to be included (basedir is determined by $rootDir global var)
          [id] is some element in the file identified by [filename] that has a matching id Attribute

    -->
    <xsl:strip-space elements="*"/>
    <xsl:template match="/">
<!--
        <xsl:message>processing include directives.....</xsl:message>
        <xsl:message>inclusion root: <xsl:value-of select="$root"/></xsl:message>
-->
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates />
        </xsl:copy>
    </xsl:template>

    <xsl:template match="bf:include">
        <!--<xsl:variable name="file" select="concat($root,substring-before(@src,'#'))"/>-->
        <xsl:variable name="file">
            <xsl:choose>
                <xsl:when test="string-length(substring-before(@src,'#')) &gt; 0"><xsl:value-of select="substring-before(@src,'#')"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="@src"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <!--<xsl:message>File is <xsl:value-of select="$file"/></xsl:message>-->

        <xsl:variable name="resource">
            <xsl:choose>
                <xsl:when test="exists(/*[@xml:base])">
                    <xsl:value-of select="concat(/*/@xml:base,$file)"/>
                </xsl:when>
                <xsl:when test="starts-with($file,'http')">
                    <xsl:value-of select="$file"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="concat($root,$file)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <!--<xsl:message>including URI:<xsl:value-of select="$resource"/></xsl:message>-->

        <xsl:variable name="fragmentId" select="substring-after(@src,'#')"/>
        <xsl:choose>
            <xsl:when test="string-length($fragmentId) &gt;0">
                <xsl:apply-templates select="doc($resource)//*[@id=$fragmentId]"/>
            </xsl:when>
            <xsl:when test="string-length($resource) &gt;0">
                <xsl:apply-templates select="doc($resource)/*[1]"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message terminate="yes">inclusion failed. Attribute src for bf:include does not point to an existing file</xsl:message>    
            </xsl:otherwise>
        </xsl:choose>

    </xsl:template>

</xsl:stylesheet>
