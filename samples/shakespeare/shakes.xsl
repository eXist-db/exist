<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:exist="http://exist.sourceforge.net/NS/exist">

    <xsl:template match="/">
        <html>
            <head>
                <title>
                    <xsl:value-of select="PLAY/TITLE"/>
                </title>
                <link rel="stylesheet" type="text/css" href="shakes.css"/>
            </head>
            <body bgcolor="#ffffff">
                <xsl:apply-templates/>
            </body>
	</html>
    </xsl:template>
    <xsl:template match="PLAY/TITLE">
        <h1>
            <xsl:apply-templates/>
        </h1>
    </xsl:template>
    <xsl:template match="PLAY">
        <html>
            <xsl:apply-templates select="TITLE"/>
            <xsl:apply-templates select="PLAYSUBT"/>
            <xsl:apply-templates select="FM"/>
            
            <p><b>Table of Contents</b></p>
            <ul>
                <xsl:for-each select="PERSONAE|ACT">
                    <li>
                        <a href="#{generate-id()}">
                            <xsl:value-of select="TITLE"/>
                        </a>
                    </li>
                    <ul>
                        <xsl:for-each select="SCENE">                        
                            <li>
                                <a href="#{generate-id()}">
                                    <xsl:value-of select="TITLE"/>
                                </a>
                            </li>
                        </xsl:for-each>
                    </ul>
                </xsl:for-each>               
            </ul>
            <xsl:apply-templates select="PERSONAE"/>
            <xsl:apply-templates select="ACT"/>
        </html>
    </xsl:template>
    <xsl:template match="FM">
        <blockquote>
            <xsl:apply-templates/>
        </blockquote>
    </xsl:template>
    <xsl:template match="PERSONAE">
        <hr/>
        <h2>
            <a>
                <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
                <xsl:value-of select="TITLE"/>
            </a>
        </h2>
        <table border="0" cellpadding="5">
            <xsl:apply-templates select="PERSONA|PGROUP"/>
        </table>
    </xsl:template>
    <xsl:template match="ACT">
	   <hr/>        
        <h1>
            <a>
                <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
                <xsl:value-of select="TITLE"/>
            </a>
        </h1>        
        <xsl:apply-templates select="SCENE"/>
    </xsl:template>
    <xsl:template match="SCENE">
        <h3>
            <a>
                <xsl:attribute name="name"><xsl:value-of select="generate-id()"/></xsl:attribute>
                <xsl:value-of select="TITLE"/>            
            </a>
        </h3>
        <xsl:apply-templates select="SPEECH|STAGEDIR"/>
    </xsl:template>
    <xsl:template match="SPEECH">
        <table border="0" cellpadding="5" cellspacing="5" width="100%">
        <tr>
            <td width="20%" valign="top">
                <div class="speaker"><xsl:apply-templates select="SPEAKER"/></div>
            </td>
            <td width="4%"/>
            <td width="76%" valign="top">
                <verse>
                    <xsl:apply-templates select="LINE|STAGEDIR"/>
                </verse>
            </td>
        </tr>
        </table>
    </xsl:template>
    <xsl:template match="SPEAKER">
        <xsl:value-of select="text()"/>
        <br/>
    </xsl:template>
    
    <xsl:template match="LINE">
        <xsl:apply-templates/><br/>
    </xsl:template>
    
    <xsl:template match="LINE/STAGEDIR">[<b>
            <xsl:apply-templates/>
        </b>]</xsl:template>
    <xsl:template match="SCENE/STAGEDIR">
        <tr>
            <td colspan="3">
                <b>
                    <xsl:apply-templates/>
                </b>
            </td>
        </tr>
    </xsl:template>
    <xsl:template match="SUBHEAD">
        <tr>
            <td colspan="3">
                <h4>
                    <xsl:apply-templates/>
                </h4>
            </td>
        </tr>
    </xsl:template>
    <xsl:template match="PLAYSUBT">
        <h3>
            <em>
                <xsl:apply-templates/>
            </em>
        </h3>
    </xsl:template>
    <xsl:template match="SCNDESCR">
        <blockquote>
            <xsl:apply-templates/>
        </blockquote>
    </xsl:template>
    <xsl:template match="PERSONAE/PERSONA">
        <tr>
            <td colspan="2"><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="PGROUP">
        <tr>
            <td>
                <xsl:for-each select="PERSONA">
                    <xsl:value-of select="text()"/><br/>
                </xsl:for-each>
            </td>
            <td>
                <xsl:value-of select="GRPDESCR"/>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="P">
        <tt>
            <xsl:apply-templates/>
        </tt>
        <br/>
    </xsl:template>
</xsl:stylesheet>

