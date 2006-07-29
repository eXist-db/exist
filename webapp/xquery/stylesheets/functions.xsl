<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:exist="http://exist.sourceforge.net/NS/exist" version="1.0">

    <xsl:template match="builtin-functions">
        <ul>
            <xsl:for-each select="module">
                <li>
                    <a href="#{generate-id()}">
                        <xsl:value-of select="description"/>
                    </a>
                </li>
            </xsl:for-each>
        </ul>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="module">
        <h2 class="modulename">
            <a>
                <xsl:attribute name="name">
                    <xsl:value-of select="generate-id()"/>
                </xsl:attribute>
            </a>
            <xsl:value-of select="description"/>
        </h2>
        <h3 class="moduleuri">
            <xsl:value-of select="@namespace"/>
        </h3>
        <xsl:apply-templates select="function"/>
    </xsl:template>

    <xsl:template match="function">
        <div class="function">
            <h4 class="functionhead">
                <a name="{@name}"/>
                <xsl:value-of select="@name"/>
            </h4>
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="prototype">
        <div class="prototype">
            <p class="signature">
                <xsl:value-of select="signature"/>
            </p>
            <xsl:apply-templates select="description"/>
            <xsl:apply-templates select="deprecated"/>
        </div>
    </xsl:template>

    <xsl:template match="description">
        <p class="description">
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="deprecated">
        <p class="deprecated">
            <xsl:apply-templates/>
        </p>
    </xsl:template>
    
    <xsl:template match="node()|@*" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
