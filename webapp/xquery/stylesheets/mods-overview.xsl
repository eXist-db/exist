<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m="http://www.loc.gov/mods/v3"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    version="1.0">
    
    <xsl:include href="context://stylesheets/doc2html-2.xsl"/>
    
    <xsl:template match="query-results">
        <table class="overview">
            <xsl:call-template name="navigation">
                <xsl:with-param name="position" select="'top'"/>
            </xsl:call-template>
            <xsl:apply-templates/>
            <xsl:call-template name="navigation">
                <xsl:with-param name="position" select="'bottom'"/>
            </xsl:call-template>
        </table>
    </xsl:template>
    
    <xsl:template name="navigation">
        <xsl:param name="position"/>
        <tr class="result-head">
            <th class="nav{$position}" width="20%">
                <xsl:if test="@start &gt; 1">
                    <a class="mods" href="mods.xq?start={@start - @max}&amp;max={@max}">
                        &lt;&lt; Previous
                    </a>
                </xsl:if>
            </th>
            <th class="nav{$position}" width="60%">
                Displaying hits <xsl:value-of select="@start"/> to <xsl:value-of select="@next - 1"/>
                (total: <xsl:value-of select="@hits"/>)<br/>
                <span class="icondesc">[<a class="mods" href="mods.xml">New
                Query</a>]</span>
            </th>
            <th class="nav{$position}" width="20%">
                <xsl:if test="number(@next) &lt; @hits">
                    <a class="mods" href="mods.xq?start={@next}&amp;max={@max}">
                        Next &gt;&gt;
                    </a>
                </xsl:if>
            </th>
        </tr>
        <xsl:if test="$position='top'">
            <tr class="result-head">
                <th class="navtop" width="20%">
                    <a class="mods"
                        href="mods.xq?order=creator&amp;start={@start}&amp;max={@max}">Creator/Editor</a>
                </th>
                <th class="navtop" width="20%">
                    <a class="mods"
                        href="mods.xq?order=title&amp;start={@start}&amp;max={@max}">Title</a>
                </th>
                <th class="navtop" width="20%">
                    <a class="mods"
                        href="mods.xq?order=date&amp;start={@start}&amp;max={@max}">Date</a>
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
                <xsl:apply-templates select="m:name"/>
            </td>
            <td class="overview">
                <a class="mods"
                    href="mods.xq?start={../@start + position() - 1}&amp;display=details&amp;max={../@max}">
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
    
    <xsl:template match="exist:match">
	    <span class="hit"><xsl:apply-templates/></span>
    </xsl:template>
    
</xsl:stylesheet>
