<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:exist="http://exist.sourceforge.net/NS/exist" version="1.0">

    <xsl:template match="header">
        <header>
            <xsl:copy-of select="*"/>
            <style type="text/css">
                div.type {
                    text-align: right;
                    color: #888888;
                }
                div.title {
                    margin-top: 10px;
                    margin-left: 10px;
                    font-weight: bold;
                    font-family: Arial, Helvetica, sans-serif;
                }
                div.mtitle {
                    padding-top: 5px;
                }
                div.creators {
                    padding-below: 10px;
                }
            </style>
        </header>
    </xsl:template>
    
    <xsl:template match="dblp">
        <p></p>
        <table border="0" cellpadding="0" cellspacing="0" bgcolor="#ffffff" width="100%">
            <tr>
                <td>
                    <table border="0" cellpadding="0" cellspacing="0" bgcolor="#000000" width="100%">
                        <tr>
                            <td>
                                <table border="0" cellpadding="7" cellspacing="1" width="100%">
                                    <tr bgcolor="#D9D9D9">
										<td>Found <xsl:value-of select="@hits"/></td>
                                        <td align="right">
                                            <xsl:if test="@next &lt; @hits">
                                                <a href="?start={@next}">More &gt;&gt;</a>
                                            </xsl:if>
                                        </td>
                                    </tr>
                                    <xsl:apply-templates/>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </xsl:template>
   
    <xsl:template match="dblp/*">
        <tr bgcolor="#FFFFFF">
            <td colspan="2">
                <div class="type"><xsl:value-of select="name()"/></div>
                <xsl:if test="author">
                    <div class="creators">
                        <xsl:apply-templates select="author"/><xsl:text>:</xsl:text>
                    </div>
                </xsl:if>
                <xsl:if test="editor">
                    <div class="creators">
                        <xsl:apply-templates select="editor"/>
                        <xsl:text>(Eds.):</xsl:text>
                    </div>
                </xsl:if>
                <div class="title"><xsl:apply-templates select="title"/></div>
                <xsl:if test="booktitle|journal">
                    <div class="mtitle">
                        <xsl:text>In: </xsl:text>
                        <xsl:apply-templates select="booktitle|journal"/>
                    </div>
                </xsl:if>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="title">
        <b><xsl:value-of select="."/></b><br/>
    </xsl:template>
    
    <xsl:template match="author">
        <xsl:if test="position()&gt;1">
          <xsl:text>; </xsl:text>
        </xsl:if>
        <xsl:value-of select="."/>
    </xsl:template>
    
    <xsl:template match="editor">
        <xsl:if test="position()&gt;1">
          <xsl:text>; </xsl:text>
        </xsl:if>
        <xsl:value-of select="."/>
    </xsl:template>
    
    <xsl:template match="booktitle|journal">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="node()|@*" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>
