<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xmldb="http://exist-db/transformer/1.0"
  version="1.0">
  
  	<xsl:param name="context"/>
  	<xsl:param name="terms"/>
  	<xsl:param name="mode"/>
	<xsl:param name="howmany"/>
	
	<xsl:template name="predicate-expression">
		<xsl:param name="context"/>
		<xsl:param name="mode"/>
		<xsl:param name="terms"/>
		<xsl:choose>
			<xsl:when test="$mode='near'">
				near(., '<xsl:value-of select="$terms"/>')
			</xsl:when>
			<xsl:when test="$mode='exact'">
				. ='<xsl:value-of select="$terms"/>'
			</xsl:when>
			<xsl:otherwise>
				. &amp;='<xsl:value-of select="$terms"/>'
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<!-- build the XPath expression -->
	<xsl:template name="build-query">
		document(*)//<xsl:value-of select="$context"/>[
		<xsl:call-template name="predicate-expression">
			<xsl:with-param name="context"><xsl:value-of select="$context"/></xsl:with-param>
			<xsl:with-param name="terms"><xsl:value-of select="$terms"/></xsl:with-param>
			<xsl:with-param name="mode"><xsl:value-of select="$mode"/></xsl:with-param>
		</xsl:call-template>
		<xsl:text>]</xsl:text>
	</xsl:template>
		
  	<xsl:template match="create-query">
		<xsl:choose>
			<xsl:when test="$terms and string-length($terms) &gt; 0">
				<xsl:variable name="query">
					<xsl:call-template name="build-query"/>
				</xsl:variable>
				<p><xsl:value-of select="$query"/></p>
  				<xmldb:for-each query="{normalize-space($query)}" 
  					use-session="true" from="0" to="1">
  					<match>
  						<play>
  							<title><xmldb:select-node query="../../TITLE/text()"/></title>
  							<act>
  								<title><xmldb:select-node query="../TITLE/text()"/></title>
								<xmldb:current-node/>
							</act>
						</play>
					</match>
				</xmldb:for-each>
			</xsl:when>
			<xsl:otherwise>
				<error>No query specified!</error>
			</xsl:otherwise>
		</xsl:choose>
  	</xsl:template>
  	
  	<xsl:template match="@*|node()" priority="-1">
		<xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
	</xsl:template>
</xsl:stylesheet>