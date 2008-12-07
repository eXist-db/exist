<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:cluster="http://exist.sourceforge.net/generators/cluster" 
  xmlns:sidebar="http://exist-db.org/NS/sidebar"
  version="1.0">
	
	<xsl:template match="/">
		<xsl:apply-templates select="cluster:page/cluster:node"/>
	</xsl:template>
	
	<xsl:template match="cluster:page/cluster:node">
    	<html>
	      	<head>
	      	<style type="text/css">
		          body {
		          	font-family: Arial, Helvetica, sans-serif;
		          	color: black;
		          }
		          
		          table.status {
		          	margin-top: 25px;
		          	border: 2px solid black;
		          	width:650px;
		          }
		          
		          td.used {
		          	background: #AA4444;
		          	color: #AA4444;
		          }
		          
		          td.free {
		          	background: #44AA44;
		          	color: #44AA44;
		          }
		          
		          td.max {
		          	background: #6666AA;
		          	color: #6666AA;
		          }
		          
		          tr.heading {
		          	color: #3333FF;
		          	background: #ddd;		          	
		          	font-size: 16pt;
		          	width:100%;
		          }

		          td.heading {
		          	color: #3333FF;
		          	background: #ddd;		          	
		          	font-size: 16pt;
		          	width:100%;
		          }
		          td.head {
		          	color: #3333FF;
		          	font-size: 14pt;
		          }		          		          
		          span.display {
		          	color: #FFFFFF;
		          }
		        </style>
			<link rel="stylesheet" type="text/css" href="styles/default-style.css"/>
	      	<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
	      	<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>

				<title><xsl:value-of select="@title"/></title>
			</head>
	
			<body>
		        <div id="top">
            		       <img src="logo.jpg" title="eXist"/>             
			       <table id="menubar">
		              <tr>
             			       <td id="header"><xsl:value-of select="title"/></td>
		                    <xsl:apply-templates select="../sidebar:sidebar/sidebar:toolbar"/>
		                </tr>
            			</table>
			    </div>
									
			   <xsl:apply-templates select="../sidebar:sidebar"/>
			   <div id="content2col">			             		
			  		<xsl:apply-templates select="cluster:info"/>
			    </div>
			</body>
    	</html>
	</xsl:template>
  
  <xsl:template match="cluster:info">
  	<table class="status">
  		<tr  class="heading">
			<td  colspan="2" class="heading"><xsl:value-of select="@type"/></td>
		</tr>
		<tr>
			<xsl:apply-templates select="cluster:data"/>
		</tr>
  	</table>
  </xsl:template>
  
  <xsl:template match="cluster:data">
  	<tr>
  		<td><xsl:value-of select="@name"/></td>  		
  		<td><xsl:value-of select="@value"/></td>  		
  	</tr>
  </xsl:template>
  
   <xsl:template match="sidebar:sidebar">
        <div id="sidebar">
            <xsl:apply-templates select="sidebar:group"/>
            <xsl:apply-templates select="sidebar:banner"/>
        </div>
    </xsl:template>

    <xsl:template match="sidebar:toolbar">
        <td align="right">
            <xsl:apply-templates/>
        </td>
    </xsl:template>
    
    <xsl:template match="sidebar:group">
        <div class="block">
            <h3><xsl:value-of select="@name"/></h3>
            <ul><xsl:apply-templates/></ul>
        </div>
    </xsl:template>

    <xsl:template match="sidebar:item">
        <xsl:choose>
            <xsl:when test="../@empty">
                <xsl:apply-templates/>
            </xsl:when>
            <xsl:otherwise>
                <li>
                    <xsl:apply-templates/>
                </li>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="sidebar:banner">
        <div class="banner">
                      
        </div>
    </xsl:template>

    <xsl:template match="sidebar:link" mode="banner">
        <a href="{@href|@url}"><xsl:value-of select="."/>
        	<img src="{sidebar:img/@src}" alt="{sidebar:img/@alt}"/>
        </a>
    </xsl:template>    

    
    <xsl:template match="ulink|sidebar:link">
        <a href="{@href|@url}"><xsl:value-of select="."/></a>
    </xsl:template>    
    
      <xsl:include href="xmlsource.xsl"/>
      
     <xsl:template match="@*|node()" priority="-1">
	  <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
  </xsl:template>


 
</xsl:stylesheet>
        
