<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:exist="http://exist.sourceforge.net/NS/exist" version="1.0">

    <xsl:template match="builtin-functions">
        <table border="0" cellpadding="5" cellspacing="5">
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    XPath/XQuery Core Library Functions (http://www.w3.org/2003/05/xpath-functions)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://www.w3.org/2003/05/xpath-functions']"/>
            
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    Utility Extension Functions (http://exist-db.org/xquery/util)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/util']"/>
            
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    XMLDB Extension Functions (http://exist-db.org/xquery/xmldb)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/xmldb']"/>
            
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    HTTP Request Extension Functions (http://exist-db.org/xquery/request)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/request']"/>
        </table>
    </xsl:template>
    
    <xsl:template match="function">
        <tr>
            <td valign="top" colspan="2">
                <a name="{@name}">
                    <b>
                        <xsl:value-of select="signature"/>
                    </b>
                </a>
            </td>
        </tr>
        <xsl:if test="description">
            <tr>
                <td width="10%"></td>
                <td width="90%"><xsl:value-of select="description"/></td>
            </tr>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="node()|@*" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
