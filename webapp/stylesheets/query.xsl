<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:exist="http://exist.sourceforge.net/NS/exist">
  
    <xsl:include href="doc2html-2.xsl"/>
    
    <xsl:template match="exist:collections">
        <select name="collection" size="1">
            <option>all documents</option>
            <xsl:apply-templates select="exist:collection"/>
        </select>
    </xsl:template>
    
    <xsl:template match="exist:collection">
        <option><xsl:value-of select="@name"/></option>
    </xsl:template>
    
</xsl:stylesheet>
    
