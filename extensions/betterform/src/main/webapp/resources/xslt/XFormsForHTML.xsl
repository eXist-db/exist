<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
                xmlns:xhtml="http://www.w3.org/1999/xhtml"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xf="http://www.w3.org/2002/xforms"
                exclude-result-prefixes="xhtml xf">
    <xsl:import href="dojo-dev.xsl"/>
    
    <xsl:strip-space elements="*"/>

    <xsl:output method="xhtml" version="1.0" encoding="UTF-8" media-type="text/xml"/>


    <!-- copy template -->
<!--
    <xsl:template match="*|@*|text()">
        <xsl:copy>
            <xsl:apply-templates select="*|@*|text()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*|@*|text()" mode="UI">
        <xsl:copy>
            <xsl:apply-templates select="*|@*|text()" mode="UI"/>
        </xsl:copy>
    </xsl:template>
-->

    <xsl:template match="xf:model" mode="UI"/>

    <xsl:template match="xhtml:body">
        <body>
            <xsl:copy-of select="@*"/>
            <div id="caLoading">
                <img src="{concat($contextroot,'/resources/images/indicator.gif')}" class="disabled" id="indicator" alt="loading" />
            </div>

            <xsl:variable name="outermostNodeset"
                select=".//xf:*[not(xf:model)][not(ancestor::xf:*)]"/>

            <!-- detect how many outermost XForms elements we have in the body -->
            <xsl:choose>
                <xsl:when test="exists(xhtml:form)">
                    fooooobobooo
                    <xsl:apply-templates select="xhtml:form" mode="xforms4xhtml"/>
                </xsl:when>
                <xsl:when test="count($outermostNodeset) = 1">
                    <!-- match any body content and defer creation of form tag for XForms processing.
                     This option allows to mix HTML forms with XForms markup. -->
					<!-- todo: issue to revisit: this obviously does not work in case there's only one xforms control in the document. In that case the necessary form tag is not written. -->
					<!-- hack solution: add an output that you style invisible to the form to make it work again. -->
					<xsl:apply-templates mode="inline"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- in case there are multiple outermost xforms elements we are forced to create
                     the form tag for the XForms processing.-->
                    <xsl:call-template name="createForm"/>
                </xsl:otherwise>
            </xsl:choose>

            <span id="legend">
                <span id="required-msg">
                <span style="color:#A42322;">*</span> - required</span> |
                <b>?</b> - help
            </span>
            <div id="betterform-logo">
                <a href="resources/jsp/forms.jsp">
                    <img src="{concat($contextroot,'/resources/images/poweredby_sw.gif')}" style="border:none;" alt="powered by betterForm"/>
                </a>
            </div>
            <div id="copyright">
                <xsl:text disable-output-escaping="yes">&amp;copy; 2001-2007 betterForm Project</xsl:text>
            </div>
            <div id="messagePane"/>
            <!-- ############################# Debug section ################################ -->
            <xsl:if test="$debug-enabled='true'">
                <button onclick="getXFormsDOM();">
                    <label>DEBUG</label>
                </button>
                <div  id="debug-section"/>
            </xsl:if>

        </body>
    </xsl:template>


    <xsl:template match="xhtml:form" mode="xforms4xhtml">
        <xsl:variable name="formName" select="@name"/>

        <xsl:copy>
            <xsl:call-template name="createFormAttributes"/>

            <xsl:choose>
                <xsl:when test="xf:model">
                    <xsl:copy-of select="."/>
                </xsl:when>
                <xsl:otherwise>
                    <xf:model>
                        <xf:instance>
                            <data xmlns="">
                                <xsl:for-each select="*[@name]">
                                    <xsl:apply-templates select="." mode="generateDataInstance"/>
                                </xsl:for-each>
                            </data>
                        </xf:instance>
                    </xf:model>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:apply-templates select="*"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*[@name]" mode="generateDataInstance">
        <xsl:element name="{@name}">
            <xsl:if test="@value"><xsl:value-of select="@value"/></xsl:if>
            <xsl:apply-templates select="*[@name]" mode="generateDataInstance"/>
        </xsl:element>
    </xsl:template>

</xsl:stylesheet>