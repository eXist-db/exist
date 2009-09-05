<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:sidebar="http://exist-db.org/NS/sidebar" version="1.0">
    <xsl:output method="xhtml" media-type="text/html"/>
    <xsl:template match="roadmap">
        <html>
            <head>
                <title>Roadmap</title>
                <link rel="stylesheet" type="text/css" href="styles/roadmap.css"/>
            	<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
            	<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
                <script language="Javascript" type="text/javascript" 
                    src="styles/curvycorners.js"></script>
            </head>

            <body bgcolor="#FFFFFF">
                <div id="page-head">
                    <img src="logo.jpg"/>
                    <div id="navbar">
                        <ul id="menu">
                            <li>
                                <a href="index.xml">Home</a>

                            </li>
                            <li>
                                <a href="download.xml">Download</a>
                            </li>
                            <li>
                                <a href="http://atomic.exist-db.org">Wiki</a>
                            </li>
                            <li class="last">
                                <a href="examples.xml">Demo</a>
                            </li>
                        </ul>
                        <h1>Open Source Native XML Database</h1>
                    </div>

                </div>
                <xsl:apply-templates select="bookinfo"/>
                <xsl:apply-templates select="sidebar:sidebar"/>
                <div id="content2col">
                    <div class="chapter">
                        <h1 class="chaptertitle rounded">
                            <xsl:value-of select="title"/>
                        </h1>
                    </div>

                    <table id="roadmap">
                        <tr>
                            <th align="left">
                                <xsl:text>Module</xsl:text>
                            </th>
                            <th>Status</th>
                            <th>Priority</th>
                            <th>Test Coverage</th>
                            <th>Progress</th>
                            <th>Who</th>

                        </tr>
                        <xsl:apply-templates
                            select="*[not(self::sidebar:sidebar|self::title|self::legend|self::comment)]"/>

                    </table>
                    <xsl:apply-templates select="legend"/>
                </div>
            </body>
        </html>

    </xsl:template>
    <xsl:template match="module[part]">
        <tr class="module">
            <td colspan="6">
                <xsl:number level="single" format="1"/>
                <xsl:text>. </xsl:text>
                <xsl:value-of select="name"/>
            </td>
        </tr>
        <xsl:apply-templates select="remark"/>
        <xsl:apply-templates select="*[not(self::name|self::remark)]"/>
    </xsl:template>
    
    <xsl:template match="part|module[not (part)]">
        <!--<tr class="{local-name(.)}">-->
        <xsl:variable name="mytext" select="progress/text()"/>
        <xsl:variable name="myclass">
            <xsl:choose>
                <xsl:when test="$mytext = 0">P0</xsl:when>
                <xsl:when test="$mytext &gt; 1 and $mytext &lt; 21">P20</xsl:when>
                <xsl:when test="$mytext &gt; 21 and $mytext &lt; 41">P40</xsl:when>
                <xsl:when test="$mytext &gt; 41 and $mytext &lt; 61">P60</xsl:when>
                <xsl:when test="$mytext &gt; 61 and $mytext &lt; 81">P80</xsl:when>
                <xsl:when test="$mytext &gt; 81 and $mytext &lt; 100"
                    >P99</xsl:when>
                <xsl:when test="$mytext = 100">Done</xsl:when>
            </xsl:choose>
        </xsl:variable>
        <tr class="{$myclass}">
            <td>
                <xsl:number count="module|part" level="multiple" format="1.1"/>
                <xsl:text>. </xsl:text>
                <xsl:value-of select="name"/>
            </td>
            <xsl:apply-templates select="status"/>
            <xsl:apply-templates select="priority"/>
            <xsl:apply-templates select="test_coverage"/>
            <xsl:apply-templates select="progress"/>
            <xsl:apply-templates select="who"/>
        </tr>
        <xsl:apply-templates select="remark"/>
    </xsl:template>
    <xsl:template match="status">
        <td class="{ text()}">
            <xsl:choose>
                <xsl:when test="text()='Stable_redesign'">Stable, but subject to
                    redesign</xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates/>
                </xsl:otherwise>
            </xsl:choose>

        </td>
    </xsl:template>

    <xsl:template match="priority">
        <td class="{ text()}">
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="test_coverage">
        <td>
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="progress">
        <xsl:variable name="mytext" select="text()"/>
        <xsl:variable name="myclass">
            <xsl:choose>
                <xsl:when test="$mytext = 0">P0</xsl:when>
                <xsl:when test="$mytext &gt; 1 and $mytext &lt; 21">P20</xsl:when>
                <xsl:when test="$mytext &gt; 21 and $mytext &lt; 41">P40</xsl:when>
                <xsl:when test="$mytext &gt; 41 and $mytext &lt; 61">P60</xsl:when>
                <xsl:when test="$mytext &gt; 61 and $mytext &lt; 81">P80</xsl:when>
                <xsl:when test="$mytext &gt; 81 and $mytext &lt; 100"
                    >P99</xsl:when>
                <xsl:when test="$mytext = 100">Done</xsl:when>

            </xsl:choose>
        </xsl:variable>

        <td class="{$myclass}">
            <xsl:apply-templates/><xsl:text>%</xsl:text>
        </td>
    </xsl:template>

    <xsl:template match="who">
        <td>

            <xsl:apply-templates/>
        </td>

    </xsl:template>

    <xsl:template match="remark">
        <tr>
            <td class="remark" colspan="6">
                <xsl:choose>
                    <xsl:when test="*"><xsl:apply-templates/></xsl:when>
                    <xsl:otherwise><p>No remarks available.</p></xsl:otherwise>
                </xsl:choose>
            </td>
        </tr>
    </xsl:template>



    <xsl:template match="legend">
        <div class="legend">
            <h1>
                <xsl:value-of select="title"/>
            </h1>
            <!--<table id="status" border="1">
                <tr>
                    <th>Status</th>
                    <th>Description</th>
                </tr>
                <xsl:apply-templates select="status"/>
            </table>-->
            <table id="progress" border="1">
                <tr>
                    <th>Percentage</th>
                    <th>Description</th>
                </tr>
                <xsl:apply-templates select="progress"/>
            </table>
            <table id="priority" border="1">
                <tr>
                    <th>Priority</th>
                    <th>Description</th>
                </tr>
                <xsl:apply-templates select="priority"/>
            </table>
        </div>
    </xsl:template>

    <!--<xsl:template match="legend/status">
        <tr class="{name/text()}">
            <td>
                <xsl:number level="single" format="1"/>
                <xsl:text>. </xsl:text>
                <xsl:choose>
                    <xsl:when test="name/text()='Stable_redesign'">Stable, but
                        subject to redesign</xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="name"/>
                    </xsl:otherwise>
                </xsl:choose>
            </td>
            <td>

                <xsl:apply-templates select="description"/>
            </td>
        </tr>
    </xsl:template>-->
    <xsl:template match="legend/progress">
        <tr class="P0">
            <td>0</td>
            <td>work not started</td>
        </tr>
        <tr class="P20">
            <td>20</td>
            <td>1-20 Percentage of completion</td>
        </tr>
        <tr class="P40">
            <td>40</td>
            <td>21-40 Percentage of completion</td>
        </tr>
        <tr class="P60">
            <td>60</td>
            <td>41-60 Percentage of completion</td>
        </tr>
        <tr class="P80">
            <td>80</td>
            <td>61-80 Percentage of completion</td>
        </tr>
        <tr class="P99">
            <td>99</td>
            <td>81-99 Percentage of completion</td>
        </tr>
        <tr class="Done">
            <td>Done</td>
            <td>100 Percentage of completion</td>
        </tr>

    </xsl:template>
    <xsl:template match="legend/priority">
        <tr class="{name/text()}">
            <td>
                <xsl:number level="single" format="1"/>
                <xsl:text>. </xsl:text>
                <xsl:value-of select="name"/>
            </td>
            <td>

                <xsl:apply-templates select="description"/>
            </td>
        </tr>
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
            <div class="head rounded-top">
                <h3>
                    <xsl:value-of select="@name"/>
                </h3>
            </div>
            <ul class="rounded-bottom">
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

    <xsl:template match="ulink|sidebar:link">
        <a href="{@href|@url}">
            <xsl:value-of select="."/>
        </a>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
