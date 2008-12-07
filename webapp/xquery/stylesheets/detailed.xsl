<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:sn="http://www.sozionet.org/1.0/#"
    xmlns:dcq="http://purl.org/dc/terms/#"
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:x="http://exist.sourceforge.net/dc-ext"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    xmlns:java="http://xml.apache.org/xslt/java"
    version="1.0">
    
    <xsl:template match="section">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="query-results">
        <table border="0" cellpadding="7" cellspacing="0" class="overview" bgcolor="#F3F3F3"
            width="100%">
            <xsl:call-template name="navigation">
                <xsl:with-param name="cssclass" select="'navtop'"/>
            </xsl:call-template>
            <xsl:apply-templates select="rdf:Description"/>
            <xsl:call-template name="navigation">
                <xsl:with-param name="cssclass" select="'navbottom'"/>
            </xsl:call-template>
        </table>
    </xsl:template>
    
    <xsl:template name="navigation">
        <xsl:param name="cssclass"/>
        <xsl:variable name="summary" select="floor((@start - 1) div @max) * @max + 1"/>
        <tr bgcolor="#D9D9D9" width="100%">
            <th class="{$cssclass}" align="left" width="20%">
                <xsl:if test="@start &gt; 1">
                    <a href="biblio.xql?start={@start - 1}&amp;howmany={@max}&amp;display=details">
                        &lt;&lt; Previous
                    </a>
                </xsl:if>
            </th>
            <th class="{$cssclass}" align="center" width="60%">
                <a href="biblio.xql?start={$summary}&amp;howmany={@max}&amp;display=summary">
                    <span class="icondesc">[Short Display]</span>
                </a>
            </th>
            <th class="{$cssclass}" align="right" width="20%">
                <xsl:if test="number(@next) &lt; @hits">
                    <a href="biblio.xql?start={@next}&amp;howmany={@max}&amp;display=details">
                        Next &gt;&gt;
                    </a>
                </xsl:if>
            </th>
        </tr>
    </xsl:template>
    
    <xsl:template match="rdf:Description">
        <tr>
            <td colspan="3">
                <div class="dc_title"><xsl:apply-templates select="dc:title"/></div>
            </td>
        </tr>
        <tr>
            <td colspan="3">
                <xsl:if test="dc:creator">
                    <p>
                        By 
                        <xsl:for-each select="dc:creator">
                            <xsl:if test="position() &gt; 1">
                                <xsl:text>; </xsl:text>
                            </xsl:if>
                            <a href="biblio.xql?field1=au&amp;term1={java:java.net.URLEncoder.encode(., 'UTF-8')}&amp;mode1=near">
                                <xsl:value-of select="."/>
                            </a>
                        </xsl:for-each>
                    </p>
                </xsl:if>
                <xsl:if test="dc:editor">
                    <p>
                        Ed. by 
                        <xsl:for-each select="dc:editor">
                            <xsl:if test="position() &gt; 1">
                                <xsl:text>; </xsl:text>
                            </xsl:if>
                            <a href="biblio.xql?field1=au&amp;term1={java:java.net.URLEncoder.encode(text(), 'UTF-8')}&amp;mode1=near">
                                <xsl:value-of select="."/>
                            </a>
                        </xsl:for-each>
                    </p>
                </xsl:if>
                <blockquote>
                    <xsl:apply-templates select="dc:description"/>
                </blockquote>
                
                <table border="0" cellspacing="8" cellpadding="0" class="details">
                    <tr>
                        <td class="details_heading" valign="top">Subjects:</td>
                        <td>
                            <xsl:for-each select="dc:subject">
                                <xsl:if test="position() &gt; 1">
                                    <xsl:text>; </xsl:text>
                                </xsl:if>
                                <a href="biblio.xql?field1=su&amp;term1={java:java.net.URLEncoder.encode(.)}&amp;mode1=exact">
                                    <xsl:apply-templates/>
                                </a>
                            </xsl:for-each>
                        </td>
                    </tr>
                    <xsl:if test="dc:language">
                        <tr>
                            <td class="details_heading">Language:</td>
                            <td><xsl:value-of select="dc:language"/></td>
                        </tr>
                    </xsl:if>
                    <xsl:if test="x:place">
                        <tr>
                            <td class="details_heading">Place:</td>
                            <td><xsl:value-of select="x:place"/></td>
                        </tr>
                    </xsl:if>
                    <xsl:if test="dc:date">
                        <tr>
                            <td class="details_heading">Date:</td>
                            <td><xsl:value-of select="dc:date"/></td>
                        </tr>
                    </xsl:if>
                    <xsl:if test="dc:identifier">
                        <tr>
                            <td class="details_heading">Identifier:</td>
                            <td><xsl:value-of select="dc:identifier"/></td>
                        </tr>
                    </xsl:if>
                </table>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="exist:match">
	    <span style="background-color: #FFFF00"><xsl:apply-templates/></span>
    </xsl:template>
    
  	<xsl:template match="node()|@*" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>
