<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    version="1.0">
    
    <xsl:template match="item">
        <div>
            <xsl:attribute name="class">
                <xsl:choose>
                    <xsl:when test="@num mod 2 = 0">even</xsl:when>
                    <xsl:otherwise>uneven</xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <div class="pos"><xsl:value-of select="@num"/></div>
            <xsl:apply-templates/>
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
        <dd>
            <font color="grey">&lt;-- <xsl:value-of select="."/> --&gt;</font>
        </dd>
    </xsl:template>
    <xsl:template match="@*">
        <xsl:text> </xsl:text>
        <xsl:choose>
            <xsl:when test="not(namespace-uri(.)='')">
                <font color="purple">
                    <xsl:value-of select="name(.)"/>
                </font>
            </xsl:when>
            <xsl:otherwise>
                <font color="red">
                    <xsl:value-of select="name(.)"/>
                </font>
            </xsl:otherwise>
        </xsl:choose> ="<font color="lime">
            <xsl:value-of select="."/>
        </font>" 
    </xsl:template>
    <xsl:template match="exist:match">
        <span style="background-color: #FFFF00">
            <xsl:apply-templates/>
        </span>
    </xsl:template>
    <xsl:template match="*">
        <div style="margin-left: 20px">
            <font color="navy">
                <xsl:text>&lt;</xsl:text>
            </font>
            <xsl:choose>
                <xsl:when test="not(namespace-uri()='')">
                    <font color="green">
                        <xsl:value-of select="name()"/>
                    </font>
                </xsl:when>
                <xsl:otherwise>
                    <font color="navy">
                        <xsl:value-of select="name()"/>
                    </font>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:apply-templates select="@*"/>
            <xsl:choose>
                <xsl:when test="exist:match">
                    <font color="navy"> &gt; </font>
                    <xsl:apply-templates/>
                    <font color="navy"> &lt;/ <xsl:value-of select="name()"/> &gt; </font>
                </xsl:when>
                <xsl:when test="text()">
                    <font color="navy"> &gt; </font>
                    <xsl:apply-templates/>
                    <font color="navy"> &lt;/ <xsl:value-of select="name()"/> &gt; </font>
                </xsl:when>
                <xsl:when test="*">
                    <font color="navy"> &gt; </font>
                    <div>
                        <xsl:apply-templates select="node()" />
                    </div>
                    <font color="navy"> &lt;/ </font>
                    <xsl:choose>
                        <xsl:when test="not(namespace-uri()='')">
                            <font color="green">
                                <xsl:value-of select="name()"/>
                            </font>
                        </xsl:when>
                        <xsl:otherwise>
                            <font color="navy">
                                <xsl:value-of select="name()"/>
                            </font>
                        </xsl:otherwise>
                    </xsl:choose>
                    <font color="navy"> &gt; </font>
                </xsl:when>
                <xsl:otherwise>
                    <font color="navy"> /&gt; </font>
                </xsl:otherwise>
            </xsl:choose>
        </div>
    </xsl:template>
</xsl:stylesheet>
