<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:exist="http://exist.sourceforge.net/NS/exist"
        xmlns:sidebar="http://exist-db.org/NS/sidebar"
        xmlns:dc="http://purl.org/dc/elements/1.1/"
        version="1.0">

    <xsl:param name="xslt.exist.version"/>
    
  <!-- used by multi-form pages -->
    <xsl:param name="page"/>

    <xsl:output method="xhtml" media-type="text/html"/>

    <xsl:variable name="showpage">
    <!-- ist Parameter $page gesetzt? -->
        <xsl:choose>
            <xsl:when test="$page">
                <xsl:value-of select="$page"/>
            </xsl:when>
      <!-- nein: hole name der ersten Page als Parameter -->
            <xsl:otherwise>
                <xsl:value-of select="//tabbed-form/page[1]/@name"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:variable>

    <xsl:variable name="include.analytics" select="false()"/>
    
    <xsl:template match="document">
        <xsl:variable name="css">
            <xsl:choose>
                <xsl:when test="header/style/@href">
                    <xsl:value-of select="header/style/@href"/>
                </xsl:when>
                <xsl:otherwise>styles/default-style2.css</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <html>
            <head>
                <title>
                    <xsl:value-of select="header/title"/>
                </title>
                <link rel="stylesheet" type="text/css" href="{$css}"/>
            	<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
            	<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
                <xsl:if test="header/style">
                    <xsl:copy-of select="header/style"/>
                </xsl:if>
                <xsl:if test="header/script">
                    <xsl:copy-of select="header/script"/>
                </xsl:if>
                <script language="Javascript" type="text/javascript" 
                    src="styles/curvycorners.js"></script>
                <script language="Javascript" type="text/javascript" 
					src="scripts/yui/yahoo-dom-event2.7.0.js"/>
                <script language="Javascript" type="text/javascript" src="scripts/main.js"/>
            </head>

            <body bgcolor="#FFFFFF">
                <div id="page-head">
                    <img src="logo.jpg" title="eXist"/>
					<div id="quicksearch">
						<form action="{sidebar:sidebar/sidebar:search/@href}" method="GET">
							<input type="text" size="20" name="q"/>
							<input type="submit" value="Search"/>
						</form>
					</div>
                    <div id="version-info">Site based on: <xsl:value-of select="$xslt.exist.version"/></div>
                    <div id="navbar">
                        <xsl:apply-templates select="sidebar:sidebar/sidebar:toolbar"/>
                        <h1><xsl:value-of select="header/title"/></h1>
                    </div>
                </div>
                <xsl:apply-templates select="sidebar:sidebar"/>
                <xsl:apply-templates select="newsblock"/>
                <xsl:apply-templates select="body"/>
                <xsl:call-template name="analytics"/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="body">
        <xsl:choose>
            <xsl:when test="../newsblock">
                <div id="content"><xsl:apply-templates/></div>
            </xsl:when>
            <xsl:otherwise>
                <div id="content2col"><xsl:apply-templates/></div>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="error">
        <p class="error">
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="exist:query-time">
        <xsl:value-of select="//exist:result/@queryTime"/>
    </xsl:template>

    <xsl:template match="exist:hits">
        <xsl:value-of select="//exist:result/@hitCount"/>
    </xsl:template>

    <xsl:template match="exist:retrieve-time">
        <xsl:value-of select="//exist:result/@retrieveTime"/>
    </xsl:template>

  <!-- templates for the sidebar -->

    <xsl:template match="sidebar:sidebar">
        <div id="sidebar">
            <xsl:apply-templates select="sidebar:group"/>
            <xsl:apply-templates select="sidebar:banner"/>
            <xsl:apply-templates select="sidebar:reference"/>
        </div>
    </xsl:template>

    <xsl:template match="sidebar:toolbar">
        <ul id="menu">
            <xsl:for-each select="sidebar:link">
                <li>
                    <xsl:if test="position() = last()">
                        <xsl:attribute name="class">last</xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="."/>
                </li>
            </xsl:for-each>
        </ul>
    </xsl:template>
    
    <xsl:template match="sidebar:group">
        <div class="block">
            <div class="head rounded-top">
                <h3><xsl:value-of select="@name"/></h3>
            </div>            
            <ul class="rounded-bottom"><xsl:apply-templates/></ul>
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

	<xsl:template match="sidebar:reference">
		<div class="reference">
			<h3><xsl:value-of select="@title"/></h3>
			<xsl:apply-templates/>
		</div>
	</xsl:template>
	
    <xsl:template match="link|sidebar:link">
        <a href="{@href}"><xsl:apply-templates/></a>
    </xsl:template>

    <xsl:template match="author">
        <small>
            <xsl:value-of select="text()"/>
        </small>
        <br/>
        <xsl:if test="@email">
            <a href="mailto:{@email}">
                <small>
                    <em>
                        <xsl:value-of select="@email"/>
                    </em>
                </small>
            </a>
        </xsl:if>
    </xsl:template>

    <xsl:template match="body/section">
        <h1 class="chaptertitle rounded">
            <xsl:if test="@id">
                <a name="{@id}"></a>
            </xsl:if>
            <xsl:value-of select="@title"/>
        </h1>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="section/section">
        <p></p>
        <xsl:if test="@id">
            <a name="{@id}"></a>
        </xsl:if>
        <h2>
            <xsl:value-of select="@title"/>
        </h2>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="p">
        <p>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="emph">
        <em>
            <xsl:apply-templates/>
        </em>
    </xsl:template>

    <xsl:template match="emphasis">
        <span class="emphasis">
            <xsl:apply-templates/>
        </span>
    </xsl:template>

    <xsl:template match="preserve">
        <pre>
            <xsl:apply-templates/>
        </pre>
    </xsl:template>

    <xsl:template match="source">
        <div align="center">
            <table width="90%" cellspacing="4" cellpadding="0" border="0">
                <tr>
                    <td bgcolor="#0086b2" width="1" height="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                    <td bgcolor="#0086b2" height="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                    <td bgcolor="#0086b2" width="1" height="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                </tr>
                <tr>
                    <td bgcolor="#0086b2" width="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                    <td bgcolor="#ffffff">
                        <pre>
                            <xsl:apply-templates/>
                        </pre>
                    </td>
                    <td bgcolor="#0086b2" width="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                </tr>
                <tr>
                    <td bgcolor="#0086b2" width="1" height="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                    <td bgcolor="#0086b2" height="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                    <td bgcolor="#0086b2" width="1" height="1">
                        <img src="resources/void.gif" width="1" height="1" vspace="0" hspace="0" border="0"/>
                    </td>
                </tr>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="abstract">
        <p>
            <table width="80%" border="0" cellpadding="0" cellspacing="0" align="center"
                    bgcolor="#000000">
                <tr>
                    <td>
                        <table width="100%" cellpadding="3" cellspacing="1">
                            <tr>
                                <td width="98%" bgcolor="#FFFFFF">
                                    <span class=".abstract">
                                        <xsl:apply-templates/>
                                    </span>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </p>
    </xsl:template>

    <xsl:template match="note">
        <div class="note">
            <h1>Note</h1>
            <div class="note_content">
                <xsl:apply-templates/>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="ul|ol|dl">
        <blockquote>
            <xsl:copy>
                <xsl:apply-templates/>
            </xsl:copy>
        </blockquote>
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

    <xsl:template match="li">
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="sl">
        <ul>
            <xsl:apply-templates/>
        </ul>
    </xsl:template>

    <xsl:template match="b|em">
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="tabbed-form">
        <table border="0" cellpadding="10" cellspacing="0" height="20">
            <tr>
                <xsl:apply-templates select="page" mode="print-tabs"/>
            </tr>
        </table>

        <table border="0" cellpadding="15" cellspacing="0" width="100%" bgcolor="{@highcolor}">
            <tr>
                <td class="tabpage">
                    <xsl:apply-templates select="page[@name=$showpage]"/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="page" mode="print-tabs">
        <xsl:variable name="highcolor">
            <xsl:value-of select="ancestor::tabbed-form/@highcolor"/>
        </xsl:variable>
        <xsl:variable name="lowcolor">
            <xsl:value-of select="ancestor::tabbed-form/@lowcolor"/>
        </xsl:variable>
        <xsl:variable name="href">
            <xsl:value-of select="ancestor::tabbed-form/@href"/>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="$showpage and $showpage=@name">
                <td class="tab" bgcolor="{$highcolor}">
                    <b>
                        <xsl:value-of select="@label"/>
                    </b>
                </td>
            </xsl:when>

            <xsl:otherwise>
                <td class="tab" bgcolor="{$lowcolor}">
                    <a class="page" href="{$href}?page={@name}">
                        <b style="font-color: white">
                            <xsl:value-of select="@label"/>
                        </b>
                    </a>
                </td>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="page">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="definitionlist">
        <table border="0" width="90%" align="center" cellpadding="5" cellspacing="5">
            <xsl:apply-templates/>
        </table>
    </xsl:template>

    <xsl:template match="definition">
        <tr>
            <xsl:apply-templates/>
        </tr>
    </xsl:template>

    <xsl:template match="term">
        <td valign="top" width="10%">
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="def">
        <td width="90%">
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="newsblock">
        <div id="news">
            <div class="block" id="news-box" style="display: none;">
                <div class="head rounded-top"><h3><a href="http://atomic.exist-db.org">News Blog</a></h3></div>
                <div id="news_content" class="news_content">
                    Loading News ...
                </div>
            </div>
			<div class="block" id="twitter-box" style="display: none;">
				<div class="head rounded-top"><h3><a href="http://twitter.com/existdb">Twitter Feed</a></h3></div>
				<div id="twitter_content" class="news_content">
					Loading Twitter Feed ...
				</div>
			</div>
            <xsl:apply-templates/>
        </div>
    </xsl:template>
    
    <xsl:template match="item">
        <li class="headline">
            <p class="date">
                <xsl:value-of select="substring(dc:date, 1, 10)"/>
            </p>
            
            <a href="{link/text()}"><xsl:value-of select="title"/></a>
        </li>
    </xsl:template>
    
    <xsl:template match="warning">
        <div class="warning">
            <h5><xsl:value-of select="title"/></h5>
            <xsl:apply-templates select="p"/>    
        </div>
    </xsl:template>
    
    <xsl:include href="xmlsource.xsl"/>
    
    <xsl:template name="analytics">
        <xsl:if test="$include.analytics">
            <!-- Piwik -->
            <script type="text/javascript">
                var pkBaseURL = (("https:" == document.location.protocol)? "https://apps.sourceforge.net/piwik/exist/": "http://apps.sourceforge.net/piwik/exist/");
                document.write(unescape("%3Cscript src='" + pkBaseURL + "piwik.js' type='text/javascript'%3E%3C/script%3E"));</script>
            <script type="text/javascript">
                piwik_action_name = '';
                piwik_idsite = 1;
                piwik_url = pkBaseURL + "piwik.php";
                piwik_log(piwik_action_name, piwik_idsite, piwik_url);</script>
            <object>
                <noscript>
                    <p>
                        <img src="http://apps.sourceforge.net/piwik/exist/piwik.php?idsite=1"
                            alt="piwik"/>
                    </p>
                </noscript>
            </object>
            <!-- End Piwik Tag -->
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>
