<?xml version="1.0" encoding="UTF-8"?>

<!--
	Search through a set of RDF references. This stylesheet does the actual
	work: it evaluates the request parameters and puts them into an XPath
	query.
-->
<xsl:stylesheet 
  xmlns:xdb="http://exist-db.org/transformer/1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">
  
  	<xsl:param name="query"/>
	<xsl:param name="field1"/>
  	<xsl:param name="term1"/>
  	<xsl:param name="mode1"/>
  	<xsl:param name="field2"/>
  	<xsl:param name="term2"/>
  	<xsl:param name="mode2"/>
  	<xsl:param name="operator"/>
	<xsl:param name="from"/>
	<xsl:param name="to"/>
	<xsl:param name="source"/>
	
	<!-- encode the query-field -->
	<xsl:template name="get-field">
		<xsl:param name="field"/>
		<xsl:choose>
			<xsl:when test="$field='any'">.</xsl:when>
			<xsl:when test="$field='au'">(dc:creator|dc:editor)|dc:contributor</xsl:when>
			<xsl:when test="$field='ti'">dc:title</xsl:when>
			<xsl:when test="$field='ab'">dc:description</xsl:when>
			<xsl:when test="$field='su'">dc:subject</xsl:when>
			<xsl:otherwise>.</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="predicate-expression">
		<xsl:param name="field"/>
		<xsl:param name="mode"/>
		<xsl:param name="term"/>
		<xsl:choose>
			<xsl:when test="$mode='near'">
				near(<xsl:value-of select="$field"/>, '<xsl:value-of select="$term"/>')
			</xsl:when>
			<xsl:when test="$mode='exact'">
				<xsl:value-of select="$field"/>='<xsl:value-of select="$term"/>'
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$field"/>&amp;='<xsl:value-of select="$term"/>'
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<!-- build the XPath expression -->
	<xsl:template name="build-query">
		<xsl:text>//rdf:Description[</xsl:text>
		<xsl:call-template name="predicate-expression">
			<xsl:with-param name="field">
				<xsl:call-template name="get-field">
					<xsl:with-param name="field">
						<xsl:value-of select="$field1"/>
					</xsl:with-param>
				</xsl:call-template>
			</xsl:with-param>
			<xsl:with-param name="term"><xsl:value-of select="$term1"/></xsl:with-param>
			<xsl:with-param name="mode"><xsl:value-of select="$mode1"/></xsl:with-param>
		</xsl:call-template>
		<xsl:if test="$term2 and string-length($term2&gt;0)">
			<xsl:text> </xsl:text>
			<xsl:value-of select="$operator"/>
			<xsl:text> </xsl:text>
			<xsl:call-template name="predicate-expression">
				<xsl:with-param name="field">
					<xsl:call-template name="get-field">
						<xsl:with-param name="field">
							<xsl:value-of select="$field2"/>
						</xsl:with-param>
					</xsl:call-template>
				</xsl:with-param>
				<xsl:with-param name="term"><xsl:value-of select="$term2"/></xsl:with-param>
				<xsl:with-param name="mode"><xsl:value-of select="$mode2"/></xsl:with-param>
			</xsl:call-template>
		</xsl:if>
		<xsl:text>]</xsl:text>
	</xsl:template>
	
  	<xsl:template match="create-query">
  		<xsl:variable name="expr">
  			<xsl:choose>
  				<xsl:when test="$query">
  					<xsl:value-of select="$query"/>
  				</xsl:when>
  				<xsl:otherwise>
  					<xsl:call-template name="build-query"/>
  				</xsl:otherwise>
  			</xsl:choose>
  		</xsl:variable>
  		<xsl:variable name="start">
  			<xsl:choose>
  				<xsl:when test="$from"><xsl:value-of select="$from"/></xsl:when>
  				<xsl:otherwise>0</xsl:otherwise>
  			</xsl:choose>
  		</xsl:variable>
  		<xsl:variable name="end">
  			<xsl:choose>
  				<xsl:when test="$to"><xsl:value-of select="$to"/></xsl:when>
  				<xsl:otherwise>9</xsl:otherwise>
  			</xsl:choose>
  		</xsl:variable>
  		<xdb:for-each query="{normalize-space($expr)}" use-session="true"
  			from="{$start}" to="{$end}">
			<xdb:current-node/>
		</xdb:for-each>
  	</xsl:template>
  	
  	<xsl:template match="node()|@*" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*"/>
		</xsl:copy>
	</xsl:template>
	
</xsl:stylesheet>
