<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    version="1.0">
    
    <xsl:include href="context://stylesheets/doc2html-2.xsl"/>
    
    <xsl:template match="query-results">
        <table border="0" cellpadding="7" cellspacing="0" width="100%" class="overview">
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
        <tr bgcolor="#D9D9D9" width="100%">
            <th class="nav{$position}" align="left" width="20%">
                <xsl:if test="@start &gt; 1">
                    <a href="biblio.xq?start={@start - 10}">
                        &lt;&lt; Previous
                    </a>
                </xsl:if>
            </th>
            <th class="nav{$position}" align="center" width="60%">
                Displaying hits <xsl:value-of select="@start"/> to <xsl:value-of select="@next - 1"/>
                (total: <xsl:value-of select="@hits"/>)
            </th>
            <th class="nav{$position}" align="right" width="20%">
                <xsl:if test="number(@next) &lt; @hits">
                    <a href="biblio.xq?start={@next}">
                        Next &gt;&gt;
                    </a>
                </xsl:if>
            </th>
        </tr>
        <xsl:if test="$position='top'">
            <tr bgcolor="#99CCFF" width="100%">
                <th class="navtop" align="left" width="20%">
                    <a href="biblio.xq?order=creator&amp;start={@start}">Creator/Editor</a>
                </th>
                <th class="navtop" align="left" width="60%">
                    <a href="biblio.xq?order=title&amp;start={@start}">Title</a>
                </th>
                <th class="navtop" align="left" width="20%">
                    <a href="biblio.xq?order=date&amp;start={@start}">Year</a>
                </th>
            </tr>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="item">
        <tr>
            <xsl:choose>
                <xsl:when test="position() mod 2 = 0">
                    <xsl:attribute name="bgcolor">#EEEEEE</xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="bgcolor">#FFFFFF</xsl:attribute>
                </xsl:otherwise>
            </xsl:choose>
            <td class="overview">
                <xsl:apply-templates select="creator"/>
            </td>
            <td class="overview">
                <a href="biblio.xq?start={../@start + position() - 2}&amp;display=details">
                    <xsl:apply-templates select="dc:title"/>
                </a>
            </td>
            <td class="overview"><xsl:value-of select="year"/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="dc:title">
    	<xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="exist:match">
	    <span style="background-color: #FFFF00"><xsl:apply-templates/></span>
    </xsl:template>
    
</xsl:stylesheet>
