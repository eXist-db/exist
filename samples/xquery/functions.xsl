<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:exist="http://exist.sourceforge.net/NS/exist" version="1.0">

	<xsl:template match="builtin-functions">
		<div>
		<div class="module">
            XPath/XQuery Core Library Functions (http://www.w3.org/2003/05/xpath-functions)
		</div>
        <xsl:apply-templates select="function[@module='http://www.w3.org/2003/05/xpath-functions']"/>
		
		<div class="module">
			Utility Extension Functions (http://exist-db.org/xquery/util)
		</div>
        <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/util']"/>
            
		<div class="module">
			XMLDB Extension Functions (http://exist-db.org/xquery/xmldb)
		</div>
        <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/xmldb']"/>
            
		<div class="module">
            HTTP Request Extension Functions (http://exist-db.org/xquery/request)
		</div>
		<xsl:apply-templates select="function[@module='http://exist-db.org/xquery/request']"/>
		</div>
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
