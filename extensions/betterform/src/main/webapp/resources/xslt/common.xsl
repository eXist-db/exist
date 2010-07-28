<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:bf="http://betterform.sourceforge.net/xforms"
    xmlns:xf="http://www.w3.org/2002/xforms"
    exclude-result-prefixes="xf bf xsl">

    <!-- ### this url will be used to build the form action attribute ### -->
    <xsl:param name="sessionKey" select="''"/>
    <xsl:param name="action-url" select="''"/>

    <xsl:variable name="xf" select="'xf'"/>

    <xsl:template match="/">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="xhtml:html">
        <html>
            <xsl:apply-templates/>
        </html>
    </xsl:template>

    <xsl:template match="xhtml:link">
        <xsl:copy-of select="."/>
    </xsl:template>

    <!-- ### skip bf:data elements ### -->
    <xsl:template match="bf:data"/>

    <!-- ### skip model section ### -->
    <xsl:template match="xf:model"/>

    <xsl:template match="xf:model" mode="inline"/>

        <!-- ### handle extensions ### -->
    <xsl:template match="xf:extension">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="bf:selector">
    </xsl:template>

    <xsl:template match="xhtml:span">
        <span>
            <xsl:if test="@class">
                <xsl:attribute name="class">
                    <xsl:value-of select="@class"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:if test="@id">
                <xsl:attribute name="id">
                    <xsl:value-of select="@id"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:if test="@style">
                <xsl:attribute name="style">
                    <xsl:value-of select="@style"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:apply-templates/>
        </span>
    </xsl:template>

    <!-- copy unmatched mixed markup, comments, whitespace, and text -->
    <!-- ### copy elements from the xhtml2 namespace to html (without any namespace) by re-creating the     ### -->
    <!-- ### elements. Other Elements are just copied with their original namespaces.                       ### -->
    <xsl:template match="*|@*|text()|comment()" name="handle-foreign-elements">
        <xsl:choose>
            <xsl:when test="namespace-uri(.)='http://www.w3.org/1999/xhtml'">
                <xsl:element name="{local-name(.)}" namespace="">
                    <xsl:apply-templates select="*|@*|text()|comment()"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="*|@*|text()|comment()"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="*|@*|text()|comment()" mode="inline">
        <xsl:choose>
            <xsl:when test="namespace-uri(.)='http://www.w3.org/1999/xhtml'">
                <xsl:element name="{local-name(.)}" namespace="">
                    <xsl:apply-templates select="*|@*|text()|comment()" mode="inline"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="*|@*|text()|comment()" mode="inline"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="getMeta">
        <xsl:variable name="uc">ABCDEFGHIJKLMNOPQRSTUVWXYZ</xsl:variable>
        <xsl:variable name="lc">abcdefghijklmnopqrstuvwxyz</xsl:variable>
        <xsl:for-each select="xhtml:meta">
            <xsl:choose>
                <xsl:when test="translate(./@http-equiv, $uc, $lc) = 'content-type'"/>
                <xsl:otherwise>
                    <meta>
                        <xsl:copy-of select="@*"/>
                    </meta>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="getLinkAndStyle"><xsl:text>
</xsl:text><xsl:for-each select="xhtml:link|xhtml:style">
            <xsl:element name="{local-name()}">
                <xsl:copy-of select="@*"/>
                <xsl:apply-templates/>
            </xsl:element>
        </xsl:for-each><xsl:text>
</xsl:text>
    </xsl:template>

    <!-- #### Note - the whitespace in this template shouldn't be touched to produce line breaks in the output -->
    <xsl:template name="addDWRImports">
        <!-- for DWR AJAX -->
        <script type="text/javascript" src="{concat($contextroot,'/Flux/engine.js')}">&#160;</script><xsl:text>
</xsl:text>
        <!-- for DWR AJAX -->
        <script type="text/javascript" src="{concat($contextroot,'/Flux/interface/Flux.js')}">&#160;</script><xsl:text>
</xsl:text>
        <script type="text/javascript" src="{concat($contextroot,'/Flux/interface/XFormsModelElement.js')}">&#160;</script><xsl:text>
</xsl:text>
        <!-- for DWR AJAX -->
        <script type="text/javascript" src="{concat($contextroot,'/Flux/util.js')}">&#160;</script><xsl:text>
</xsl:text>
    </xsl:template>

    <xsl:template name="copyInlineScript">
        <!-- copy inline javascript -->
        <xsl:for-each select="xhtml:script">
            <script>
                    <xsl:attribute name="type">
                        <xsl:value-of select="@type"/>
                    </xsl:attribute>
                    <xsl:if test="@src">
                    <xsl:attribute name="src">
                        <xsl:value-of select="@src"/>
                    </xsl:attribute>
                    </xsl:if>   
                    <xsl:apply-templates mode="inline"/>
            </script>
                <xsl:text>
