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
        <div id="content">
            <div id="navigation">
                <form action="overview.xq" method="GET">
                    <table>
                        <tr>
                            <th align="left">Display:</th>
                            <th align="right">Order by:</th>
                        </tr>
                        <tr>
                            <td align="left">
                                <select name="howmany" onChange="form.submit()">
                                    <option>10</option>
                                    <option selected="true">50</option>
                                    <option>100</option>
                                </select>
                            </td>
                            <td align="right">
                                <select name="order" onChange="form.submit()">
                                    <option>Date</option>
                                    <option value="title">Title</option>
                                    <option value="creator">Creator</option>
                                </select>
                            </td>
                            <input type="hidden" name="start" value="{@start}"/>
                        </tr>
                        <tr>
                            <td align="left">
                                <xsl:if test="@start &gt; 1">
                                    <a href="?start={@start - @max}&amp;howmany={@max}">&lt;&lt; previous</a>
                                </xsl:if>
                            </td>
                            <td align="right">
                                <xsl:if test="@next &lt;= @hits">
                                    <a href="?start={@next}&amp;howmany={@max}">more &gt;&gt;</a>
                                </xsl:if>
                            </td>
                        </tr>
                    </table>
                </form>
            </div>
            <xsl:apply-templates select="item"/>
        </div>
    </xsl:template>
    
    <xsl:template match="item">
        <div class="record">
            <a href="?action=remove&amp;doc={java:java.net.URLEncoder.encode(@doc)}&amp;collection={java:java.net.URLEncoder.encode(../@collection)}"><img src="images/delete.gif"/></a>
            <p class="citation">
                <input type="checkbox" class="mark" value="N1"/>
                <xsl:apply-templates select="m:titleInfo"/>
            </p>
            <p class="keywords">
                <span class="heading">By: </span>
                <xsl:apply-templates select="m:name"/>
            </p>
            <xsl:if test="m:subject">
                <p class="keywords">
                    <span class="heading">Topics: </span>
                    <xsl:apply-templates select="m:subject/m:topic|m:subject/m:geographic"/>
                </p>
            </xsl:if>
            <xsl:apply-templates select="m:abstract"/>
        </div>
    </xsl:template>
    
    <xsl:template match="p">
        <p><xsl:apply-templates/></p>
    </xsl:template>
    
    <xsl:template match="exist:match">
	    <span class="hit"><xsl:apply-templates/></span>
    </xsl:template>
    
    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
    </xsl:template>
    
</xsl:stylesheet>
