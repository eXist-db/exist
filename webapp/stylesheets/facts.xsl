<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0">

    <xsl:include href="doc2html-2.xsl"/>

    <xsl:template match="category">
        <table border="0" cellpadding="5" cellspacing="2">
            <tr bgcolor="#999">
                <th colspan="2" align="left" style="color: white">
                    <xsl:value-of select="@name"/>
                </th>
            </tr>
            <xsl:apply-templates/>
        </table>
        <br/>
    </xsl:template>

    <xsl:template match="topic">
        <tr>
            <td bgcolor="#CCCCCC" align="left" valign="top">
                <b><xsl:value-of select="@name"/></b>
            </td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>

</xsl:stylesheet>