</xsl:text>
        </xsl:for-each>
    </xsl:template>

    <!-- to be overwritten by dev stylessheets -->
    <xsl:template name="addDojoRequires"/>

    <xsl:template name="assemble-control-classes">
        <xsl:param name="appearance"/>

        <xsl:variable name="name-classes">
            <xsl:call-template name="get-name-classes">
                <xsl:with-param name="appearance" select="$appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:variable name="type">
            <xsl:call-template name="getXSDType"/>
        </xsl:variable>

        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes"/>
        </xsl:variable>

        <xsl:variable name="author-classes">
            <xsl:call-template name="get-author-classes"/>
        </xsl:variable>

        <xsl:variable name="incremental">
            <xsl:choose>
                <xsl:when test="@incremental ='true'">xfIncremental</xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:variable>

        <!-- *** this is to ease styling of repeated controls and scripting *** -->
        <xsl:variable name="repeat-id" select="ancestor::*[name(.)='xf:repeat'][1]/@id" />
        <xsl:variable name="pos" select="position()" />

        <xsl:variable name="repeatClasses">
            <xsl:choose>
                <xsl:when test="boolean(string-length($repeat-id) > 0)">
                    <xsl:value-of select="concat($repeat-id,'-',$pos,' repeated')"/>
                </xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:variable>
        <!--<xsl:value-of select="normalize-space(concat('xf-control ',$name-classes, ' ', 'xs-',$type ,' ',$mip-classes, ' ', $author-classes,' ',$incremental,' ',$repeatClasses))"/>-->
        <xsl:value-of select="normalize-space(concat('xfControl ',$name-classes, ' ', $type ,' ',$mip-classes, ' ', $author-classes,' ',$incremental,' ',$repeatClasses))"/>
    </xsl:template>

    <xsl:template name="getXSDType">
        <xsl:variable name="plainType">
            <xsl:call-template name="getType"/>
        </xsl:variable>

        <xsl:variable name="fullName"><xsl:call-template name="toUpperCaseFirstLetter"><xsl:with-param name="name" select="$plainType"/></xsl:call-template></xsl:variable>
        <xsl:value-of select="if(string-length($fullName)=0) then '' else concat('xsd', $fullName)"/>
    </xsl:template>

    <xsl:template name="getType">
        <xsl:choose>
            <xsl:when test="contains(bf:data/@bf:type,':')">
                <xsl:value-of select="substring-after(bf:data/@bf:type,':')"/>
            </xsl:when>
            <xsl:when test="bf:data/@bf:type">
                <xsl:value-of select="bf:data/@bf:type"/>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="get-name-classes">
        <xsl:param name="name" select="local-name()"/>
        <xsl:param name="appearance"/>


        <xsl:variable name="fullName"><xsl:call-template name="toUpperCaseFirstLetter"><xsl:with-param name="name" select="$name"/></xsl:call-template></xsl:variable>
        <xsl:variable name="displayAppearance"><xsl:call-template name="toUpperCaseFirstLetter"><xsl:with-param name="name" select="$appearance"/></xsl:call-template></xsl:variable>
        <!--<xsl:message>fullName:<xsl:value-of select="$fullName"/> appearance:<xsl:value-of select="$appearance"/> DisplayAppearance:<xsl:value-of select="$displayAppearance"/></xsl:message>-->
        <xsl:choose>
            <xsl:when test="$appearance">
                <!--<xsl:value-of select="concat($xf,$name, ' ', $appearance, '-',$name)"/>-->
                <!--<xsl:value-of select="concat($xf,$fullName, ' ', $xf,$displayAppearance,$fullName)"/>-->
                <xsl:value-of select="concat($xf,$displayAppearance,$fullName)"/>
                <!--<xsl:message>computedClassAppearance : <xsl:value-of select="concat($xf,$fullName, ' ', $xf,$displayAppearance,$fullName)"/></xsl:message>-->
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="concat($xf,$fullName)"/>
                <!--<xsl:message>computedClass : <xsl:value-of select="concat($xf,$fullName)"/></xsl:message>-->
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="toUpperCaseFirstLetter">
        <xsl:param name="name"/>
        <xsl:variable name="start" select="upper-case(substring($name,1,1))"/>
        <xsl:variable name="end" select="substring($name,2)"/>
<!--
        <xsl:message>start:<xsl:value-of select="$start"/></xsl:message>
        <xsl:message>end:<xsl:value-of select="$end"/></xsl:message>
