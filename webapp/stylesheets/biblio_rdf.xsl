<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:exist="http://exist.sourceforge.net/NS/exist" 
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:x="http://exist.sourceforge.net/dc-ext"
  xmlns:java="http://xml.apache.org/xslt/java"
  version="1.0">	

  <xsl:include href="doc2html-2.xsl"/>
  
  <xsl:template match="exist:result">
    <xsl:if test="exist:queryInfo/@hits">
        <p>Found <xsl:value-of select="exist:queryInfo/@hits"/> hits in
        <xsl:value-of select="exist:queryInfo/@qtime"/> ms.</p>
    </xsl:if>
    <xsl:choose>
        <xsl:when test=".//rdf:Description">
            <p></p>
            <table border="0" cellpadding="0" cellspacing="0" bgcolor="#ffffff" width="100%">
                <tr>
                    <td>
                        <table border="0" cellpadding="0" cellspacing="0" bgcolor="#000000" width="100%">
                            <tr>
                                <td>
                                    <table border="0" cellpadding="7" cellspacing="1" width="100%">                                
                                        <tr bgcolor="#0b88e8">
                                            <td align="left" width="25%">
                                                <xsl:if test="exist:queryInfo/@prev">
                                                    <a href="{exist:queryInfo/@prev}">&lt;&lt;previous</a>
                                                </xsl:if>
                                            </td>
                                            <td align="center" width="25%">
                                                <a href="{exist:queryInfo/@current}&amp;source=true">
                                                    show XML source
                                                </a>                                        
                                            </td>
                                            <td align="center" width="25%">
                                                <a href="biblio.xml">
                                                    new query
                                                </a>                                        
                                            </td>
                                            <td align="right" width="25%">
                                                <xsl:if test="exist:queryInfo/@next">
                                                    <a href="{exist:queryInfo/@next}">more &gt;&gt;</a>
                                                </xsl:if>
                                            </td>
                                        </tr>
                                        <xsl:choose>
                                            <xsl:when test="xml-source">
                                                <tr bgcolor="#ffffff">
                                                    <td align="left" colspan="4">
                                                        <xsl:apply-templates/>
                                                    </td>
                                                </tr>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:apply-templates/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </xsl:when>
        <xsl:otherwise>
            <p>Nothing has been found for your query.</p> 
            <p><a href="biblio.xml">&lt;&lt; back</a></p>
        </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="rdf:Description">
    <tr bgcolor="#ffffff">
      <td align="left" colspan="4">
	    <p>
	    <xsl:apply-templates select="dc:title"/>
		<xsl:if test="dc:creator">
		  <xsl:text>by </xsl:text>
		  <xsl:apply-templates select="dc:creator"/>
		  <xsl:if test="dc:editor">
		    <xsl:text>; </xsl:text>
		    <xsl:apply-templates select="dc:editor"/>
	          </xsl:if>
                  <xsl:if test="dc:contributor">
                    <xsl:text>; </xsl:text>
                    <xsl:apply-templates select="dc:contributor"/>
                  </xsl:if>
		</xsl:if>
		</p>

		<xsl:if test="x:place">
		  <xsl:value-of select="x:place"/>
		  <xsl:if test="dc:date">
		    <xsl:text>: </xsl:text>
		  </xsl:if>
		</xsl:if>
		<xsl:value-of select="dc:date"/>
		<br />

		<xsl:if test="dc:identifier">
		  <xsl:apply-templates select="dc:identifier"/>
		  <br />
		</xsl:if>

		<xsl:if test="dc:subject">
		  <p><em>Keywords: </em>
		  <xsl:apply-templates select="dc:subject"/></p>
		</xsl:if>

		<xsl:if test="dc:description">
		  <p><em>Description: </em> 
		  <xsl:value-of select="dc:description/text()"/></p>
		</xsl:if>

                <p align="right"><em>Source: </em>
                   <xsl:value-of select="@exist:source"/></p>
	  </td>
	</tr>
  </xsl:template>

  <xsl:template match="dc:creator">
    <xsl:if test="position()>1">
      <xsl:text>; </xsl:text>
    </xsl:if>
    <xsl:variable name="creator" select="./text()"/>
    <xsl:variable name="query">document(*)//rdf:Description[near(dc:editor|dc:creator|dc:contributor,'<xsl:value-of select="$creator"/>')]</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query)'/>
    <a href="/exist/rdf_pbib.xsp?start=1&amp;howmany=15&amp;query={$encoded}">
      <xsl:apply-templates/>
    </a>
  </xsl:template>
  	
  <xsl:template match="dc:editor">
    <xsl:if test="position()=1">
      <xsl:text>ed. by </xsl:text>
    </xsl:if>
    <xsl:if test="position()>1">
      <xsl:text>; </xsl:text>
    </xsl:if>
    <xsl:variable name="editor" select="./text()"/>
    <xsl:variable name="query">document(*)//rdf:Description[near(dc:editor|dc:creator|dc:contributor,'<xsl:value-of select="$editor"/>')]</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query)'/>
    <a href="/exist/rdf_pbib.xsp?start=1&amp;howmany=15&amp;query={$encoded}">
      <xsl:apply-templates/>
    </a>
  </xsl:template>

  <xsl:template match="dc:contributor">
    <xsl:if test="position()=1">
      <xsl:text>with contributions by </xsl:text>
    </xsl:if>
    <xsl:if test="position()>1">
      <xsl:text>; </xsl:text>
    </xsl:if>
    <xsl:variable name="contributor" select="./text()"/>
    <xsl:variable name="query">document(*)//rdf:Description[near(dc:editor|dc:creator|dc:contributor,'<xsl:value-of select="$contributor"/>')]</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query)'/>
    <a href="/exist/rdf_pbib.xsp?start=1&amp;howmany=15&amp;query={$encoded}">
		<xsl:apply-templates/>
    </a>
  </xsl:template>

  <xsl:template match="dc:title">
    <b><xsl:apply-templates/></b><br />
  </xsl:template>

  <xsl:template match="dc:subject">
    <xsl:variable name="subject" select="./text()"/>
    <xsl:variable name="query">document(*)//rdf:Description[dc:subject&amp;='<xsl:value-of select='$subject'/>']</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query)'/>
    <a href="/exist/rdf_pbib.xsp?start=1&amp;howmany=15&amp;query={$encoded}">
      <xsl:apply-templates/>
    </a>
    <xsl:if test="position()&lt;last()">
      <xsl:text>; </xsl:text>
    </xsl:if>
  </xsl:template>
    
  <xsl:template match="dc:identifier">
	<xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="exist:match">
  	<span style="background-color: #FFFF00">
  		<xsl:apply-templates/>
  	</span>
  </xsl:template>
</xsl:stylesheet>
