<?xml version="1.0" encoding="ISO-8859-1" ?>
<!-- 
Copyright J.M. Vanel 2001-2003 - under GNU public licence 
jmvanel@free.fr 

Worldwide Botanical Knowledge Base 
http://wwbota.free.fr/
$Header$
-->

<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
                version="1.1" >

  <xsl:import href='split-string.xslt' />

  <xsl:template match= "text() [../@class='highlight'] |
                        text() [../@class='highlight-container'][string-length(normalize-space(.)) &gt;0] " >
     <xsl:variable name="contains" select="../@highlight" />
     <!-- DEBUG $contains="<xsl:value-of select="$contains" />" -->
     <xsl:variable name="split-string">
       <xsl:call-template name="split-string">
        <xsl:with-param name="string" select="." />
       </xsl:call-template>
     </xsl:variable>

     <!-- DEBUG "<xsl:value-of select="." />",<xsl:copy-of select="$split-string" /> -->
     <xsl:for-each select="$split-string/*/*">
       <xsl:choose>
         <xsl:when test="contains(., $contains) or $contains = '' " >
           <span class="highlight"><xsl:value-of select="." /></span>
         </xsl:when>
         <xsl:otherwise>
           <xsl:value-of select="." />
         </xsl:otherwise>
       </xsl:choose><xsl:text> </xsl:text>
     </xsl:for-each>
  </xsl:template>

  <xsl:template match="@*|node()" >
    <xsl:copy>
     <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>      
  </xsl:template>

</xsl:stylesheet> 
