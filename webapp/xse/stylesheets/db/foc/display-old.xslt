<?xml version="1.0" encoding="iso-8859-1" ?>
<!-- $Header$ -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
       xmlns:t = "http://wwbota.free.fr/taxonomy"
       exclude-result-prefixes="t"
       xmlns="http://www.w3.org/1999/xhtml" 
       version="1.0" >

<!-- useless for Mozilla, and IE doesn't understand: 
<xsl:ouput method="html" /> 
-->

<xsl:template match="xml-source">
  <dl style="">
    <xsl:apply-templates mode="wwbota-classic"/>
  </dl>
</xsl:template>

  <xsl:template match="flora">
   <html>
     <head><title> WWBKB taxon description </title></head>
     <div>
       <xsl:apply-templates mode="wwbota-classic" />
     </div>
   </html>
  </xsl:template> 

  <!-- This is a simple identity function -->
  <xsl:template match="@*|*|processing-instruction()|comment()">
   <xsl:copy>
    <xsl:apply-templates select="*|@*|text()|processing-instruction()|comment()" />
   </xsl:copy>
  </xsl:template>

  <xsl:template match="tr" mode="wwbota-classic" priority='10' >
      <xsl:variable name="FOCTaxonKey" select='normalize-space(td[1])'/>
      <b><a href="http://flora.huh.harvard.edu:8080/flora/browse.do?flora_id=2&amp;taxon_id={$FOCTaxonKey}" title="Browse Taxon on official Site">FOC Taxon key: <xsl:value-of select='td[1] '/></a></b>
   <p>
    <xsl:apply-templates mode="wwbota-classic" />
   </p>
  </xsl:template> 

  <xsl:template match="t:f" priority='1.9' mode="wwbota-classic" >
    <xsl:call-template name="manage-highlight-container" />
    <xsl:call-template name="manage-comma" />
  </xsl:template>

  <xsl:template name="manage-highlight-container" >
    <xsl:choose>
      <xsl:when test="@class = 'highlight-container'">
        <span class='highlight-container' >
          <xsl:apply-templates mode="wwbota-classic" />
        </span>
      </xsl:when>
      <xsl:otherwise>
          <xsl:apply-templates mode="wwbota-classic" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- This catches <span class='highlight' > made by highlight-single-word -->
  <xsl:template match="span" mode="wwbota-classic" >
    <xsl:copy-of select="." />
  </xsl:template>

  <xsl:template name="manage-comma" >
    <xsl:variable name="more-low-level" select="position() != last() and (
      following-sibling::t:f or
      following-sibling::t:meas or
      following-sibling::t:num 
    )"
    />
    <xsl:if test="$more-low-level">, </xsl:if>
  </xsl:template>

  <!-- numeric count -->
  <xsl:template match="t:num" priority='2' mode="wwbota-classic" >
    <span style="color:brown" class='num' >
     <xsl:call-template name="manage-highlight-container" />
    </span>
    <xsl:call-template name="manage-comma" />
  </xsl:template>
  <!-- measure with units -->
  <xsl:template match="t:meas" priority='2' mode="wwbota-classic" >
    <i><span style="color:brown" class='meas' >
     <xsl:call-template name="manage-highlight-container" />
    </span></i>
    <xsl:call-template name="manage-comma" />
  </xsl:template>

  <xsl:template match="name" priority='2' mode="wwbota-classic" > 
   <b>
    <xsl:apply-templates mode="wwbota-classic" />
   </b><xsl:text> </xsl:text>
  </xsl:template> 

  <xsl:template match="genus" priority='2' mode="wwbota-classic" > 
   <em>
    <xsl:apply-templates mode="wwbota-classic" />
   </em><xsl:text> </xsl:text>
  </xsl:template> 

  <!-- Second-level organs: match="species/*/*" -->
  <xsl:template match="td/*/*" mode="wwbota-classic">
    <xsl:variable name="content">
    <xsl:variable name="preceding-low-level" select="
      preceding-sibling::t:f or
      preceding-sibling::t:meas or
      following-sibling::t:num 
    "
    />
    <xsl:if test="$preceding-low-level">; </xsl:if>
    <xsl:apply-templates mode="wwbota-classic" />
    </xsl:variable><!-- name="content" -->
    <xsl:choose>
      <xsl:when test="@class = 'highlight'">
        <span style="background-color:yellow" class='highlight' >
          <xsl:copy-of select="$content" />
        </span>
      </xsl:when>
      <xsl:otherwise>
        <xsl:copy-of select="$content" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Organ names, qualifiers -->
  <xsl:template match="td/*/t:n" mode="wwbota-classic" priority="10">
   <span style="color:blue" class="organ-name" >
    <xsl:apply-templates mode="wwbota-classic" />
   </span><xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template match="td/*/*/t:n" mode="wwbota-classic" priority="10">
   <span style="color:green" class="sub-organ-name" >
    <xsl:apply-templates mode="wwbota-classic" />
   </span><xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template match="t:q" mode="wwbota-classic" >
   <span style="color:green" class="organ-qualifier" >
    <i>
    <xsl:call-template name="manage-highlight-container" />
    <xsl:text> </xsl:text></i>
   </span>
  </xsl:template>

  <!-- First-level organs: match="species/*" -->
  <xsl:template match="td/*" mode="wwbota-classic">
    <xsl:variable name="content">
      <xsl:text> </xsl:text>
      <xsl:apply-templates mode="wwbota-classic" />.
    </xsl:variable><!-- name="content" -->
    <xsl:choose>
      <xsl:when test="@class">
        <span style="background-color:#ffffcc" class='{@class}' >
          <xsl:copy-of select="$content" />
        </span>
      </xsl:when>
      <xsl:otherwise>
        <xsl:copy-of select="$content" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template> 

  <!-- Suppress unwanted cells in the top-level table -->
  <xsl:template match="td[ (.='null') or (string(number(.)) != 'NaN') ]" mode="wwbota-classic" />
  <xsl:template match="th"  mode="wwbota-classic" />

</xsl:stylesheet> 

