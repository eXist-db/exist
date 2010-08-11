<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
    xmlns="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:bf="http://betterform.sourceforge.net/xforms"
    exclude-result-prefixes="xforms bf"
    xpath-default-namespace= "http://www.w3.org/1999/xhtml">

    <xsl:import href="dojo.xsl"/>
    <!-- ### CDN support is not avaibable for this stylesheet -->
    <xsl:variable name="useCDN" select="'false'"/>

    <xsl:template name="addDojoCSS"><xsl:text>
</xsl:text>
                <style type="text/css">
                    <xsl:choose>
                        <xsl:when test="//body/@class='soria'">
                    @import "<xsl:value-of select="$contextroot"/>/resources/scripts/dijit/themes/soria/soria.css";
                        </xsl:when>
                        <xsl:otherwise>
                    @import "<xsl:value-of select="$contextroot"/>/resources/scripts/dijit/themes/tundra/tundra.css";
                        </xsl:otherwise>
                    </xsl:choose>
                    @import "<xsl:value-of select="$contextroot"/>/resources/scripts/dojo/resources/dojo.css";
                    @import "<xsl:value-of select="$contextroot"/>/resources/scripts/dojox/widget/Toaster/Toaster.css";
                    @import "<xsl:value-of select="$contextroot"/>/resources/scripts/dojox/layout/resources/FloatingPane.css";
	                @import "<xsl:value-of select="$contextroot"/>/resources/scripts/dojox/layout/resources/ResizeHandle.css";
                </style><xsl:text>
</xsl:text>
    </xsl:template>

    <xsl:template name="addDojoConfig">
        <script type="text/javascript">
            var djConfig = {
                debugAtAllCost:<xsl:value-of select="$debug-enabled"/>,
                locale:'<xsl:value-of select="$locale"/>',
                isDebug:<xsl:value-of select="$debug-enabled"/>,
                parseOnLoad:false
            };
        </script><xsl:text>
</xsl:text>
    </xsl:template>

    <!-- ### the dev stylesheet currently does not support to use a CDN in conjunction with local code but
    that's probably a exotic use case. -->
    <xsl:template name="addDojoImport">
        <script type="text/javascript" src="{concat($contextroot,$scriptPath,'dojo/dojo.js')}"> </script><xsl:text>
</xsl:text>
    </xsl:template>

    <xsl:template name="addDojoRequires">
                dojo.require("betterform.FluxProcessor");
                dojo.require("betterform.XFormsModelElement");
                dojo.require("betterform.ui.Control");
                dojo.require("betterform.ui.util");
                // needed for alert Handling
                dojo.require("dijit.Tooltip");
                dojo.require("betterform.ui.container.Repeat");
                dojo.require("betterform.ui.container.RepeatItem");

                <!--dojo.require("betterform.ui.container.Container");-->

                <!--dojo.require("dojo.parser");-->
                <!--dojo.require("dojo.fx");-->
                <!--dojo.require("dojo.NodeList-fx");-->
                <!--dojo.require("dojo.dnd.Selector");-->
                <!--dojo.require("dojo.dnd.Source");-->


                <!--dojo.require("dijit.Dialog");-->
                <!--dojo.require("dijit.TitlePane");-->
               <!-- dojo.require("dijit.Tooltip");-->
                <!--dojo.require("dijit.form.CheckBox");-->
                <!--dojo.require("dijit.form.Button");-->
                <!--dojo.require("dijit.layout.ContentPane");-->
                <!--dojo.require("dijit.layout.TabContainer");-->
                <!--dojo.require("dijit.layout.BorderContainer");-->
                <!--dojo.require("dijit.layout.AccordionContainer");-->
                <!--dojo.require("dojox.layout.FloatingPane");-->

                <!--dojo.require("dojox.widget.FisheyeLite");-->
                <!--dojo.require("dojox.widget.Toaster");-->
                <!-- Not used? -->
                <!-- dojo.require("betterform.ui.textarea.MinimalTextarea"); -->
                <!--dojo.require("betterform.ui.upload.Upload");-->
                <!-- Only used is in OutputElementFactory -->
                <!-- dojo.require("betterform.ui.tree.OPMLTree"); -->
    </xsl:template>

</xsl:stylesheet>
