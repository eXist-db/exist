<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!--
        AUTHOR: Lars Huttar
        MODIFIED: Fabian Otto <otto@betterform.de>
        DATE: 2009-09-14
        DESCIPTION:  Pretty prints XML to HTML.
        Escaped XML is wrapped into <pre> and <code>. All tags are escaped
        using &lt; and &gt;
    -->

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:variable name="nl">
        <xsl:text> </xsl:text>
    </xsl:variable>
    <xsl:variable name="indent-increment" select="' '"/>

    <xsl:template name="write-starttag">
        <xsl:text>&lt;</xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:for-each select="@*">
            <xsl:call-template name="write-attribute"/>
        </xsl:for-each>
        <xsl:if test="not(*|text()|comment()|processing-instruction())">/</xsl:if>
        <xsl:text>&gt;</xsl:text>
    </xsl:template>

    <xsl:template name="write-endtag">
        <xsl:text>&lt;/</xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:text>&gt;</xsl:text>
    </xsl:template>

    <xsl:template name="write-attribute">
        <xsl:text> </xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:text>="</xsl:text>
        <xsl:value-of select="."/>
        <xsl:text>"</xsl:text>
    </xsl:template>

    <xsl:template match="*">
        <xsl:param name="indent-string" select="$indent-increment"/>
        <xsl:value-of select="$indent-string"/>
        <xsl:call-template name="write-starttag"/>
        <xsl:if test="*">
            <xsl:value-of select="$nl"/>
        </xsl:if>
        <xsl:apply-templates>
            <xsl:with-param name="indent-string" select="concat($indent-string, $indent-increment)"/>
        </xsl:apply-templates>
        <xsl:if test="*">
            <xsl:value-of select="$indent-string"/>
        </xsl:if>
        <xsl:if test="*|text()|comment()|processing-instruction()">
            <xsl:call-template name="write-endtag"/>
        </xsl:if>
        <xsl:value-of select="$nl"/>
    </xsl:template>

      <!-- wraps xml (instance data) in HTML <pre> and <code> tags. -->
    <xsl:template match="/">
        <data>
            <xsl:text disable-output-escaping="yes">&lt;![CDATA[</xsl:text>
            <pre><code id='jsText'><xsl:text>&#x0A;</xsl:text>
                    <xsl:apply-templates select="*"/>
                </code></pre>
            <xsl:text disable-output-escaping="yes">]]&gt;</xsl:text>
        </data>
    </xsl:template>

</xsl:stylesheet>