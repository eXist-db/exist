<xsl:stylesheet version="2.0"
                xmlns:html="http://www.w3.org/1999/xhtml"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xf="http://www.w3.org/2002/xforms"
                xmlns:ev="http://www.w3.org/2001/xml-events"
                xmlns:ts="http://www.w3c.org/MarkUp/Forms/XForms/Test/11"
                exclude-result-prefixes="html ev xsl xf">

    <xsl:output method="xml" encoding="UTF-8"/>

    <xsl:template match="/">
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*|@*|text()">
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="duration">
        <hours><xsl:value-of select="@hours"/></hours>
        <minutes><xsl:value-of select="@minutes"/></minutes>
    </xsl:template>

</xsl:stylesheet>