-->
        <xsl:value-of select="concat($start,$end)"/>
    </xsl:template>

    <xsl:template name="assemble-label-classes">
        <xsl:for-each select="xf:label[1]">
            <xsl:variable name="name-classes">
                <xsl:call-template name="get-name-classes"/>
            </xsl:variable>
            <xsl:variable name="mip-classes">
                <xsl:call-template name="get-mip-classes">
                    <xsl:with-param name="limited" select="true()"/>
                </xsl:call-template>
            </xsl:variable>
            <xsl:variable name="author-classes">
                <xsl:call-template name="get-author-classes"/>
            </xsl:variable>

            <xsl:value-of select="normalize-space(concat($name-classes, ' ', $mip-classes, ' ', $author-classes))"/>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="get-mip-classes">
        <xsl:param name="limited" select="false()"/>

        <xsl:if test="bf:data">
            <xsl:choose>
                <xsl:when test="boolean($limited)">
                    <xsl:choose>
                        <xsl:when test="bf:data/@bf:enabled='false'">
                            <xsl:text>xfDisabled</xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>xfEnabled</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:variable name="enabled">
                        <xsl:choose>
                            <xsl:when test="bf:data/@bf:enabled='false'">
                                <xsl:text>xfDisabled</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>xfEnabled</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="readonly">
                        <xsl:choose>
                            <xsl:when test="bf:data/@bf:readonly='true'">
                                <xsl:text>xfReadOnly</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>xfReadWrite</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="required">
                        <xsl:choose>
                            <xsl:when test="bf:data/@bf:required='true'">
                                <xsl:text>xfRequired</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>xfOptional</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="valid">
                        <xsl:choose>
                            <xsl:when test="bf:data/@bf:valid='false'">
                                <xsl:text>xfInvalid</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>xfValid</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:value-of select="concat($enabled,' ',$readonly,' ',$required, ' ', $valid)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:if>
    </xsl:template>

    <!-- ### CSS CLASS ASSEMBLY HELPERS ### -->
    <xsl:template name="get-author-classes">
        <xsl:choose>
            <xsl:when test="@class">
                <xsl:value-of select="@class"/>
            </xsl:when>
            <xsl:when test="@xhtml:class">
                <xsl:value-of select="@xhtml:class"/>
            </xsl:when>
        </xsl:choose>
    </xsl:template>


    <!-- ************************************************************************************************ -->
    <!-- ********************************** COMMON TEMPLATES FOR CONTAINER ELEMENTS ********************* -->
    <!-- ************************************************************************************************ -->
    <!-- ### largely redundant with 'assemble-label-classes' but sets 'group-label' instead of 'label' ### -->
    <xsl:template name="assemble-group-label-classes">
        <xsl:for-each select="xf:label[1]">
            <xsl:variable name="name-classes">xfGroupLabel</xsl:variable>
            <xsl:variable name="mip-classes">
                <xsl:call-template name="get-mip-classes">
                    <xsl:with-param name="limited" select="true()"/>
                </xsl:call-template>
            </xsl:variable>
            <xsl:variable name="author-classes">
                <xsl:call-template name="get-author-classes"/>
            </xsl:variable>

            <xsl:value-of select="normalize-space(concat($name-classes, ' ', $mip-classes, ' ', $author-classes))"/>
        </xsl:for-each>
    </xsl:template>

    <!-- assembles group/switch/repeat classes -->
    <xsl:template name="assemble-compound-classes">
        <xsl:param name="appearance"/>

        <xsl:variable name="name-classes">
            <xsl:call-template name="get-name-classes">
                <xsl:with-param name="appearance" select="$appearance"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes">
                <xsl:with-param name="limited" select="false()"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="author-classes">
            <xsl:call-template name="get-author-classes"/>
        </xsl:variable>

        <xsl:value-of select="normalize-space(concat('xfContainer',' ',$name-classes, ' ', $mip-classes, ' ', $author-classes))"/>
    </xsl:template>

    <!-- assembles repeat item classes -->
    <xsl:template name="assemble-repeat-item-classes">
        <xsl:param name="selected"/>

        <xsl:variable name="name-classes">
            <xsl:choose>
                <xsl:when test="boolean($selected)">
                    <xsl:text>xfRepeatItem xfRepeatIndex</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text>xfRepeatItem</xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes"/>
        </xsl:variable>
        <xsl:value-of select="normalize-space(concat($name-classes, ' ', $mip-classes))"/>
    </xsl:template>

    <!-- ########################## ACTIONS ####################################################### -->
    <!-- these templates serve no real purpose here but are shown for reference what may be over-   -->
    <!-- written by customized stylesheets importing this one. -->
    <!-- ########################## ACTIONS ####################################################### -->

    <xsl:template match="xf:action"/>
    <xsl:template match="xf:dispatch"/>
    <xsl:template match="xf:rebuild"/>
    <xsl:template match="xf:recalculate"/>
    <xsl:template match="xf:revalidate"/>
    <xsl:template match="xf:refresh"/>
    <xsl:template match="xf:setfocus"/>
    <xsl:template match="xf:load"/>
    <xsl:template match="xf:setvalue"/>
    <xsl:template match="xf:send"/>
    <xsl:template match="xf:reset"/>
    <xsl:template match="xf:message"/>
    <xsl:template match="xf:toggle"/>
    <xsl:template match="xf:insert"/>
    <xsl:template match="xf:delete"/>
    <xsl:template match="xf:setindex"/>

</xsl:stylesheet>
