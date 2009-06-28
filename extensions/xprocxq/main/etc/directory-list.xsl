<xsl:stylesheet
 xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
 xmlns:file="http://exist-db.org/xquery/file"
 xmlns:c="http://www.w3.org/ns/xproc-step"
 version="1.0" >
     
<xsl:output indent="yes"/>

<xsl:template match="file:list">
<c:directory name="{@name}">
	<xsl:apply-templates/>
</c:directory>	
</xsl:template>

<xsl:template match="file:file">
<c:file name="{@name}"/>
</xsl:template>

</xsl:stylesheet>
