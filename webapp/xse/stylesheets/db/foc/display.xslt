<?xml version="1.0" encoding="iso-8859-1" ?>
<!-- $Header$ -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
       xmlns:t = "http://wwbota.free.fr/taxonomy"
       exclude-result-prefixes="t"
       xmlns="http://www.w3.org/1999/xhtml" 
       version="1.0" >

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
  <xsl:template match="@*|*|processing-instruction()|comment()" priority='-2' >
   <xsl:copy>
    <xsl:apply-templates select="*|@*|text()|processing-instruction()|comment()" />
   </xsl:copy>
  </xsl:template>

  <!-- This is a simple identity function -->
  <xsl:template match="@*|*|processing-instruction()|comment()" priority='-2' mode="wwbota-classic" >
   <xsl:copy>
    <xsl:apply-templates select="*|@*|text()|processing-instruction()|comment()" mode="wwbota-classic" />
   </xsl:copy>
  </xsl:template>

  <!-- Rubrics: -->
  <xsl:template match="t:description" mode="wwbota-classic" >
    <h3>Description</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" mode="wwbota-classic" />
  </xsl:template>
  <xsl:template match="key" mode="wwbota-classic" >
    <h3>Key</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="comm1" mode="wwbota-classic" >
    <h3>Comments</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="comm2" mode="wwbota-classic" >
    <h3>Comments 2</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="comm3" mode="wwbota-classic" >
    <h3>Comments 3</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="habitat" mode="wwbota-classic" >
    <h3>Habitat</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="distribution" mode="wwbota-classic" >
    <h3>Distribution</h3>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>

  <xsl:template match="taxon_date" mode="wwbota-classic" >
    <h4>Taxon date</h4>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="common_name" mode="wwbota-classic" >
    <h4>Common name</h4>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="country" mode="wwbota-classic" >
    <h4>Country</h4>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>
  <xsl:template match="region" mode="wwbota-classic" >
    <h4>Region</h4>
    <xsl:apply-templates select="*|text()|processing-instruction()|comment()" />
  </xsl:template>

  <xsl:template match="*[local-name()='taxon_id'][1]" mode="wwbota-classic" >
      <xsl:variable name="FOCTaxonKey" select='normalize-space(.)'/>
      <xsl:variable name="name" select="../* [local-name()='name']"/>
      <b><a href="http://flora.huh.harvard.edu:8080/flora/browse.do?flora_id=2&amp;taxon_id={$FOCTaxonKey}" title="Browse Taxon on official Site"> <xsl:value-of select='$name'/> (FOC Taxon key: <xsl:value-of select='$FOCTaxonKey'/>)</a> </b>
  </xsl:template>

  <xsl:template match="*[local-name()='rank_id']" mode="wwbota-classic" >
    rank_id:  <xsl:value-of select='.'/>; 
  </xsl:template>
  <xsl:template match="*[local-name()='taxon_date']" mode="wwbota-classic" >
    taxon date:  <xsl:value-of select='.'/>; 
  </xsl:template>

  <xsl:template match="t:taxon" mode="wwbota-classic" priority='10' >
   <p>
    <xsl:apply-templates mode="wwbota-classic" />
   </p>
  </xsl:template> 

  <!-- suppress all this -->
  <xsl:template match="*[local-name()='name' or
                         local-name()='authority_id' or
                         local-name()='publication_id' or 
                         local-name()='taxon_page' or
                         local-name()='category_id' or
                         local-name()='tropicos_id'
]" mode="wwbota-classic" />
  <xsl:template match="*[local-name()='taxon_id'] [position() >= 2]" mode="wwbota-classic" />

  <!-- description processing -->
  <xsl:template match="t:f" priority='1.9' mode="wwbota-classic" >
  <!-- <xsl:call-template name="manage-highlight-container" /> -->
    <xsl:apply-templates />
    <xsl:call-template name="manage-comma" />
  </xsl:template>

  <!-- unused -->
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
  <xsl:template match="t:num" priority='3' mode="wwbota-classic" >
    <xsl:text> </xsl:text>
    <span style="color:brown" class='num' >
     <!-- <xsl:call-template name="manage-highlight-container" /> -->
     <xsl:apply-templates />
    </span>
    <xsl:text> </xsl:text>
    <xsl:call-template name="manage-comma" />
  </xsl:template>

  <!-- measure with units -->
  <xsl:template match="t:meas" priority='2' mode="wwbota-classic" >
    <i><span style="color:brown" class='meas' >
     <!-- <xsl:call-template name="manage-highlight-container" /> -->
     <xsl:apply-templates />
    </span></i>
    <xsl:call-template name="manage-comma" />
  </xsl:template>

<!--
  <xsl:template match="name" priority='2' mode="wwbota-classic" > 
   <b>
    <xsl:apply-templates mode="wwbota-classic" />
   </b><xsl:text> </xsl:text>
  </xsl:template> 
-->

  <xsl:template match="genus" priority='2' mode="wwbota-classic" > 
   <em>
    <xsl:apply-templates mode="wwbota-classic" />
   </em><xsl:text> </xsl:text>
  </xsl:template> 

  <!-- Second-level organs: match="species/*/*" -->
  <xsl:template match="t:description/*/*" mode="wwbota-classic" priority="2" >
    <xsl:variable name="content">
      <xsl:variable name="preceding-low-level" select="
        preceding-sibling::t:f or
        preceding-sibling::t:meas or
        preceding-sibling::t:num or
        following-sibling::t:num 
      " />
      <xsl:if test="$preceding-low-level">; </xsl:if>
      <xsl:apply-templates mode="wwbota-classic" />
    </xsl:variable>
    <xsl:copy-of select="$content" />
  </xsl:template>

  <!-- Organ names, qualifiers -->
  <xsl:template match="t:description/*/t:n" mode="wwbota-classic" priority="10">
   <span style="color:blue" class="organ-name" >
    <xsl:apply-templates mode="wwbota-classic" />
   </span><xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template match="t:description/*/*/t:n" mode="wwbota-classic" priority="10">
   <span style="color:green" class="sub-organ-name" >
    <xsl:apply-templates mode="wwbota-classic" />
   </span><xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template match="t:q" mode="wwbota-classic" >
   <span style="color:green" class="organ-qualifier" >
    <i>
     <!-- <xsl:call-template name="manage-highlight-container" /> -->
     <xsl:apply-templates />
     <xsl:text> </xsl:text></i>
   </span>
  </xsl:template>

  <!-- First-level organs: match="species/*" -->
  <xsl:template match="t:description/*" mode="wwbota-classic" priority="2" >
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
  <xsl:template match="t:description/*[ (.='null') or (string(number(.)) != 'NaN') ]" mode="wwbota-classic"  priority='-3'/>
  <xsl:template match="th"  mode="wwbota-classic" />

</xsl:stylesheet> 

