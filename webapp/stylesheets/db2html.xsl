<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:exist="http://exist.sourceforge.net/NS/exist"
  xmlns:sidebar="http://exist-db.org/NS/sidebar"
  version="1.0">
  
    <xsl:template match="book">
        <html>
            <head>
                <xsl:choose>
                    <xsl:when test="bookinfo/style/@href">
                        <link rel="stylesheet" type="text/css" href="{bookinfo/style/@href}"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <link rel="stylesheet" type="text/css" href="styles/default-style.css"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="bookinfo/style[not(@href)]">
                        <xsl:copy-of select="bookinfo/style[not(@href)]"/>
                </xsl:if>
                <title><xsl:value-of select="bookinfo/title/text()"/></title>
            </head>
    
            <body bgcolor="#FFFFFF">
                <xsl:apply-templates select="bookinfo"/>
                <xsl:apply-templates select="sidebar:sidebar"/>
                <div id="content2col">
                    <xsl:apply-templates select="chapter"/>
                </div>
            </body>
        </html>
    </xsl:template>
            
    <xsl:template name="toc">
        <div class="toc">
            <xsl:for-each select="section">
                <div class="tocitem">
                    <a href="#{generate-id()}">
                        <xsl:number count="section" level="multiple" format="1. "/> <xsl:value-of select="title"/>
                    </a>
                    <xsl:for-each select="section">
                        <div class="tocitem2">
                            <a href="#{generate-id()}">
                                <xsl:number count="section" level="multiple" format="1. "/> <xsl:value-of select="title"/>
                            </a>
                        </div>
                    </xsl:for-each>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>
    
    <xsl:template match="author">
        <div class="authors">
            <small>
                <xsl:value-of select="firstname"/> <xsl:value-of select="surname"/>
            </small>
            <br/>
            <xsl:if test=".//email">
                <a href="mailto:{.//email}"><small><em><xsl:value-of select=".//email"/></em></small></a>
            </xsl:if>
        </div>
    </xsl:template>
  
    <xsl:template match="chapter">
        <xsl:apply-templates select="title"/>
        <xsl:call-template name="toc"/>
        <xsl:apply-templates select="*[not(name()='title')]"/>
    </xsl:template>
    
    <xsl:template match="chapter/title">
        <h1>
            <a>
                <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
            </a>
            <xsl:apply-templates/>
        </h1>
    </xsl:template>
    
    <xsl:template match="chapter/abstract">
        <p class="abstract"><xsl:apply-templates/></p>
    </xsl:template>
    
    <xsl:template match="chapter/section">
        <a>
            <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
        </a>
        <xsl:if test="@id">
            <a name="{@id}"></a>
        </xsl:if>
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="chapter/section/title">
        <h2>
            <xsl:number count="section"/>. <xsl:apply-templates/>
        </h2>
    </xsl:template>
    
    <xsl:template match="chapter/section/section">
        <a>
            <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
        </a>
        <xsl:if test="@id">
            <a name="{@id}"></a>
        </xsl:if>
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="chapter/section/section/title">
        <h3>
            <xsl:number count="section" level="multiple" format="1. "/><xsl:apply-templates/>
        </h3>
    </xsl:template>
    
    <xsl:template match="chapter/section/section/section">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="chapter/section/section/section/title">
        <h4><xsl:apply-templates/></h4>
    </xsl:template>
    
    <xsl:template match="para">
        <p><xsl:apply-templates/></p>
    </xsl:template>
    
    <xsl:template match="emphasis">
        <em><xsl:apply-templates/></em>
    </xsl:template>
    
    <xsl:template match="figure">
        <div class="figure">
            <p class="figtitle">Figure: <xsl:value-of select="title"/></p>
            <xsl:apply-templates select="graphic"/>
        </div>
    </xsl:template>
    
    <xsl:template match="bookinfo">
        <div id="page-head">
            <xsl:choose>
                <xsl:when test="graphic/@fileref">
                    <img src="{graphic/@fileref}"/>
                </xsl:when>
                <xsl:otherwise>
                    <img src="logo.jpg" title="eXist"/>
                </xsl:otherwise>
            </xsl:choose>
            <div id="navbar">
                <h1><xsl:value-of select="title"/></h1>
                <xsl:apply-templates select="../sidebar:sidebar/sidebar:toolbar"/>
            </div>
        </div>
    </xsl:template>
    
    <xsl:template match="graphic">
        <img src="{@fileref}"/>
    </xsl:template>
    
    <xsl:template match="filename">
        <span class="filename"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template match="classname">
        <span class="classname"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template match="methodname">
        <span class="methodname"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template match="option">
        <span class="option"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template match="command">
        <span class="command"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template match="synopsis">
        <div class="synopsis">
            <xsl:call-template name="returns2br">
                <xsl:with-param name="string" select="."/>
            </xsl:call-template>
        </div>
    </xsl:template>
    
    <xsl:template match="example">
        <div class="example">
            <div class="example_title">Example: <xsl:value-of select="title"/></div>
            <xsl:apply-templates select="*[name(.)!='title']"/>
        </div>
    </xsl:template>
    
    <xsl:template match="screen">
        <div class="screen">
            <xsl:call-template name="returns2br">
                <xsl:with-param name="string" select="."/>
            </xsl:call-template>
        </div>
    </xsl:template>

    <xsl:template match="screenshot">
        <center>
            <xsl:apply-templates/>
        </center>
    </xsl:template>
    
    <xsl:template match="programlisting">
        <pre>
            <xsl:apply-templates/>
        </pre>
    </xsl:template>
    
    <xsl:template match="note">
        <div class="note">
            <span class="note_title">Note</span>
            <xsl:apply-templates/>
        </div>
    </xsl:template>
    
    <xsl:template match="title">
        <span id="header"><xsl:value-of select="."/></span>
    </xsl:template>
    
    <xsl:template match="ulink|sidebar:link">
        <a href="{@href|@url}"><xsl:value-of select="."/></a>
    </xsl:template>
    
    <xsl:template match="variablelist">
        <div class="variablelist">
            <table border="0" cellpadding="5" cellspacing="0">
                <xsl:apply-templates/>
            </table>
        </div>
    </xsl:template>
    
    <xsl:template match="varlistentry">
        <tr>
            <xsl:apply-templates/>
        </tr>
    </xsl:template>
    
    <xsl:template match="term">
        <td width="20%" align="left" valign="top">
            <xsl:apply-templates/>
        </td>
    </xsl:template>
    
    <xsl:template match="varlistentry/listitem">
        <td width="80%" align="left">
            <xsl:apply-templates/>
        </td>
    </xsl:template>
    
    <xsl:template match="orderedlist">
        <ol>
            <xsl:apply-templates/>
        </ol>
    </xsl:template>
    
    <xsl:template match="orderedlist/listitem">
        <li><xsl:apply-templates/></li>
    </xsl:template>
    
    <xsl:template match="itemizedlist">
        <xsl:choose>
            <xsl:when test="@style='none'">
                <ul class="none"><xsl:apply-templates/></ul>
            </xsl:when>
            <xsl:otherwise>
                <ul><xsl:apply-templates/></ul>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="itemizedlist/listitem">
        <li><xsl:apply-templates/></li>
    </xsl:template>
    
    <xsl:template match="unorderedlist">
        <ul>
            <xsl:apply-templates/>
        </ul>
    </xsl:template>
    
    <xsl:template match="unorderedlist/listitem">
        <li><xsl:apply-templates/></li>
    </xsl:template>
    
    <xsl:template match="sgmltag">
        &lt;<xsl:apply-templates/>&gt;
    </xsl:template>
    
    <xsl:template name="returns2br">
        <xsl:param name="string"/>
        <xsl:variable name="return" select="'&#xa;'"/>
        <xsl:choose>
          <xsl:when test="contains($string,$return)">
            <xsl:value-of select="substring-before($string,$return)"/>
            <br/>
            <xsl:call-template name="returns2br">
              <xsl:with-param name="string" select="substring-after($string,$return)"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="$string"/>
          </xsl:otherwise>
       </xsl:choose>
  </xsl:template>
  
  <xsl:template match="sidebar:sidebar">
        <div id="sidebar">
            <xsl:apply-templates select="sidebar:group"/>
            <xsl:apply-templates select="sidebar:banner"/>
        </div>
    </xsl:template>

    <xsl:template match="sidebar:toolbar">
        <ul id="menu">
            <xsl:for-each select="sidebar:link">
                <li><xsl:apply-templates select="."/></li>
            </xsl:for-each>
        </ul>
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
            <xsl:apply-templates/>
        </div>
    </xsl:template>

  <xsl:include href="xmlsource.xsl"/>

  <xsl:template match="@*|node()" priority="-1">
	  <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
  </xsl:template>

</xsl:stylesheet>

