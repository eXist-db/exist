<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:exist="http://exist.sourceforge.net/NS/exist" version="1.0">

	<xsl:template match="document">
		<html>
			<head>
				<title>XQuery Available Functions</title>
				<link rel="stylesheet" type="text/css" 
					href="styles/functions.css"/>
			</head>
			<body>
				<h1>Available XQuery Functions</h1>
				<xsl:apply-templates select="builtin-functions"/>
			</body>
			<p>
				<small>View <a href="transform.xql?_source=yes">source code</a></small>
			</p>
		</html>
	</xsl:template>

    <xsl:template match="builtin-functions">
		<ul>
			<xsl:for-each select="module">
				<li>
					<a href="#{generate-id()}">
						<xsl:value-of select="description"/>
					</a>
				</li>
			</xsl:for-each>
		</ul>
		<xsl:apply-templates/>
    </xsl:template>
    
	<xsl:template match="module">
		<div class="module">
			<a style="color: white">
				<xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
				<xsl:value-of select="description"/> (<xsl:value-of select="@namespace"/>)
			</a>
		</div>
		<xsl:apply-templates select="function"/>
	</xsl:template>

    <xsl:template match="function">
		<div class="function">
			<div class="functionhead">
                <a name="{@name}">
                    <xsl:value-of select="@name"/>
                </a>
			</div>
			<xsl:apply-templates/>
		</div>
	</xsl:template>
	
	<xsl:template match="prototype">
		<div class="prototype">
			<div class="signature">
				<xsl:value-of select="signature"/>
			</div>
			<div class="description">
				<xsl:apply-templates select="description"/>
			</div>
		</div>
    </xsl:template>
    
    <xsl:template match="node()|@*" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
