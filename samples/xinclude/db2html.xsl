<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    xmlns:sidebar="http://exist-db.org/NS/sidebar" version="1.0">

    <xsl:template match="book|article">
        <html>
            <head>
                <title>
                    <xsl:value-of select="(bookinfo|articleinfo)/title/text()"/>
                </title>
                <link type="text/css" href="styles/SyntaxHighlighter.css" rel="stylesheet" />
                <xsl:variable name="styleref" select="(bookinfo|articleinfo)/style/@href"/>
                <xsl:choose>
                    <xsl:when test="$styleref">
                        <link rel="stylesheet" type="text/css" href="{$styleref}"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <link rel="stylesheet" type="text/css" href="styles/default-style.css"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:copy-of select="(bookinfo|articleinfo)/link"/>
                <xsl:copy-of select="(bookinfo|articleinfo)/script"/>
                <script type="text/javascript" src="styles/niftycube.js"/>
                <script type="text/javascript" src="scripts/syntax/sh-min.js"/>
                <script type="text/javascript">
                    window.onload = function() {
                        Nifty("h1.chaptertitle", "transparent");
                        Nifty("div.note", "transparent");
                        Nifty("div.example", "transparent");
                        Nifty("div.important", "transparent");
                        Nifty("div.block div.head", "top");
                        Nifty("div.block ul", "bottom");
                        
                        dp.SyntaxHighlighter.HighlightAll('code');
                    }
                </script>
            </head>

            <body bgcolor="#FFFFFF">
                <xsl:apply-templates select="bookinfo|articleinfo"/>
                <xsl:apply-templates select="sidebar:sidebar"/>
                <div id="content2col">
                    <xsl:choose>
                        <xsl:when test="self::article">
                            <h1 class="chaptertitle">
                                <xsl:value-of select="articleinfo/title"/>
                            </h1>
                            <xsl:apply-templates select="section"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:apply-templates select="chapter"/>
                        </xsl:otherwise>
                    </xsl:choose>
                    <xsl:apply-templates select="bookinfo|articleinfo" mode="backmatter"/>
                </div>
            </body>
        </html>
    </xsl:template>

    <xsl:template name="toc">
        <ul class="toc">
            <xsl:for-each select="section">
                <li>
                    <a>
                        <xsl:choose>
                            <xsl:when test="@id">
                                <xsl:attribute name="href">#<xsl:value-of select="@id"
                                /></xsl:attribute>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="href">#<xsl:value-of select="generate-id()"
                                /></xsl:attribute>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:number count="section" level="multiple" format="1. "/>
                        <xsl:value-of select="title"/>
                    </a>
                    <xsl:if test="section">
                        <ul>
                            <xsl:for-each select="section">
                                <li>
                                    <a>
                                        <xsl:choose>
                                            <xsl:when test="@id">
                                                <xsl:attribute name="href">#<xsl:value-of select="@id"
                                                /></xsl:attribute>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:attribute name="href">#<xsl:value-of select="generate-id()"
                                                /></xsl:attribute>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                        <xsl:number count="section" level="multiple" format="1. "/>
                                        <xsl:value-of select="title"/>
                                    </a>
                                </li>
                            </xsl:for-each>
                        </ul>
                    </xsl:if>
                </li>
            </xsl:for-each>
        </ul>
    </xsl:template>

    <xsl:template match="author"/>

    <xsl:template match="bookinfo|articleinfo" mode="backmatter">
        <div class="authors">
            <xsl:apply-templates select="date" mode="backmatter"/>
            <xsl:apply-templates select="author|orgname" mode="backmatter"/>
        </div>
    </xsl:template>

    <xsl:template match="date" mode="backmatter">
        <div class="date">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="author" mode="backmatter">
        <div class="author">
            <xsl:if test="position() = 1">
                <xsl:attribute name="class">author first</xsl:attribute>
            </xsl:if>
            <xsl:value-of select="firstname"/>
            <xsl:text> </xsl:text>
            <xsl:value-of select="surname"/>
            <br/>
            <xsl:for-each select=".//jobtitle">
                <xsl:apply-templates/>
                <br/>
            </xsl:for-each>
            <xsl:for-each select=".//orgname">
                <xsl:apply-templates/>
                <br/>
            </xsl:for-each>
            <xsl:for-each select=".//email">
                <a href="mailto:{.}">
                    <small>
                        <em>
                            <xsl:value-of select="."/>
                        </em>
                    </small>
                </a>
            </xsl:for-each>
        </div>
    </xsl:template>

    <xsl:template match="orgname" mode="backmatter">
        <div class="author">
            <xsl:if test="position() = 1">
                <xsl:attribute name="class">author first</xsl:attribute>
            </xsl:if>
            <xsl:value-of select="."/>
        </div>
    </xsl:template>
    
    <xsl:template match="toc">
        <ul class="toc">
            <xsl:apply-templates/>
        </ul>
    </xsl:template>
    
    <xsl:template match="tocpart">
        <li>
            <xsl:apply-templates/>
        </li>
    </xsl:template>
    
    <xsl:template match="tocentry">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="chapter">
        <div class="chapter">
            <xsl:apply-templates select="title"/>
            <xsl:choose>
                <xsl:when test="toc">
                    <xsl:apply-templates select="toc"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="toc"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:apply-templates select="*[not(name()='title' or name() = 'toc')]"/>
        </div>
    </xsl:template>

    <xsl:template match="chapter/title">
        <h1 class="chaptertitle">
            <a>
                <xsl:attribute name="name">
                    <xsl:value-of select="generate-id()"/>
                </xsl:attribute>
            </a>
            <xsl:apply-templates/>
        </h1>
        <xsl:for-each select="author">
            <xsl:apply-templates select="."/>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="chapter/abstract|article/abstract">
        <div class="abstract">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="chapter/section|article/section">
        <h2>
            <a>
                <xsl:choose>
                    <xsl:when test="@id">
                        <xsl:attribute name="name">
                            <xsl:value-of select="@id"/>
                        </xsl:attribute>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="name">
                            <xsl:value-of select="generate-id()"/>
                        </xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
            </a>
            <xsl:apply-templates select="title"/>
        </h2>
        <xsl:apply-templates select="*[not(self::title)]"/>
    </xsl:template>

    <xsl:template match="chapter/section/title|article/section/title">
        <xsl:number count="section"/>. <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="chapter/section/section|article/section/section">
        <h3>
            <a>
                <xsl:choose>
                    <xsl:when test="@id">
                        <xsl:attribute name="name">
                            <xsl:value-of select="@id"/>
                        </xsl:attribute>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="name">
                            <xsl:value-of select="generate-id()"/>
                        </xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
            </a>
            <xsl:apply-templates select="title"/>
        </h3>
        <xsl:apply-templates select="*[not(name()='title')]"/>
    </xsl:template>

    <xsl:template match="chapter/section/section/title|article/section/section/title">
        <xsl:number count="section" level="multiple" format="1. "/>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="chapter/section/section/section|article/section/section/section">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="chapter/section/section/section/title|article/section/section/title">
        <h4>
            <xsl:apply-templates/>
        </h4>
    </xsl:template>

    <xsl:template match="chapter/section/section/section/section|article/section/section/section">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template
        match="chapter/section/section/section/section/title|article/section/section/section/title">
        <h5>
            <xsl:apply-templates/>
        </h5>
    </xsl:template>

    <!--xsl:template match="listitem/para[count(../*) = 1]">
        <xsl:apply-templates/>
    </xsl:template-->
    
    <xsl:template match="para">
        <p>
            <xsl:copy-of select="@class"/>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="emphasis">
        <em>
            <xsl:apply-templates/>
        </em>
    </xsl:template>

    <xsl:template match="figure">
        <div class="figure">
            <p class="figtitle">Figure: <xsl:value-of select="title"/></p>
            <xsl:apply-templates select="graphic"/>
        </div>
    </xsl:template>

    <xsl:template match="bookinfo|articleinfo">
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
                <xsl:apply-templates select="../sidebar:sidebar/sidebar:toolbar"/>
                <xsl:choose>
                    <xsl:when test="productname">
                        <h1>
                            <xsl:value-of select="productname"/>
                        </h1>
                    </xsl:when>
                    <xsl:otherwise>
                        <h1>
                            <xsl:value-of select="title"/>
                        </h1>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="graphic">
        <img src="{@fileref}" border="0">
            <xsl:copy-of select="@width|@height"/>
        </img>
    </xsl:template>

    <xsl:template
        match="filename|classname|methodname|option[not(ancestor::form)]|command|parameter|
        guimenu|guimenuitem|function|envar">
        <span class="{local-name(.)}">
            <xsl:apply-templates/>
        </span>
    </xsl:template>

    <xsl:template match="synopsis">
        <xsl:choose>
            <xsl:when test="@language">
                <textarea class="{@language}" name="code">
                    <xsl:apply-templates/>
                </textarea>
            </xsl:when>
            <xsl:otherwise>
                <div class="synopsis">
                    <xsl:call-template name="returns2br">
                        <xsl:with-param name="string" select="."/>
                    </xsl:call-template>
                </div>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="example">
        <div class="example">
            <h1>Example: <xsl:value-of select="title"/></h1>
            <div class="example_content">
                <xsl:apply-templates select="*[name(.)!='title']"/>
            </div>
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
        <div class="screenshot">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="programlisting">
        <xsl:choose>
            <xsl:when test="markup">
                <textarea class="xml" name="code">
                    <xsl:apply-templates select="markup/node()"/>
                </textarea>
            </xsl:when>
            <xsl:when test="@language">
                <textarea class="{@language}" name="code">
                    <xsl:apply-templates/>
                </textarea>
            </xsl:when>
            <xsl:otherwise>
                <pre>
                    <xsl:apply-templates/>
                </pre>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="note">
        <div class="note">
            <h1>Note</h1>
            <div class="note_content">
                <xsl:apply-templates/>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="important">
        <div class="important">
            <h1>Important</h1>
            <div class="important_content">
                <xsl:apply-templates/>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="title">
        <span id="header">
            <xsl:value-of select="."/>
        </span>
    </xsl:template>

    <xsl:template match="ulink|sidebar:link">
        <a href="{@href|@url}">
            <xsl:apply-templates/>
        </a>
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
        <th width="20%" align="left" valign="top">
            <p>
                <xsl:apply-templates/>
            </p>
        </th>
    </xsl:template>

    <xsl:template match="varlistentry/listitem">
        <td width="80%" align="left" valign="top">
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="orderedlist">
        <ol>
            <xsl:apply-templates/>
        </ol>
    </xsl:template>

    <xsl:template match="orderedlist/listitem">
        <li>
            <xsl:apply-templates/>
        </li>
    </xsl:template>

    <xsl:template match="itemizedlist">
        <xsl:choose>
            <xsl:when test="@style='none'">
                <ul class="none">
                    <xsl:apply-templates/>
                </ul>
            </xsl:when>
            <xsl:otherwise>
                <ul>
                    <xsl:apply-templates/>
                </ul>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="itemizedlist/listitem">
        <li>
            <xsl:apply-templates/>
        </li>
    </xsl:template>

    <xsl:template match="unorderedlist">
        <ul>
            <xsl:apply-templates/>
        </ul>
    </xsl:template>

    <xsl:template match="unorderedlist/listitem">
        <li>
            <xsl:apply-templates/>
        </li>
    </xsl:template>

    <xsl:template match="sgmltag">
        <xsl:choose>
            <xsl:when test="@class = 'attribute'"> @<xsl:apply-templates/>
            </xsl:when>
            <xsl:otherwise> &lt;<xsl:apply-templates/>&gt; </xsl:otherwise>
        </xsl:choose>
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
            <div class="head">
                <h3>
                    <xsl:value-of select="@name"/>
                </h3>
            </div>
            <ul>
                <xsl:apply-templates/>
            </ul>
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
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- ADDED: May 10, 2006 by Spencer Rose -->

    <xsl:template match="procedure">
        <div class="procedure">
            <ol>
                <xsl:apply-templates select="step"/>
            </ol>
        </div>
    </xsl:template>
    <xsl:template match="step">
        <li>
            <xsl:apply-templates/>
        </li>
    </xsl:template>

    <xsl:template match="informaltable">
        <div id="informaltable">
            <table border="0" cellpadding="0" cellspacing="0">
                <xsl:apply-templates/>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="informaltable//td">
        <td valign="top">
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="table">
        <div class="formaltable">
            <xsl:if test="title">
                <h1>
                    <xsl:apply-templates select="title"/>
                </h1>
            </xsl:if>
            <table>
                <xsl:apply-templates select="*[not(self::title)] | @*"/>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="row">
        <tr>
            <xsl:apply-templates/>
        </tr>
    </xsl:template>

    <xsl:template match="entry">
        <td>
            <xsl:apply-templates/>
        </td>
    </xsl:template>
</xsl:stylesheet>
