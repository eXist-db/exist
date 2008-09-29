<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:collection="http://apache.org/cocoon/xmldb/1.0" 
  version="1.0">

    <xsl:param name="collection"/>
    
    <xsl:template match="collection:collections">
        <html>
            <head>
                <title>/<xsl:value-of select="$collection"/></title>
            	<link rel="shortcut icon" href="resources/exist_icon_16x16.ico"/>
            	<link rel="icon" href="resources/exist_icon_16x16.png" type="image/png"/>
                <style type="text/css">
                    th {
                        font-family: Arial, Helvetica, sans-serif;
                        color: white;
                        font-weight: bold;
                        padding-top: 8px;
                    }
                </style>
            </head>
            <body>
                <table width="100%" align="center" border="0" bgcolor="#666699">
                    <tr>
                        <th align="left" width="60%">
                            /<xsl:value-of select="$collection"/>
                        </th>
                        <th align="left" width="20%"></th>
                        <th align="left" width="20%">type</th>
                    </tr>
                    <xsl:if test="not($collection = 'db')">
                        <tr bgcolor="#CACACA">
                            <td width="60%">
                                <a href="../">..</a>
                            </td>
                            <td width="20%"></td>
                            <td width="20%">
                                parent collection
                            </td>
                        </tr>
                    </xsl:if>
                    <xsl:apply-templates select="collection:collection"/>
                    <xsl:apply-templates select="collection:resource"/>
                </table>
                
                <form action="./" method="GET">
                    <table width="100%" align="center" border="0" bgcolor="#666699">
                        <tr>
                            <th align="left">XQuery</th>
                        </tr>
                        <tr>
                            <td bgcolor="9999CC">
                                <textarea name="xpath" cols="60" rows="5"/>
                                <input type="submit"/>
                            </td>
                        </tr>
                    </table>
                </form>
                
                <p>This database browser uses Cocoon's xmldb:// pseudo-protocol to
                browse through collections, retrieve resources and execute queries.
                The complete application logic is defined in the 
                <a href="/exist/sitemap.xmap">sitemap</a>. Two stylesheets
                are used: the first processes collection contents, the second displays query
                results.</p>
            </body>
        </html>
    </xsl:template>
    
    <xsl:template match="collection:collection">
        <xsl:variable name="name"><xsl:value-of select="@name"/></xsl:variable>
        <xsl:choose>
            <xsl:when test="position() mod 2 = 0">
                <tr bgcolor="#CACACA"> 
                    <td width="60%">
                        <a href="{$name}/"><xsl:value-of select="$name"/>/</a>
                    </td>
                    <td width="20%"></td>
                    <td width="20%">collection</td>
                </tr>
            </xsl:when>
            <xsl:otherwise>
                <tr bgcolor="#9999CC"> 
                    <td width="60%">
                        <a href="{$name}/"><xsl:value-of select="$name"/>/</a>
                    </td>
                    <td width="20%"></td>
                    <td width="20%">collection</td>
                </tr>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="collection:resource">
        <xsl:variable name="name"><xsl:value-of select="@name"/></xsl:variable>
        <xsl:choose>
            <xsl:when test="position() mod 2 = 0">
                <tr bgcolor="#CACACA">
                    <td width="60%">
                        <a href="{$name}"><xsl:value-of select="$name"/></a>
                    </td>
                    <td width="20%">
                        <a href="{$name}?cocoon-view=pretty-content">view source</a>
                    </td>
                    <td width="20%">resource</td>
                </tr>
            </xsl:when>
            <xsl:otherwise>
                <tr bgcolor="#9999CC"> 
                    <td width="60%">
                        <a href="{$name}"><xsl:value-of select="$name"/></a>
                    </td>
                    <td width="20%">
                        <a href="{$name}?cocoon-view=pretty-content">view source</a>
                    </td>
                    <td width="20%">resource</td>
                </tr>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
