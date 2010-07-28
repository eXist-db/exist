<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<!-- $Id: sort-instance.xsl,v 1.4 2006/03/21 19:24:57 uli Exp $ -->
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xf="http://www.w3.org/2002/xforms"
	xmlns:bf="http://betterform.sourceforge.net/xforms">

    <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
    <xsl:strip-space elements="*"/>

	<xsl:template match="/">
		<xsl:apply-templates />
	</xsl:template>

	<xsl:template match="*|@*|text()">
        <xsl:copy>
            <xsl:apply-templates select="*|@*|text()"/>
        </xsl:copy>
    </xsl:template>


	<xsl:template match="bf:data"/>
	<xsl:template match="xf:group[@appearance='repeated']"/>
	<xsl:template match="@src"/>


	<xsl:template match="xf:repeat" priority="5">
		<xsl:variable name="index" select="bf:data/@bf:index"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:attribute name="bf:index"><xsl:value-of select="$index"/></xsl:attribute>
			<xsl:apply-templates select="bf:data/xf:group/*"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="xf:case">
		<xsl:copy>
			<xsl:variable name="selected" select="bf:data/@bf:selected"/>
			<xsl:attribute name="selected"><xsl:value-of select="$selected"/></xsl:attribute>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>


</xsl:stylesheet>
