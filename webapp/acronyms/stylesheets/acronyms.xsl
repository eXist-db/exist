<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xmlad="http://www.xml-acronym-demystifier.org"
  xmlns:exist="http://exist.sourceforge.net/NS/exist"
  version="1.0">
  
  	<xsl:preserve-space elements="*"/>
  	
  	<xsl:template match="entries">
  		<table border="0" width="100%" cellspacing="15" cellpadding="10">
  			<xsl:apply-templates select="xmlad:Entry"/>
  		</table>
    </xsl:template>
   
  	<xsl:template match="xmlad:Entry">
  		<tr bgcolor="F3F3F3">
  			<td  class="entry">
  				<xsl:apply-templates select="xmlad:Acronym"/> 
  					<xsl:if test="xmlad:Acronym/@type">
  						(Type: <xsl:value-of select="xmlad:Acronym/@type"/>)
  					</xsl:if>
  					<br/>
  				<div class="expansion"><xsl:value-of select="xmlad:Acronym/@expansion"/></div>
  				
  				<p><xsl:apply-templates select="xmlad:Definition"/></p>
  				
  				<xsl:if test="xmlad:AlternateForms">
  					<p><b>Alternate Forms</b>: 
  						<xsl:for-each select="xmlad:AlternateForms/xmlad:AlternateForm">
  							<xsl:if test="position() &gt; 1">
  								<xsl:text>; </xsl:text>
  							</xsl:if>
  							<xsl:apply-templates select="."/>
  						</xsl:for-each>
  					</p>
  				</xsl:if>
  				
  				<xsl:if test="xmlad:MoreInfo">
  					<p><b>More Info</b>: <a href="{xmlad:MoreInfo/text()}">
  						<xsl:apply-templates select="xmlad:MoreInfo"/></a>
  					</p>
  				</xsl:if>
  				
  				<xsl:if test="xmlad:SpecLocs|xmlad:SpecLoc">
  					<b>Specifications:</b><br/>
  					<ul>
  					<xsl:for-each select=".//xmlad:SpecLoc">
  						<li><a href="{text()}"><xsl:value-of select="."/></a></li>
  					</xsl:for-each>
  					</ul>
  				</xsl:if>
  			</td>
  		</tr>
  	</xsl:template>
  	
  	<xsl:template match="xmlad:a">
  		<xsl:text> </xsl:text>
  		<xsl:choose>
  			<xsl:when test="@URIRef">
  				<a href="{@URIRef}"><xsl:apply-templates/></a>
  			</xsl:when>
  			<xsl:when test="@glossRef">
  				<a href="?field=acronym&amp;term={@glossRef}">
  					<xsl:apply-templates/>
  				</a>
  			</xsl:when>
  		</xsl:choose>
  		<xsl:text> </xsl:text>
  	</xsl:template>
  	
  	<xsl:template match="exist:match">
		<span style="background-color: #FFFF00"><xsl:apply-templates/></span>
	</xsl:template>

  	<xsl:template match="node()|@*" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*"/>
		</xsl:copy>
	</xsl:template>
	
</xsl:stylesheet>
