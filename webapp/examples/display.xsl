<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xmldb="http://exist-db/transformer/1.0"
  xmlns:exist="http://exist.sourceforge.net/NS/exist"
  version="1.0">
  
  	<xsl:param name="query"/>
  	
  	<xsl:template match="header">
  		<header>
  			<xsl:apply-templates/>
	  		<style type="text/css">
	    		* {
	    			font-family: sans-serif, Helvetica, Arial;
	    		}
	    		p.message { 
	    			border: 1pt solid black;
	    			margin: 15px;
	    		}
	    		a.playtitle {
	    			font-size: 14pt;
	    			color: white;
	    		}
	    		div.act {
	    			font-size: 12pt;
	    			padding-left: 20px;
	    			color: white;
	    		}
	    		div.play {
	    			background-color: #334477;
	    			border: 1pt solid black;
	    			padding-left: 10px;
	    			padding-top: 10px;
	    		}
	    		div.scene {
	    			background-color: white;
	    			color: black;
	    			padding: 8px;
	    		}
	    		div.scenetitle {
	    			font-size: 12pt;
	    			font-weight: bold;
	    			color: #334477;
	    		}
	    	</style>
	    </header>
    </xsl:template>
    
	<xsl:template match="xmldb:result-set">
		<p class="message">Found <xsl:value-of select="@count"/> hits
		in <xsl:value-of select="@query-time"/> ms.</p>
		
		<table border="0" cellpadding="0" cellspacing="10" width="100%">
			<xsl:apply-templates/>
		</table>
	</xsl:template>
	
	<xsl:template match="match">
		<tr>
			<td>
				<xsl:apply-templates/>
			</td>
		</tr>
	</xsl:template>
	
	<xsl:template match="play">
		<div class="play">
			<a class="playtitle" href="../xmldb{../@xmldb:collection}/{../@xmldb:document-id}">
				<xsl:value-of select="title"/>
			</a>
			<xsl:apply-templates/>
		</div>
	</xsl:template>
	
	<xsl:template match="act">
		<div class="act">
			<xsl:value-of select="title"/>
			<xsl:apply-templates/>
		</div>
	</xsl:template>
	
	<xsl:template match="title|TITLE"/>
	
	<xsl:template match="SCENE">
		<div class="scene">
			<div class="scenetitle"><xsl:value-of select="TITLE/text()"/></div>

			<table border="0" cellpadding="5" cellspacing="2" width="100%">
				<xsl:apply-templates select="SPEECH|STAGEDIR"/>
			</table>
		</div>
	</xsl:template>
	
	<xsl:template match="SPEECH">
		<tr>
			<td width="20%" valign="top">
				<xsl:apply-templates select="SPEAKER"/> 
			</td>
			<td width="80%">
				<xsl:apply-templates select="LINE|STAGEDIR"/>
			</td>
		</tr>
	</xsl:template>

	<xsl:template match="LINE"><xsl:apply-templates/><br/></xsl:template>

	<xsl:template match="LINE/STAGEDIR">[ <b><xsl:apply-templates/></b> ] </xsl:template>
	
	<xsl:template match="SCENE/STAGEDIR">
		<tr><td colspan="2"><b><xsl:apply-templates/></b></td></tr>
	</xsl:template>
	
	<xsl:template match="SPEECH/STAGEDIR">
		[ <b><xsl:apply-templates/></b> ]
	</xsl:template>
	
	<xsl:template match="SPEAKER">
		<b><xsl:value-of select="."/></b>
	</xsl:template>
	
	<xsl:template match="exist:match">
		<span style="background-color: #FFFF00"><xsl:apply-templates/></span>
	</xsl:template>
	
	<xsl:template match="@*|node()" priority="-1">
		<xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
	</xsl:template>
</xsl:stylesheet>