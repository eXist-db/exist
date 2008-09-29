<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:exist="http://exist.sourceforge.net/NS/exist" 
  version="1.0">

  <xsl:preserve-space elements="*"/>
  
  <xsl:template match="changes">
    <html>
      <head>
      	<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
      	<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
        <style type="text/css">
          body {
          font-family: sans-serif;
          color: black;
          background: white;
          background-position: top left;
          background-attachment: fixed;
          background-repeat: no-repeat;
          text-align: left;
          alignment: left;
          }          
          
          h1 { font: 200% sans-serif; color: #203cd8; }
          h2 { font: 150% sans-serif; color: #203cd8; }          
          h3 { font: 120% sans-serif; color: #203cd8; }
        </style>

        <title><xsl:value-of select="@title"/></title>
      </head>

      <body bgcolor="#FFFFFF">
        <h1><xsl:value-of select="@title"/></h1>
        
        <xsl:apply-templates select="release"/>
      </body>
    </html>
  </xsl:template>
  
  <xsl:template match="release">
    <h2>
        <xsl:text>Version: </xsl:text>
        <xsl:value-of select="@version"/>
    </h2>
    <ul>
        <xsl:for-each select="topic">
            <li type="none">
                <a>
                    <xsl:attribute name="href">#<xsl:value-of select="generate-id()"/></xsl:attribute>
                    <xsl:number value="position()" format="I"/><xsl:text>. </xsl:text><xsl:value-of select="@category"/>
                </a>
            </li>
        </xsl:for-each>
    </ul>
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="topic">
    <xsl:variable name="number">
        <xsl:number value="position()" format="I"/>
    </xsl:variable>
    <h3>        
        <a>
            <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>                        
            <xsl:value-of select="@category"/>
        </a>
    </h3>
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="action">
    <p>
        <b style="text-decoration: underline"><xsl:value-of select="@name"/></b>
        <xsl:text>: </xsl:text>
        <xsl:value-of select="text()"/>
    </p>
  </xsl:template>
  
</xsl:stylesheet>
        
