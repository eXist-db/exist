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

<!-- Parameters:
- string: a whitespace-separated string
Output: an <words> element containing <w> elements, with 1 <w> element per word occuring in $string . 
        <w> element are sorted. -->
<xsl:template name="sorted-split-string">
 <xsl:param name="string" />
 <words>
    <xsl:variable name="unsorted-split-string">
     <xsl:call-template name="split-string" >
      <xsl:with-param name="string" >
        <xsl:value-of select="$string" />
      </xsl:with-param>
     </xsl:call-template>
    </xsl:variable>
    <xsl:for-each select="$unsorted-split-string/words/*" >
     <xsl:sort />
     <xsl:copy-of select="." />
    </xsl:for-each>
 </words>
</xsl:template>

<!-- Same usage as "sorted-split-string, but <w> element are not sorted. -->
<xsl:template name="split-string">
 <xsl:param name="string" />
 <words>
   <xsl:variable name="raw-result" >
     <xsl:call-template name="split-string-impl">
      <xsl:with-param name="all" >
       <text>
        <xsl:value-of select="normalize-space($string)" />
       </text>
      </xsl:with-param>
     </xsl:call-template>
   </xsl:variable>
   <xsl:copy-of select="$raw-result/index/*" />
 </words>
</xsl:template>

<!-- Parameters: all: a whitespace-separated string, wrapped by a <text> element, plus optionally an initial index wrapped by an <index> element
Output: an <index> element containing <w> elements -->
<xsl:template name="split-string-impl">
 <xsl:param name="all" />
 <!-- First word in $all: -->
 <xsl:variable name="first" >
   <xsl:variable name="first0" select="substring-before($all/text,' ')" />
   <xsl:choose>
     <xsl:when test="$first0" >
       <xsl:copy-of select="$first0" />
     </xsl:when>
     <xsl:otherwise>
       <xsl:copy-of select="$all/text" />
     </xsl:otherwise>
   </xsl:choose>
 </xsl:variable>

 <!-- Inspired from M. Kay, 2nd edition p. 186 -->
 <!-- <xsl:message>DEBUG <xsl:copy-of select="$all" />, $first="<xsl:value-of select="$first"/>"</xsl:message> -->
 <xsl:choose>
  <xsl:when test="string-length($first)" >
<!--
   <xsl:choose>
    <xsl:when test="contains( $all/index, $first)" >
     <xsl:call-template name="split-string-impl">
      <xsl:with-param name="all" >
        <text>
         <xsl:value-of select="substring-after($all/text,' ')" />
        </text>
        <xsl:copy-of select="$all/index" />
      </xsl:with-param>
     </xsl:call-template>
    </xsl:when>
    <xsl:otherwise>
-->
     <xsl:call-template name="split-string-impl">
      <xsl:with-param name="all" >
        <text>
         <xsl:value-of select="substring-after($all/text,' ')" />
        </text>
        <index>
         <xsl:copy-of select="$all/index/*" />
         <xsl:text>
         </xsl:text> 
         <w><xsl:value-of select="$first" /></w>
        </index>
      </xsl:with-param>
     </xsl:call-template>
<!--
    </xsl:otherwise>
   </xsl:choose>
-->
  </xsl:when>
  <xsl:otherwise>
   <xsl:copy-of select="$all"/>
  </xsl:otherwise>
 </xsl:choose>

</xsl:template>
</xsl:stylesheet> 
