<?xml version="1.0" encoding="ISO-8859-1"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:collection="http://apache.org/cocoon/xmldb/1.0"
  xmlns:java="http://xml.apache.org/xslt/java"
  version="1.0">

    <xsl:param name="collection"/>
    <xsl:param name="resource"/>
    <xsl:param name="block"/>
    
    <xsl:include href="xml2html.xsl"/>
    
    <xsl:template match="collection:results">
        <html>
            <head>
                <title><xsl:value-of select="@query"/></title>
            	<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
            	<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
                <style type="text/css">
                    th {
                        font-family: Arial, Helvetica, sans-serif;
                        color: white;
                        font-weight: bold;
                        padding-top: 8px;
                    }
                    
                    th.pages {
                        font-size: smaller;
                    }
                    
                    dl {
                        font-family: Courier, monospace;
                        padding-below: 8px;
                    }
                </style>
            </head>
            <body>
                <table width="80%" align="center" border="0" bgcolor="#666699">
                    <tr>
                        <th align="left" width="50%">
                            <xsl:value-of select="@query"/>
                        </th>
                        <th align="right" width="50%" class="pages">
                            <xsl:variable name="encoded-query" select="java:java.net.URLEncoder.encode(@query)"/>
                            <!-- create [ x ] link for every page -->
                            <xsl:for-each select="collection:block">
                                <!-- don't show more than 30 blocks -->
                                <xsl:if test="position() &lt; 30">
                                    <xsl:choose>
                                        <xsl:when test="position()=$block">
                                            <xsl:text>[</xsl:text><xsl:value-of select="@collection:id"/><xsl:text>]</xsl:text>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:choose>
                                                <xsl:when test="$resource">
                                                    <xsl:text>[</xsl:text><a href="{$resource}?xpath={$encoded-query}&amp;start={@collection:id}"><xsl:value-of select="@collection:id"/></a><xsl:text>] </xsl:text>
                                                </xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:text>[</xsl:text><a href="./?xpath={$encoded-query}&amp;start={@collection:id}"><xsl:value-of select="@collection:id"/></a><xsl:text>] </xsl:text>
                                                </xsl:otherwise>
                                            </xsl:choose>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:if>
                            </xsl:for-each>
                        </th>
                    </tr>
                    <tr bgcolor="#CACACA">
                        <td colspan="2">
                            <a href="./">parent collection: /<xsl:value-of select="$collection"/></a>
                        </td>
                    </tr>
                    <xsl:apply-templates/>
                    
                </table>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="collection:block">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="collection:result">
        <tr>
            <xsl:choose>
                <xsl:when test="position() mod 2 = 0">
                    <td bgcolor="#CACACA" colspan="2">
                        <dl>
                            <xsl:apply-templates mode="xmlsrc"/>
                        </dl>
                    </td>
                </xsl:when>
                <xsl:otherwise>
                    <td bgcolor="9999CC" colspan="2">
                        <dl>
                            <xsl:apply-templates mode="xmlsrc"/>
                        </dl>
                    </td>
                </xsl:otherwise>
            </xsl:choose>
        </tr>
    </xsl:template>
</xsl:stylesheet>
