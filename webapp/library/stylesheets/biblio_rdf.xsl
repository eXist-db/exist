<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:exist="http://exist.sourceforge.net/NS/exist" 
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:x="http://exist.sourceforge.net/dc-ext"
  xmlns:java="http://xml.apache.org/xslt/java"
  xmlns:xmldb="http://exist-db.org/transformer/1.0"
  version="1.0">	
	
  <xsl:include href="context://stylesheets/doc2html-2.xsl"/>
  
  <xsl:template match="xmldb:result-set">
  	<p>
  		Query: <xsl:value-of select="@xpath"/>.
  	</p>
    <xsl:if test="@count">
        <p>Found <xsl:value-of select="@count"/> hits in
        <xsl:value-of select="@query-time"/> ms.</p>
    </xsl:if>
  	<xsl:variable name="from" select="@from"/>
  	<xsl:variable name="to" select="@to"/>
  	<!-- create links to the previous/next set of query results -->
  	<xsl:variable name="next">
  		<xsl:choose>
  			<xsl:when test="$to + 1 &lt;= @count">?query=<xsl:value-of select="java:java.net.URLEncoder.encode(normalize-space(@xpath), 'UTF-8')"/>&amp;from=<xsl:value-of select="$to + 1"/>&amp;to=<xsl:value-of select="$to + 11"/>
  			</xsl:when>
  			<xsl:otherwise></xsl:otherwise>
  		</xsl:choose>
  	</xsl:variable>
  	<xsl:variable name="prev">
  		<xsl:choose>
  			<xsl:when test="number($from) &gt; 0">?query=<xsl:value-of select="java:java.net.URLEncoder.encode(normalize-space(@xpath),'UTF-8')"/>&amp;from=<xsl:value-of select="$from - 11"/>&amp;to=<xsl:value-of select="$from - 1"/>
  			</xsl:when>
  			<xsl:otherwise></xsl:otherwise>
  		</xsl:choose>
  	</xsl:variable>
    <xsl:choose>
    	<!-- print out the menu-bar and the hits -->
        <xsl:when test="rdf:Description">
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
		                                        <xsl:if test="$prev!=''">
		                                            <a style="color: white" href="{$prev}">&lt;&lt;previous</a>
		                                        </xsl:if>
		                                    </td>
		                                    <td align="center" width="25%">
		                                        <a style="color: white" href="biblio.xml">
		                                            new query
		                                        </a>                                        
		                                    </td>
		                                    <td align="right" width="25%">
		                                        <xsl:if test="$next!=''">
		                                            <a style="color: white" href="{$next}">more &gt;&gt;</a>
		                                        </xsl:if>
		                                    </td>
		                                </tr>
		                                <xsl:apply-templates/>
		                                <tr bgcolor="#0b88e8">
		                                    <td align="left" width="25%">
		                                        <xsl:if test="$prev!=''">
		                                            <a style="color: white" href="{$prev}">&lt;&lt;previous</a>
		                                        </xsl:if>
		                                    </td>
		                                    <td align="center" width="25%">
		                                        <a style="color: white" href="biblio.xml">
		                                            new query
		                                        </a>                                        
		                                    </td>
		                                    <td align="right" width="25%">
		                                        <xsl:if test="$next!=''">
		                                            <a style="color: white" href="{$next}">more &gt;&gt;</a>
		                                        </xsl:if>
		                                    </td>
		                                </tr>
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

	<xsl:template match="show-source">
		<xsl:apply-templates/>
	</xsl:template>
	
  <xsl:template match="xmldb:result">

  </xsl:template>
  
  	<!-- format a single RDF description -->
	<xsl:template match="rdf:Description">
    	<tr bgcolor="#ffffff">
      		<td align="left" colspan="3">
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
				<xsl:if test="x:series">
					<xsl:apply-templates select="x:series"/><br/>
				</xsl:if>
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
				  <xsl:apply-templates select="dc:description"/></p>
				</xsl:if>

                <p align="right"><em>Source: </em>
                   <xsl:value-of select="@exist:source"/></p>
	  		</td>
	  	</tr>
	</xsl:template>

	<xsl:template match="@*|*" mode="get-text">
		<xsl:apply-templates mode="get-text"/>
	</xsl:template>
	
	<xsl:template match="text()" mode="get-text">
		<xsl:value-of select="."/>
	</xsl:template>
	
  <xsl:template match="dc:creator">
    <xsl:if test="position()>1">
      <xsl:text>; </xsl:text>
    </xsl:if>
    <xsl:variable name="creator">
		<xsl:apply-templates mode="get-text"/>
	</xsl:variable>
    <xsl:variable name="query">document(*)//rdf:Description[near(dc:editor|dc:creator|dc:contributor,'<xsl:value-of select="$creator"/>')]</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query,"UTF-8")'/>
    <a href="?query={$encoded}">
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
    <xsl:variable name="editor">
		<xsl:apply-templates mode="get-text"/>
	</xsl:variable>
    <xsl:variable name="query">document(*)//rdf:Description[near(dc:editor|dc:creator|dc:contributor,'<xsl:value-of select="$editor"/>')]</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query,"UTF-8")'/>
    <a href="?query={$encoded}">
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
    <xsl:variable name="contributor">
		<xsl:apply-templates mode="get-text"/>
	</xsl:variable>
    <xsl:variable name="query">document(*)//rdf:Description[near(dc:editor|dc:creator|dc:contributor,'<xsl:value-of select="$contributor"/>')]</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query)'/>
    <a href="?query={$encoded}">
		<xsl:apply-templates/>
    </a>
  </xsl:template>

  <xsl:template match="dc:title">
    <b><xsl:apply-templates/></b><br />
  </xsl:template>

  <xsl:template match="dc:subject">
    <xsl:variable name="subject">
		<xsl:apply-templates mode="get-text"/>
	</xsl:variable>
    <xsl:variable name="query">document(*)//rdf:Description[dc:subject&amp;='<xsl:value-of select='$subject'/>']</xsl:variable>
    <xsl:variable name="encoded" select='java:java.net.URLEncoder.encode($query,"UTF-8")'/>
    <a href="?query={$encoded}">
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
