<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m="http://www.loc.gov/mods/v3"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    xmlns:java="http://xml.apache.org/xslt/java"
    version="1.0">
    
    <xsl:template match="m:titleInfo[not(@type)]">
        <span class="title">
            <xsl:for-each select="m:nonSort|m:title">
                <xsl:value-of select="."/><xsl:text> </xsl:text>
            </xsl:for-each>
        </span>
    </xsl:template>
    
    <xsl:template match="m:abstract">
        <xsl:if test="string-length(text()) &gt; 0">
            <p class="keywords">
                <span class="heading">Abstract: </span>
                <xsl:apply-templates/>
            </p>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="m:name">
        <xsl:if test="position() != 1">
            <xsl:text>; </xsl:text>
        </xsl:if>
        <xsl:choose>
            <xsl:when test="m:namePart[not(@type)]">
                <a href="?field=au&amp;query={java:java.net.URLEncoder.encode(m:namePart[not(@type)], 'UTF-8')}&amp;mode1=near&amp;max={ancestor::items/@max}">
                    <xsl:apply-templates select="m:namePart[not(@type)]"/>
                </a>
            </xsl:when>
            <xsl:when test="m:namePart[@type='family']">
                <xsl:variable name="name">
                    <xsl:value-of select="m:namePart[@type='family']"/>, <xsl:value-of select="m:namePart[@type='given']"/>
                </xsl:variable>
                <a href="?field=au&amp;query={java:java.net.URLEncoder.encode($name, 'UTF-8')}&amp;mode1=near&amp;max={ancestor::items/@max}">
                    <xsl:apply-templates select="m:namePart[@type='family']"/>
                    <xsl:text>, </xsl:text>
                    <xsl:apply-templates select="m:namePart[@type='given']"/>
                </a>
            </xsl:when>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="m:topic|m:geographic">
        <xsl:if test="position() != 1">
            <xsl:text>; </xsl:text>
        </xsl:if>
        <a href="?field=su&amp;query={java:java.net.URLEncoder.encode(text(), 'UTF-8')}&amp;max={ancestor::items/@max}">
            <xsl:value-of select="."/>
        </a>
    </xsl:template>
</xsl:stylesheet>
