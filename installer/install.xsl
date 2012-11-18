<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:repo="http://exist-db.org/xquery/repo"
    xmlns:expath="http://expath.org/ns/pkg"
    xmlns:str="http://exslt.org/strings"
    version="3.0">
    
    <xsl:output indent="yes"/>
    
    <xsl:param name="project-name"/>
    <xsl:param name="project-version"/>
    
    <xsl:param name="apps"/>

    <xsl:template match="packs">
        <packs>
            <xsl:apply-templates/>
            <xsl:for-each select="str:tokenize($apps, ';')">
                <xsl:call-template name="app">
                    <xsl:with-param name="file" select="."/>
                </xsl:call-template>
            </xsl:for-each>
        </packs>
    </xsl:template>
    
    <xsl:template match="appname">
        <appname><xsl:value-of select="$project-name"/></appname>
    </xsl:template>
    
    <xsl:template match="appversion">
        <appversion><xsl:value-of select="$project-version"/></appversion>
    </xsl:template>
    
    <xsl:template name="app">
        <xsl:param name="file"/>
        <xsl:variable name="name" select="substring-before($file, '.xar')"/>
        <xsl:variable name="package" select="document($file)//expath:package"/>
        <pack name="{$package/@abbrev}" required="no" preselected="yes" parent="Apps">
            <description><xsl:value-of select="$package/expath:title"/></description>
            <fileset targetdir="$INSTALL_PATH/autodeploy" dir="installer/apps">
                <include name="{$package/@abbrev}*.xar"/>
            </fileset>
        </pack>
    </xsl:template>
    
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>