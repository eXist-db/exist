<xsl:stylesheet
 xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
 xmlns:fn='http://www.w3.org/2003/05/xpath-functions'
 version="2.0" >

<xsl:output indent="yes"/>


<xsl:template match ='/'>
    <test-report xmlns='http://xproc.org/ns/testreport'>
    <title>XProc Test Results for XML Calabash</title>
    <date>2009-12-01T09:55:12</date>
    <processor>
    <name>eXist XProc</name>
    <vendor>James Fuller</vendor>
    <vendor-uri>http://xproc.net/xproc</vendor-uri>
    <version>1.5.0</version>
    <language>en_US</language>
    <xproc-version>1.0</xproc-version>
    <xpath-version>1.0</xpath-version>
    <psvi-supported>false</psvi-supported>
    </processor>
    <test-suite>
            <xsl:apply-templates select="test"/>
    </test-suite>
    </test-report>
</xsl:template>

<xsl:template match="test">
    <xsl:copy/>
    <!--xsl:choose>
        <xsl:when test="result and expected">
            <xsl:choose>
                <xsl:when test="contains(result,expected)">
                    <pass uri="{name}">
                        <title>{name}</title>
                    </pass>
                </xsl:when>
                <xsl:otherwise>
                    <fail uri="{name}">
                        <title>{name}</title>
                     </fail>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:when>
        <xsl:otherwise>
                   <fail uri="{name}">
                        <title>{name}</title>
                   </fail>
        </xsl:otherwise>
    </xsl:choose-->
</xsl:template>
</xsl:stylesheet>
