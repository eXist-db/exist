<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    version="1.0">
    
    <xsl:template match="query-results">
        <table class="overview" cellspacing="0" border="0" cellpadding="2" bgcolor="#F3F3F3">
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
        <tr class="result-head" width="100%">
            <th class="nav{$position}" align="left" width="20%">
                <xsl:if test="@start &gt; 1">
                    <a href="biblio.xql?start={@start - @max}&amp;howmany={@max}">
                        &lt;&lt; Previous
                    </a>
                </xsl:if>

            </th>
            <th class="nav{$position}" align="center" width="60%">
                Displaying hits <xsl:value-of select="@start"/> to <xsl:value-of select="@next - 1"/>
                (total: <xsl:value-of select="@hits"/>)<br/>
                <a href="biblio.xml">New Query</a>
            </th>
            <th class="nav{$position}" align="right" width="20%">
                <xsl:if test="number(@next) &lt;= @hits">
                    <a href="biblio.xql?start={@next}&amp;howmany={@max}">
                        Next &gt;&gt;
                    </a>
                </xsl:if>

            </th>
        </tr>
        <xsl:if test="$position='top'">
            <tr bgcolor="#99CCFF" width="100%">
                <th class="navtop" align="left" width="20%">
                    <a href="biblio.xql?order=creator&amp;start={@start}&amp;howmany={@max}">Creator/Editor</a>
                </th>
                <th class="navtop" align="left" width="60%">
                    <a href="biblio.xql?order=title&amp;start={@start}&amp;howmany={@max}">Title</a>
                </th>
                <th class="navtop" align="left" width="20%">
                    <a href="biblio.xql?order=date&amp;start={@start}&amp;howmany={@max}">Year</a>
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
                <a href="biblio.xql?start={../@start + position() -
                1}&amp;howmany={../@max}&amp;display=details">
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
    
  	<xsl:template match="node()|@*" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>
