<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  
  version="1.0">
  
    <!-- the following templates pretty-print xml source code.
         All xml content contained between the xml-source tags
         is pretty printed.
    -->
    <xsl:template match="xml-source">
      <dl style="font-family=Courier,monospace">
        <xsl:apply-templates mode="xmlsrc"/>
      </dl>
    </xsl:template>
    
    <xsl:template match="text()" mode="xmlsrc">
      <xsl:if test="normalize-space(.)!=''">
        <dd>
          <xsl:value-of select="."/>
        </dd>
      </xsl:if>
    </xsl:template>
    
    <xsl:template match="processing-instruction()" mode="xmlsrc">
      <dd>
        <font color="darkred">&lt;?<xsl:value-of select="."/>?&gt;</font>
      </dd>
    </xsl:template>
    
    <xsl:template match="comment()" mode="xmlsrc">
      <dd>
        <font color="grey">&lt;-- <xsl:value-of select="."/> --&gt;</font>
      </dd>
    </xsl:template>
    
    <xsl:template match="@*" mode="xmlsrc">
      <xsl:text>
      </xsl:text>
      <xsl:choose>
        <xsl:when test="not(namespace-uri(.)='')">
          <font color="green">
            <xsl:value-of select="name(.)"/>
          </font>
        </xsl:when>
        <xsl:otherwise>
          <font color="darkred">
            <xsl:value-of select="name(.)"/>
          </font>
        </xsl:otherwise>
      </xsl:choose>
      ="<font color="navy"><xsl:value-of select="."/></font>"
    </xsl:template>
    
    <xsl:template match="*" mode="xmlsrc">
      <dd>
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
        <xsl:apply-templates select="@*" mode="xmlsrc"/>
        <xsl:choose>
          <xsl:when test="*">
            <font color="navy">
              &gt;
            </font>
            <dl>
              <xsl:apply-templates select="node()" mode="xmlsrc"/>
            </dl>
            <font color="navy">
              &lt;/
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
            <font color="navy">
              &gt;
            </font>
          </xsl:when>
          <xsl:when test="text()">
            <font color="navy">
              &gt;
            </font>
            <font color="black">
              <xsl:value-of select="text()"/>
            </font>
            <font color="navy">
              &lt;/
              <xsl:value-of select="name()"/>
              &gt;
            </font>
          </xsl:when>
          <xsl:otherwise>
            <font color="navy">
              /&gt;
            </font>
          </xsl:otherwise>
        </xsl:choose>
      </dd>
    </xsl:template>
</xsl:stylesheet>
