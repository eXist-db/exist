<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m="http://www.loc.gov/mods/v3"
    xmlns:exist="http://exist.sourceforge.net/NS/exist"
    xmlns:java="http://xml.apache.org/xslt/java"
    version="1.0">
    
    <xsl:template match="items">
        <xsl:variable name="summary" select="floor((@start - 1) div @max) * @max + 1"/>
        <div>
            <table class="overview" cellspacing="0">
                <xsl:call-template name="navigation">
                    <xsl:with-param name="position" select="'top'"/>
                    <xsl:with-param name="summary" select="$summary"/>
                </xsl:call-template>
                <xsl:apply-templates/>
                <xsl:call-template name="navigation">
                    <xsl:with-param name="position" select="'bottom'"/>
                    <xsl:with-param name="summary" select="$summary"/>
                </xsl:call-template>
            </table>
        
            <div class="blog_shadow"/>
        </div>
    </xsl:template>
    
    <xsl:template name="navigation">
        <xsl:param name="position"/>
        <xsl:param name="summary"/>
        <tr class="result-head">
            <th class="nav{$position}" width="20%">
                <xsl:if test="@start &gt; 1">
                    <a class="mods" href="?start={@start - 1}&amp;display=details&amp;max={@max}">
                        &lt;&lt; Previous
                    </a>
                </xsl:if>
            </th>
            <th class="nav{$position}" width="60%">
                <a class="mods" href="?start={$summary}&amp;display=summary&amp;max={@max}">
                    <span class="icondesc">[Back to Overview]</span>
                </a>
            </th>
            <th class="nav{$position}" width="20%">
                <xsl:if test="number(@next) &lt; @hits">
                    <a class="mods" href="?start={@next}&amp;display=details&amp;max={@max}">
                        Next &gt;&gt;
                    </a>
                </xsl:if>
            </th>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:mods">
        <tr>
            <td colspan="3">
                <xsl:apply-templates select="m:titleInfo"/>
                <div class="mods_names">
                    <xsl:text>by </xsl:text><xsl:apply-templates select="m:name"/>
                </div>
                <xsl:apply-templates select="m:abstract"/>
                <table class="details" cellpadding="3">
                    <xsl:if test="m:subject">
                        <tr>
                            <td class="details_heading">Subjects:</td>
                            <td>
                                <xsl:apply-templates 
                                    select="m:subject/m:topic|m:geographic"/>
                            </td>
                        </tr>
                    </xsl:if>
                    <xsl:apply-templates select="m:typeOfResource"/>
                    <xsl:apply-templates select="m:identifier"/>
                    <xsl:apply-templates select="m:originInfo"/>
                </table>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:abstract">
        <div class="mods_abstract">
            <xsl:apply-templates/>
        </div>
    </xsl:template>
    
    <xsl:template match="m:titleInfo[not(@type)]">
        <div class="dc_title">
            <xsl:for-each select="m:nonSort|m:title">
                <xsl:apply-templates select="."/><xsl:text> </xsl:text>
            </xsl:for-each>
        </div>
        <xsl:if test="m:subTitle">
            <div class="mods_subTitle">
                <xsl:apply-templates select="m:subTitle"/>
            </div>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="m:titleInfo[@type]"/>
    
    <xsl:template match="m:name">
        <xsl:if test="position() &gt; 1">
            <xsl:text>; </xsl:text>
        </xsl:if>
        <xsl:choose>
            <xsl:when test="m:namePart[not(@type)]">
                <a class="mods"
                    href="?field=au&amp;query={java:java.net.URLEncoder.encode(m:namePart[not(@type)], 'UTF-8')}&amp;mode1=near&amp;max={ancestor::query-results/@max}">
                    <xsl:apply-templates select="m:namePart[not(@type)]"/>
                </a>
            </xsl:when>
            <xsl:when test="m:namePart[@type='family']">
                <xsl:variable name="name">
                    <xsl:value-of select="m:namePart[@type='family']"/>, <xsl:value-of select="m:namePart[@type='given']"/>
                </xsl:variable>
                <a class="mods"
                    href="?field=au&amp;query={java:java.net.URLEncoder.encode($name, 'UTF-8')}&amp;mode1=near&amp;max={ancestor::query-results/@max}">
                    <xsl:apply-templates select="m:namePart[@type='family']"/>
                    <xsl:text>, </xsl:text>
                    <xsl:apply-templates select="m:namePart[@type='given']"/>
                </a>
            </xsl:when>
        </xsl:choose>
        <xsl:if test="m:namePart[@type='date']">
            <xsl:text> [</xsl:text>
            <xsl:value-of select="m:namePart[@type='date']"/>
            <xsl:text>]</xsl:text>
        </xsl:if>
        <xsl:if test="m:role">
            <xsl:text> (</xsl:text>
            <xsl:value-of select="m:role/m:roleTerm|m:role/m:text"/>
            <xsl:text>)</xsl:text>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="m:typeOfResource">
        <tr>
            <td class="details_heading">Type of Resource:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:subject">
        <xsl:apply-templates select="m:topic"/>
    </xsl:template>
    
    <xsl:template match="m:topic">
        <xsl:if test="position() != 1">
            <xsl:text>; </xsl:text>
        </xsl:if>
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="m:originInfo">
        <xsl:apply-templates select="m:edition"/>
        <xsl:apply-templates select="m:publisher"/>
        <xsl:apply-templates select="m:place"/>
        <xsl:apply-templates select="m:copyrightDate"/>
        <xsl:apply-templates select="m:dateIssued[1]"/>
    </xsl:template>
    
    <xsl:template match="m:dateIssued">
        <tr>
            <td class="details_heading">Date Issued:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:copyrightDate">
        <tr>
            <td class="details_heading">Copyright Date:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:edition">
        <tr>
            <td class="details_heading">Edition:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:place">
        <xsl:apply-templates select="m:placeTerm[@type='text']"/>
    </xsl:template>
    
    <xsl:template match="m:placeTerm">
        <tr>
            <td class="details_heading">Place:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:publisher">
        <tr>
            <td class="details_heading">Publisher:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="m:identifier">
        <tr>
            <td class="details_heading">
                Identifier (<xsl:value-of select="@type"/>)
            </td>
            <td>
                <xsl:choose>
                    <xsl:when test="@type='uri'">
                        <a href="{text()}">
                            <xsl:apply-templates/>
                        </a>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates/>
                    </xsl:otherwise>
                </xsl:choose>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="exist:match">
	    <span class="hit"><xsl:apply-templates/></span>
    </xsl:template>
    
</xsl:stylesheet>
