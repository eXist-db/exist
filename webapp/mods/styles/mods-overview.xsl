<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m="http://www.loc.gov/mods/v3"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    xmlns:java="http://xml.apache.org/xslt/java"
    version="1.0">
    
    <xsl:template match="items">
        <div>
            <table class="overview" cellspacing="0">
                <xsl:call-template name="navigation">
                    <xsl:with-param name="position" select="'top'"/>
                </xsl:call-template>
                <xsl:apply-templates/>
                <xsl:call-template name="navigation">
                    <xsl:with-param name="position" select="'bottom'"/>
                </xsl:call-template>
            </table>
            
            <div class="blog_shadow"/>
        </div>
    </xsl:template>
    
    <xsl:template name="navigation">
        <xsl:param name="position"/>
        <tr class="result-head">
            <th class="nav{$position}" width="15%"> 
                <xsl:if test="@start &gt; 1">
                    <a class="mods" href="?start={@start - @max}&amp;max={@max}">
                        &lt;&lt; Previous
                    </a>
                </xsl:if>
            </th>
            <th class="nav{$position}" width="70%" colspan="2">
                Displaying items <xsl:value-of select="@start"/> to <xsl:value-of select="@next - 1"/>
                (total: <xsl:value-of select="@hits"/>)<br/>
                <span class="icondesc">
                    [<a class="mods" href="{@chiba}&amp;submitsave={java:java.net.URLEncoder.encode('store.xq')}">
                        Create New
                    </a>]
                </span>
            </th>
            <th class="nav{$position}" width="15%">
                <xsl:if test="number(@next) &lt; @hits">
                    <a class="mods" href="?start={@next}&amp;max={@max}">
                        Next &gt;&gt;
                    </a>
                </xsl:if>
            </th>
        </tr>
        <xsl:if test="$position='top'">
            <tr class="result-head">
                <th class="table-head" width="15%"></th>
                <th class="table-head" width="20%">
                    <a class="mods"
                        href="?order=creator&amp;start={@start}&amp;max={@max}">Creator/Editor</a>
                </th>
                <th class="table-head" width="50%">
                    <a class="mods"
                        href="?order=title&amp;start={@start}&amp;max={@max}">Title</a>
                </th>
                <th class="table-head" width="15%">
                    <a class="mods"
                        href="?order=date&amp;start={@start}&amp;max={@max}">Date</a>
                </th>
            </tr>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="item">
        <tr>
            <xsl:choose>
                <xsl:when test="position() mod 2 = 0">
                    <xsl:attribute name="class">row-1</xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="class">row-2</xsl:attribute>
                </xsl:otherwise>
            </xsl:choose>
            <td class="overview">
                <a href="{@chiba}&amp;instance=/chiba/exist/{@collection}/{@doc}&amp;submitsave=store.xq?document={@doc}">
                    <img src="images/edit.gif"/>
                </a>
                <a href="?action=remove&amp;doc={java:java.net.URLEncoder.encode(@doc)}&amp;collection={java:java.net.URLEncoder.encode(@collection)}"><img src="images/delete.gif"/></a>
            </td>
            <td class="overview">
                <xsl:apply-templates select="m:name"/>
            </td>
            <td class="overview">
                <a class="mods"
                    href="?start={../@start + position() - 1}&amp;display=details&amp;max={../@max}">
                    <xsl:apply-templates select="m:titleInfo"/>
                </a>
            </td>
            <td class="overview">
                <xsl:choose>
                    <xsl:when test="m:copyrightDate">
                        <xsl:value-of select="m:copyrightDate"/>
                    </xsl:when>
                    <xsl:when test="m:dateIssued[@type='marc']">
                        <xsl:value-of select="m:dateIssued[@type='marc']"/>
                    </xsl:when>
                    <xsl:when test="m:dateIssued">
                        <xsl:value-of select="m:dateIssued"/>
                    </xsl:when>
		    <xsl:when test="m:date">
		      <xsl:value-of select="m:date"/>
		    </xsl:when>
                </xsl:choose>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:titleInfo[not(@type)]">
        <xsl:for-each select="m:nonSort|m:title">
            <xsl:value-of select="."/><xsl:text> </xsl:text>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template match="m:name">
        <xsl:choose>
            <xsl:when test="m:namePart[not(@type)]">
                <xsl:apply-templates select="m:namePart[not(@type)]"/>
            </xsl:when>
            <xsl:when test="m:namePart[@type='family']">
                <xsl:apply-templates select="m:namePart[@type='family']"/>
                <xsl:text>, </xsl:text>
                <xsl:apply-templates select="m:namePart[@type='given']"/>
            </xsl:when>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="p">
        <p><xsl:apply-templates/></p>
    </xsl:template>
    
    <xsl:template match="exist:match">
	    <span class="hit"><xsl:apply-templates/></span>
    </xsl:template>
</xsl:stylesheet>
