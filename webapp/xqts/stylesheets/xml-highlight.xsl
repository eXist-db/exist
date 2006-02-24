<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    version="1.0">
    
    <xsl:preserve-space elements="*"/>
    <xsl:output indent="no"/>
    
    <xsl:template match="item">
        <div>
            <xsl:attribute name="class">
                <xsl:choose>
                    <xsl:when test="@num mod 2 = 0">even</xsl:when>
                    <xsl:otherwise>uneven</xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <div class="pos"><xsl:value-of select="@num"/></div>
            <div class="item"><xsl:apply-templates/></div>
        </div>
    </xsl:template>
    
    <xsl:template match="text()">
        <xsl:value-of select="."/>
    </xsl:template>
    
    <xsl:template match="processing-instruction()">
        <dd>
            <font color="darkred">&lt;?<xsl:value-of select="."/>?&gt;</font>
        </dd>
    </xsl:template>
    
    <xsl:template match="comment()">
        <div class="xml-comment">
            &lt;-- <xsl:value-of select="."/> --&gt;
        </div>
    </xsl:template>
    
    <xsl:template match="@*">
        <span class="xml-attr-name" xml:space="preserve">
            <xsl:text> </xsl:text>
            <xsl:value-of select="name(.)"/>
        </span><xsl:text>="</xsl:text>
        <span class="xml-attr-value">
            <xsl:value-of select="."/>
        </span>
        <xsl:text>"</xsl:text>
    </xsl:template>
    
    <xsl:template match="text()">
        <span class="xml-text"><xsl:value-of select="."/></span>
    </xsl:template>
    
    <xsl:template match="exist:match">
        <span class="xml-match">
            <xsl:value-of select="."/>
        </span>
    </xsl:template>
    
    <xsl:template match="*">
        <div class="xml-element">
            <span class="xml-element-tag"><xsl:text>&lt;</xsl:text></span>
            <span class="xml-element-name"><xsl:value-of select="name()"/></span>
            <xsl:apply-templates select="@*"/>
            <xsl:choose>
                <xsl:when test="count(node()) &gt; 0">
                    <span class="xml-element-tag">&gt;</span>
                    <xsl:apply-templates select="node()|comment()"/>
                    <span class="xml-element-tag">&lt;/</span>                    
                    <span class="xml-element-name"><xsl:value-of select="name()"/></span>
                    <span class="xml-element-tag">&gt;</span>
                </xsl:when>
                <xsl:otherwise>
                    <span class="xml-element-tag">/&gt;</span>
                </xsl:otherwise>
            </xsl:choose>
        </div>
    </xsl:template>
</xsl:stylesheet>