<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m="http://www.loc.gov/mods/v3"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    xmlns:java="http://xml.apache.org/xslt/java"
    version="1.0">
    
    <xsl:include href="mods-common.xsl"/>
    
    <xsl:template match="items">
            <form action="overview.xq" method="GET">
                <div id="navigation">
                    <table cellspacing="0">
                        <tr>
                            <td width="60">Display:</td>
                            <td width="60">
                                <select name="howmany" onChange="form.submit()">
                                    <option>10</option>
                                    <option selected="true">50</option>
                                    <option>100</option>
                                </select>
                            </td>
                            <td/>
                            <td width="60">Order by:</td>
                            <td align="right" width="60">
                                <select name="order" onChange="form.submit()">
                                    <option>Date</option>
                                    <option value="title">Title</option>
                                    <option value="creator">Creator</option>
                                </select>
                            </td>
                            <input type="hidden" name="start" value="{@start}"/>
                        </tr>
                        <tr>
                            <td align="left" class="navbar" colspan="2">
                                <xsl:if test="@start &gt; 1">
                                    <a href="?start={@start - @max}&amp;howmany={@max}&amp;view={@view}">&lt;&lt; previous</a>
                                </xsl:if>
                            </td>
                            <td align="center" class="navbar">
                                Showing hits <xsl:value-of select="@start"/> to 
                                <xsl:value-of select="@next - 1"/> of
                                <xsl:value-of select="@hits"/>
                            </td>
                            <td align="right" class="navbar" colspan="2">
                                <xsl:if test="@next &lt;= @hits">
                                    <a href="?start={@next}&amp;howmany={@max}&amp;view={@view}">more &gt;&gt;</a>
                                </xsl:if>
                            </td>
                        </tr>
                    </table>
                </div>
                <xsl:apply-templates select="item"/>
            </form>
    </xsl:template>
    
    <xsl:template match="item">
        <div class="record">
            <table width="100%">
                <tr>
                    <td class="actionhead">
                        <a href="?start={@pos}&amp;howmany={../@max}&amp;view=details">Details</a>
                        <xsl:text> | </xsl:text>
                        <xsl:if test="m:location/m:url">
                            <a href="{m:location/m:url}">View Document</a>
                        </xsl:if>
                    </td>
                    <td class="xmllink">
                        <a target="_new" href="../xmldb/{../@collection}/{@doc}">
                            <img src="images/xml_rss.gif"/>
                        </a>
                    </td>
                </tr>
            </table>
            <xsl:apply-templates select="m:titleInfo"/>
            <xsl:if test="m:name">
                <p class="keywords">
                    <span class="heading">By: </span>
                    <xsl:apply-templates select="m:name"/>
                </p>
            </xsl:if>
            <xsl:if test="m:originInfo/m:publisher">
                <p class="keywords">
                    <span class="heading">Published By: </span>
                    <xsl:apply-templates select="m:originInfo/m:publisher"/>
                </p>
            </xsl:if>
            <xsl:if test="m:subject">
                <p class="keywords">
                    <span class="heading">Topics: </span>
                    <xsl:apply-templates select="m:subject/m:topic|m:subject/m:geographic"/>
                </p>
            </xsl:if>
            <xsl:apply-templates select="m:abstract"/>
            <xsl:apply-templates select="m:typeOfResource"/>
            <xsl:apply-templates select="m:identifier"/>
            <xsl:apply-templates select="m:originInfo"/>
            <xsl:apply-templates select="m:physicalDescription"/>
        </div>
    </xsl:template>
    
    <xsl:template match="p">
        <p><xsl:apply-templates/></p>
    </xsl:template>
    
    <xsl:template match="exist:match">
	    <span class="hit"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template name="display-link">
        <xsl:param name="url"/>
        <a href="{$url}">View Document</a>
    </xsl:template>
    
    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
    </xsl:template>
    
</xsl:stylesheet>
