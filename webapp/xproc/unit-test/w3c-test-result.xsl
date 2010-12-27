<xsl:stylesheet
 xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
 xmlns:fn='http://www.w3.org/2003/05/xpath-functions'
 xmlns:c="http://www.w3.org/ns/xproc-step"
 xmlns='http://xproc.org/ns/testreport'
 version="1.0" >

<xsl:output indent="yes"/>

<xsl:template match ='tests'>
    <test-report xmlns='http://xproc.org/ns/testreport'>
    <title>XProc Test Results eXist XProc (xprocxq)</title>
    <date>2010-12-23T09:55:12</date>
    <processor>
    <name>eXist XProc</name>
    <vendor>James Fuller</vendor>
    <vendor-uri>http://xproc.net/xproc</vendor-uri>
    <version>1.5.1</version>
    <language>en_US</language>
    <xproc-version>1.0</xproc-version>
    <xpath-version>1.0</xpath-version>
    <psvi-supported>false</psvi-supported>
    </processor>
    <test-suite>
            <xsl:apply-templates select="//test"/>
    </test-suite>
    </test-report>
</xsl:template>

<xsl:template match="test">


    <xsl:choose>
        <xsl:when test="./@pass eq 'true'">
            <pass uri="{@uri}">
                <title><xsl:value-of select="@title"/></title>
            </pass>
        </xsl:when>
        <xsl:otherwise>
           <fail uri="{@uri}">
                <title><xsl:value-of select="@title"/></title>
           </fail>
        </xsl:otherwise>
    </xsl:choose></xsl:template>
</xsl:stylesheet>
