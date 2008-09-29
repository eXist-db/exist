<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:exist="http://exist.sourceforge.net/NS/exist" 
  version="1.0">

<xsl:template match="PLAY/TITLE"><h1><xsl:apply-templates/></h1></xsl:template>

<xsl:template match="PLAY"><html>
	<head>
		<title><xsl:value-of select="TITLE"/></title>
		<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
		<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
	</head>
	<body>
		<xsl:apply-templates select="TITLE"/>
		<xsl:apply-templates select="PLAYSUBT"/>
		<xsl:apply-templates select="FM"/>
		<xsl:apply-templates select="PERSONAE"/>
		<xsl:apply-templates select="ACT"/>
	</body>
</html></xsl:template>

<xsl:template match="FM"><blockquote>
	<xsl:apply-templates/>
</blockquote></xsl:template>

<xsl:template match="PERSONAE"><h2><xsl:value-of select="TITLE"/></h2>

<xsl:apply-templates select="PERSONA|PGROUP"/>
</xsl:template>

<xsl:template match="ACT"><h2><xsl:value-of select="TITLE"/></h2>

<xsl:apply-templates select="SCENE"/></xsl:template>

<xsl:template match="SCENE"><h3><xsl:value-of select="TITLE"/></h3>

<table border="0" cellpadding="5" cellspacing="5">
	<xsl:apply-templates select="SPEECH|STAGEDIR"/>
</table></xsl:template>

<xsl:template match="SPEECH"><tr>
	<td width="20%" valign="top">
		<xsl:apply-templates select="SPEAKER"/> 
	</td>
	<td width="4%"></td>
	<td width="76%">
		<xsl:apply-templates select="LINE|STAGEDIR"/>
	</td>
</tr></xsl:template>

<xsl:template match="SPEAKER"><xsl:value-of select="text()"/><br/></xsl:template>

<xsl:template match="LINE"><xsl:apply-templates/><br/></xsl:template>

<xsl:template match="LINE/STAGEDIR">[ <b><xsl:apply-templates/></b> ] </xsl:template>

<xsl:template match="SCENE/STAGEDIR"><tr>
	<td colspan="3"><b><xsl:apply-templates/></b></td>
</tr></xsl:template>

<xsl:template match="SUBHEAD"><tr>
	<td colspan="3"><h4><xsl:apply-templates/></h4></td>
</tr></xsl:template>

<xsl:template match="PLAYSUBT"><h3><em><xsl:apply-templates/></em></h3></xsl:template>

<xsl:template match="SCNDESCR"><blockquote><xsl:apply-templates/></blockquote></xsl:template>

<xsl:template match="PERSONAE/PERSONA"><xsl:apply-templates/><br/></xsl:template>

<xsl:template match="PGROUP"><xsl:for-each select="PERSONA">
	<xsl:value-of select="text()"/>, 
</xsl:for-each>
<xsl:value-of select="GRPDESCR"/><br/></xsl:template>

<xsl:template match="P"><tt><xsl:apply-templates/></tt><br/></xsl:template>

</xsl:stylesheet>
