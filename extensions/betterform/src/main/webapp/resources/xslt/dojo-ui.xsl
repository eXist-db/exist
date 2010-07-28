<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:bf="http://betterform.sourceforge.net/xforms"
    exclude-result-prefixes="xhtml xforms bf">

    <!-- ####################################################################################################### -->
    <!-- This stylesheet handles the XForms UI constructs [XForms 1.0, Chapter 9]'group', 'repeat' and           -->
    <!-- 'switch' and offers some standard interpretations for the appearance attribute.                         -->
    <!-- author: joern turner                                                                                    -->
    <!-- author: lars windauer                                                                                   -->
    <!-- ####################################################################################################### -->

    <xsl:param name="betterform-pseudo-item" select="'betterform-pseudo-item'"/>
    <!-- ############################################ PARAMS ################################################### -->
    <!-- ##### should be declared in dojo.xsl ###### -->
    <!-- ############################################ VARIABLES ################################################ -->


    <xsl:output method="html" indent="yes"/>
    <xsl:preserve-space elements="*"/>

    <!-- ####################################################################################################### -->
    <!-- #################################### GROUPS ########################################################### -->
    <!-- ####################################################################################################### -->

    <xsl:template match="xforms:group" name="group" priority="10">
        <xsl:variable name="group-id" select="@id"/>
        <xsl:variable name="group-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:call-template name="group-body">
            <xsl:with-param name="group-id" select="$group-id"/>
            <xsl:with-param name="group-classes" select="$group-classes"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="group-body">
        <xsl:param name="group-id"/>
        <xsl:param name="group-classes"/>
        <xsl:param name="group-label" select="true()"/>
        <script type="text/javascript">dojo.require("betterform.ui.container.Group");</script><xsl:text>
</xsl:text>
        
        <span id="{$group-id}" class="{$group-classes}" dojoType="betterform.ui.container.Group">
            <span class="legend">
                <xsl:choose>
                    <xsl:when test="$group-label and xforms:label">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($group-id, '-label')"/>
                        </xsl:attribute>
                        <xsl:attribute name="class">
                            <xsl:call-template name="assemble-group-label-classes"/>
                        </xsl:attribute>
                        <xsl:apply-templates select="xforms:label"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="style">display:none;</xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
            </span>

            <xsl:apply-templates select="*[not(self::xforms:label)] | text()"/>
        </span>
    </xsl:template>

    <xsl:template name="group-body-repeated">
        <xsl:param name="group-id"/>
        <xsl:param name="group-classes"/>
        <xsl:param name="group-label" select="true()"/>

        <div id="{$group-id}" class="{$group-classes}" controlType="{local-name()}">
            <div class="legend">
                <xsl:choose>
                    <xsl:when test="$group-label and xforms:label">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($group-id, '-label')"/>
                        </xsl:attribute>
                        <xsl:attribute name="class">
                            <xsl:call-template name="assemble-group-label-classes"/>
                        </xsl:attribute>
                        <xsl:apply-templates select="xforms:label"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="style">display:none;</xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
            </div>

            <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-full-prototype"/>
        </div>
    </xsl:template>


    <!-- ######################################################################################################## -->
    <!-- ####################################### custom group with vertical layout ############################## -->
    <!-- ######################################################################################################## -->

    <xsl:template match="xforms:group[@appearance='bf:verticalTable']" priority="15">
        <xsl:variable name="group-id" select="@id"/>

        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes"/>
        </xsl:variable>
        <script type="text/javascript">dojo.require("betterform.ui.container.Group");</script><xsl:text>
</xsl:text>

        <table cellspacing="0" cellpadding="0" class="xfContainer caVerticalTable {$mip-classes}" id="{$group-id}" dojoType="betterform.ui.container.Group">
            <xsl:if test="exists(xforms:label)">
                <caption class="xfGroupLabel"><xsl:apply-templates select="./xforms:label"/></caption>
            </xsl:if>
            <tbody>
            <xsl:for-each select="*[not(local-name()='label')]">
                <xsl:choose>
                    <!-- if we got a group with appearance bf:horizontalColumn we put the label
                     of the first control into the lefthand column -->
                    <xsl:when test="local-name()='group' and ./@appearance='bf:horizontalColumn'">
                        <tr>
                            <td class="caLabelColumn">
                               <!-- use the label of the nested group for the left column -->
                                <xsl:value-of select="xforms:label"/>
                            </td>
                            <td>
                                <xsl:apply-templates select="."/>
                            </td>
                        </tr>
                    </xsl:when>
                    <xsl:when test="local-name()='group' or local-name()='repeat' or local-name()='switch'">
                        <tr>
                            <td colspan="3">
                                <xsl:apply-templates select="."/>
                            </td>
                        </tr>
                    </xsl:when>
                    <xsl:when test="namespace-uri()='http://www.w3.org/1999/xhtml'">
                        <tr>
                            <td colspan="3">
                                <xsl:apply-templates select="."/>
                            </td>
                        </tr>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:if test="exists(node())">
                        <tr>
                            <td class="caVerticalTableLabel" valign="top">
                                <xsl:variable name="label-classes">
                                    <xsl:call-template name="assemble-label-classes"/>
                                </xsl:variable>
                                <xsl:if test="local-name(.) != 'trigger'">
                                    <label id="{@id}-label" for="{@id}-value" class="{$label-classes}">
                                        <xsl:apply-templates select="xforms:label"/>
                                    </label>                                    
                                </xsl:if>
                            </td>
                            <td class="caVerticalTableValue" valign="top">
                                <xsl:apply-templates select="." mode="table"/>
                            </td>
                            <td class="bfVerticalTableInfo" valign="top">
                                <xsl:apply-templates select="xforms:alert"/>
                                <xsl:apply-templates select="xforms:hint"/>
                                <xsl:apply-templates select="xforms:help"/>
                                <span class="info" style="display:none;" id="{concat(@id,'-info')}">ok</span>
                            </td>
                        </tr>
                        </xsl:if>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:for-each>
            </tbody>
        </table>
    </xsl:template>


    <xsl:template match="xforms:trigger" mode="table">
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:call-template name="trigger">
            <xsl:with-param name="classes" select="$control-classes"/>
        </xsl:call-template>
    </xsl:template>


    <xsl:template match="xforms:input|xforms:output|xforms:range|xforms:secret|xforms:select|xforms:select1|xforms:textarea|xforms:upload" mode="table">
            <xsl:variable name="id" select="@id"/>
            <xsl:variable name="control-classes">
                <xsl:call-template name="assemble-control-classes">
                    <xsl:with-param name="appearance" select="@appearance"/>
                </xsl:call-template>
            </xsl:variable>

            <div id="{$id}"
                 dojoType="betterform.ui.Control"
                 class="{$control-classes}">
                <xsl:if test="@style">
                    <xsl:attribute name="style">
                        <xsl:value-of select="@style"/>
                    </xsl:attribute>
                </xsl:if>
                <xsl:call-template name="buildControl"/>
                <xsl:copy-of select="xhtml:script"/>
            </div>
    </xsl:template>
    <!--<xsl:template match="bf:data" mode="table" priority="10"/>-->

    <!-- ######################################################################################################## -->
    <!-- ####################################### custom group with horizontal layout ############################## -->
    <!-- ######################################################################################################## -->


    <!-- appearance horizontalColumn allows to be nested into a verticalTable and display its labels in the left
    column of the vertical table. All other controls will be wrapped in a horizontal group and be written to the
    right column. -->
    <xsl:template match="xforms:group[@appearance='bf:horizontalColumn']" priority="15">
        <xsl:call-template name="horizontalTable"/>
    </xsl:template>

    <!-- this template is used for horizontalTable AND for horizontalColumn appearance -->
    <xsl:template match="xforms:group[@appearance='bf:horizontalTable']" priority="15" name="horizontalTable">
<!--
        <xsl:if test="exists(xforms:group)">
            <xsl:message terminate="yes">ERROR: This custom group appearance may not have child groups</xsl:message>
        </xsl:if>
-->
        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes"/>
        </xsl:variable>
        <script type="text/javascript">dojo.require("betterform.ui.container.Group");</script><xsl:text>
</xsl:text>
        

        <xsl:variable name="realAppearance" select="if(@appearance='bf:horizontalTable') then 'caHorizontalTable' else 'caHorizontalColumn'"/>
        <table class="xfContainer {$realAppearance} {$mip-classes}" dojoType="betterform.ui.container.Group">
            <tr>
                <!--<td colspan="{count(*[position() &gt; 1 and position() &lt; last()])}" class="caHorizontalTableHeader">-->
                <!--<td colspan="{count(*[position() &gt; 1]) -1}" class="xfGroupLabel">-->
                <td colspan="{count(*[position() &gt; 1])}" class="xfGroupLabel">
                    <xsl:if test="exists(xforms:label) and @appearance !='bf:horizontalColumn'">
                        <xsl:apply-templates select="./xforms:label"/>
                    </xsl:if>
                </td>
            </tr>
            <tr>
                <!--<xsl:for-each select="*[position() &gt; 1 and position() &lt; last()]/xforms:label">-->
                <!--<xsl:for-each select="*[position() &gt; 1]/xforms:label">-->
                <xsl:for-each select="*[position() &gt; 1]">
                <!--<xsl:for-each select="*[position() &gt; 1]">-->
                    <td class="caHorizontalTableLabel  caTableCol{position()}">                        
                        <xsl:if test="local-name(.) != 'trigger'">
                            <label id="{@id}-label" for="{@id}-value" class="xfTableLable"><xsl:apply-templates select="xforms:label"/></label>
                        </xsl:if>
                    </td>
                </xsl:for-each>
            </tr>
            <tr>
                <!--<xsl:for-each select="*[position() &gt; 1 and position() &lt; last()]">-->
                <xsl:for-each select="*[position() &gt; 1 and *[position() != last()]]">
                    <td class="caHorizontalTableValue">
                        <xsl:apply-templates select="." />
                    </td>
                </xsl:for-each>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="bf:data" priority="10"/>
    <!-- ######################################################################################################## -->
    <!-- ####################################### REPEAT ######################################################### -->
    <!-- ######################################################################################################## -->
    <!-- ### COMPACT REPEAT ### -->
    <xsl:template match="xforms:repeat[@appearance='compact']" name="compact-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'compact'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:if test="not(ancestor::xforms:repeat)">
            <!-- generate prototype(s) for scripted environment -->
            <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']">
                <xsl:call-template name="processCompactPrototype">
                    <xsl:with-param name="id" select="$repeat-id"/>
                </xsl:call-template>
            </xsl:for-each>
            <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']//xforms:repeat">
                <xsl:call-template name="processRepeatPrototype"/>
            </xsl:for-each>
            <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']//xforms:itemset">
                <xsl:call-template name="processItemsetPrototype"/>
            </xsl:for-each>
        </xsl:if>
              <script type="text/javascript">
                  dojo.require("betterform.ui.container.Repeat");
                  dojo.require("betterform.ui.container.RepeatItem");</script><xsl:text>
</xsl:text>

        <table repeatId="{$repeat-id}"
               jsId="{$repeat-id}"
               class="{$repeat-classes}"
               dojoType="betterform.ui.container.Repeat"
               appearance="compact"
               border="0"
               cellpadding="0"
               cellspacing="0"
               >
            <!-- build table header -->
            <!-- <xsl:for-each select="bf:data/xforms:group[@appearance='repeated'][1]"> -->
            <!-- Don´t use Prototype for RepeatHeader but first Repeatitem -->
            <xsl:for-each select="xforms:group[@appearance='repeated'][1]">
                <tr class="xfRepeatHeader">
                    <xsl:call-template name="processCompactHeader"/>
                </tr>
            </xsl:for-each>

            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="id" select="@id"/>
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <tr repeatItemId="{$id}"
                    class="{$repeat-item-classes}"
                    dojoType="betterform.ui.container.RepeatItem"
                    appearance="compact">
                    <xsl:call-template name="processCompactChildren"/>
                </tr>
            </xsl:for-each>
        </table>
    </xsl:template>

    <!-- header for compact repeat -->
    <xsl:template name="processCompactHeader">
        <xsl:for-each select="xforms:*">
            <xsl:variable name="col-classes">
                <xsl:choose>
                    <xsl:when test="./bf:data/@bf:enabled='false'"><xsl:value-of select="concat('caTableCol-',position(),' ','xfDisabled')"/></xsl:when>
                    <xsl:otherwise><xsl:value-of select="concat('caTableCol-',position())"/></xsl:otherwise>
                </xsl:choose>
            </xsl:variable>
            <td class="{$col-classes}">
                <xsl:choose>
                    <xsl:when test="self::xforms:*[local-name(.)='trigger' or local-name(.)='submit' or (local-name(.)='output' and @appearance='caLink')][xforms:label]">
                        <xsl:variable name="label-classes">
                            <xsl:call-template name="assemble-label-classes"/>
                        </xsl:variable>
                        <label id="{@id}-label-header" class="{$label-classes}">
                            <xsl:value-of select="xforms:label/@header"/>
                        </label>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:variable name="label-classes">
                            <xsl:call-template name="assemble-label-classes"/>
                        </xsl:variable>

                        <label id="{@id}-label" class="{$label-classes}">
                            <xsl:apply-templates select="xforms:label"/>
                        </label>
                    </xsl:otherwise>
                </xsl:choose>
            </td>
        </xsl:for-each>
    </xsl:template>

    <!-- prototype for compact repeat -->
    <xsl:template name="processCompactPrototype">
        <xsl:param name="id"/>

        <table style="display:none;">
            <tr class="xfRepeatHeader">
                <!-- build table header -->
                <!-- <xsl:for-each select="bf:data/xforms:group[@appearance='repeated'][1]"> -->
                <!-- Don´t use Prototype for RepeatHeader but first Repeatitem -->
                <xsl:for-each select="xforms:group[@appearance='repeated'][1]">
                    <xsl:call-template name="processCompactHeader" />
                </xsl:for-each>
            </tr>
            <tr id="{$id}-prototype" class="xfRepeatPrototype xfDisabled xfReadWrite xfOptional xfValid">
                <xsl:for-each select="xforms:*">
                    <xsl:variable name="col-classes">
                        <xsl:choose>
                            <xsl:when test="./bf:data/@bf:enabled='false'">
                                <xsl:value-of select="concat('caTableCol-',position(),' ','xfDisabled')"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="concat('caTableCol-',position())"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <td valign="top" class="{$col-classes}">
                        <xsl:apply-templates select="." mode="repeated-compact-prototype"/>
                    </td>
                </xsl:for-each>
            </tr>
        </table>
    </xsl:template>

    <!-- overridden control template for compact repeat -->
    <xsl:template match="xforms:input|xforms:output|xforms:range|xforms:secret|xforms:select|xforms:select1|xforms:textarea|xforms:upload" mode="repeated-compact-prototype" priority="10">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>

        <xsl:variable name="htmlElem">
            <xsl:choose>
                <xsl:when test="local-name()='output'">span</xsl:when>
                <xsl:otherwise>div</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <xsl:element name="{$htmlElem}">
            <xsl:attribute name="id" select="$id"/>
            <xsl:attribute name="class" select="concat($control-classes,' xfRepeated')"/>
            <xsl:attribute name="controlType" select="local-name()"/>
            <xsl:attribute name="appearance" select="@appearance"/>
            <xsl:attribute name="dojoAttachEvent">onfocus:_onFocus</xsl:attribute>

            <xsl:choose>
                <xsl:when test="'output' = local-name() and exists(@mediatype)">
                        <xsl:attribute name="mediatype" select="@mediatype"/>
                </xsl:when>
                <xsl:when test="'select' = local-name()">
                        <xsl:call-template name="select"/>
                </xsl:when>
                <xsl:when test="'select1' = local-name()">
                            <xsl:call-template name="select1"/>
                </xsl:when>
            </xsl:choose>
            <xsl:apply-templates select="xforms:alert"/>
            <xsl:apply-templates select="xforms:hint"/>
            <xsl:apply-templates select="xforms:help"/>

        </xsl:element>

	</xsl:template>

    <xsl:template match="xforms:group" mode="repeated-compact-prototype" priority="10">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:variable name="appearance" select="@appearance"/>

        <xsl:variable name="htmlElem">
            <xsl:choose>
                <xsl:when test="$appearance='minimal'">span</xsl:when>
                <xsl:otherwise>div</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>


        <xsl:element name="{$htmlElem}">
            <xsl:attribute name="id" select="$id"/>
            <xsl:attribute name="class" select="concat($control-classes,' xfRepeated')"/>
            <xsl:attribute name="controlType" select="local-name()"/>
            <xsl:attribute name="appearance" select="$appearance"/>
            <xsl:attribute name="dojoAttachEvent">onfocus:_onFocus</xsl:attribute>
            <!-- prevent xforms:label for groups within compact repeat-->
            <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-compact-prototype"/>
        </xsl:element>

    </xsl:template>

    <xsl:template match="xforms:switch" mode="repeated-compact-prototype" priority="10">
            <xsl:variable name="switch-id" select="@id"/>
            <xsl:variable name="switch-classes">
                <xsl:call-template name="assemble-compound-classes">
                    <xsl:with-param name="appearance" select="@appearance"/>
                </xsl:call-template>
            </xsl:variable>
            <div id="{$switch-id}" class="{$switch-classes}" dojoType="betterform.ui.container.Switch">
                <xsl:apply-templates mode="repeated-compact-prototype"/>
            </div>
    </xsl:template>


    <xsl:template match="xforms:case[bf:data/@bf:selected='true']" mode="repeated-compact-prototype" priority="10">
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'xfCase xfSelectedCase'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-compact-prototype"/>
        </div>
    </xsl:template>

    <!-- ### DE-SELECTED/NON-SELECTED CASE ### -->
    <xsl:template match="xforms:case" mode="repeated-compact-prototype" priority="10">
        <!-- render only in scripted environment -->
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'xfCase xfDeselectedCase'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-compact-prototype"/>
        </div>
    </xsl:template>




    <xsl:template match="xforms:repeat" mode="repeated-compact-prototype" priority="10">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <table id="{$id}" class="{$control-classes} xfRepeated" controlType="{local-name()}" appearance="{@appearance}" dojoAttachEvent='onfocus:_onFocus' repeatId="{$id}">
            <tr class="xfRepeatHeader">
                <xsl:call-template name="processCompactHeader" />
            </tr>
        </table>
    </xsl:template>

    
    <xsl:template match="xforms:trigger" mode="repeated-compact-prototype" priority="10">
            <xsl:variable name="id" select="@id"/>
            <xsl:variable name="appearance">
                <xsl:choose>
                <xsl:when test="string-length(@appearance) &gt;0"><xsl:value-of select="@appearance"/></xsl:when>
                <xsl:otherwise>full</xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <xsl:variable name="control-classes">
                <xsl:call-template name="assemble-control-classes"/>
            </xsl:variable>
            <xsl:choose>
                <xsl:when test="exists(@ref) or exists(@bind)">
                    <div id="{$id}" class="{$control-classes} xfRepeated"  controlType="{local-name()}" appearance="{$appearance}" ><xsl:value-of select="xforms:label"/></div>
                </xsl:when>
                <xsl:otherwise>
                    <div id="{$id}" class="{$control-classes} xfRepeated" unbound="true">
                          <div id="{$id}-value" class="xfValue" appearance="{$appearance}" controlType="trigger" label="{xforms:label}" name="d_{$id}"  title="" navindex="" accesskey="" source=""> </div>
                    </div>
                </xsl:otherwise>
            </xsl:choose>
    </xsl:template>

    <xsl:template match="xforms:input|xforms:output|xforms:range|xforms:secret|xforms:select|xforms:select1|xforms:textarea|xforms:upload"
                  mode="repeated-full-prototype"
                  priority="20">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>

        <xsl:variable name="htmlElem">
            <xsl:choose>
                <xsl:when test="local-name()='output'">span</xsl:when>
                <xsl:otherwise>div</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:element name="{$htmlElem}">
            <xsl:attribute name="id" select="$id"/>
            <xsl:attribute name="class" select="concat($control-classes,' xfRepeated')"/>
            <xsl:attribute name="controlType" select="local-name()"/>
            <xsl:attribute name="appearance" select="@appearance"/>
            <xsl:attribute name="title" select="normalize-space(xforms:hint)"/>
            <xsl:attribute name="dojoAttachEvent">onfocus:_onFocus</xsl:attribute>

            <xsl:if test="'output' = local-name() and exists(@mediatype)"><xsl:attribute name="mediatype" select="@mediatype"/></xsl:if>
            <label class="xfLabel"><xsl:apply-templates select="xforms:label"/></label>

            <!--<xsl:apply-templates select="xforms:alert"/>-->
            <xsl:choose>
                <xsl:when test="'select' = local-name()">
                        <xsl:call-template name="select"/>
                        <!--<xsl:apply-templates select="xforms:alert"/>-->
                </xsl:when>
                <xsl:when test="'select1' = local-name()">
                            <xsl:call-template name="select1"/>
                            <!--<xsl:apply-templates select="xforms:alert"/>-->
                </xsl:when>
            </xsl:choose>

            <xsl:apply-templates select="xforms:alert"/>
            <xsl:apply-templates select="xforms:hint"/>
            <xsl:apply-templates select="xforms:help"/>

        </xsl:element>
    </xsl:template>

    <xsl:template match="xforms:group"
                  mode="repeated-full-prototype"
                  priority="10">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="appearance" select="@appearance"/>

        <xsl:variable name="htmlElem">
            <xsl:choose>
                <xsl:when test="$appearance='minimal'">span</xsl:when>
                <xsl:otherwise>div</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="group-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:element name="{$htmlElem}">
             <xsl:attribute name="id" select="$id"/>
             <xsl:attribute name="class" select="concat($group-classes,' xfRepeated dijitContentPane')"/>
             <xsl:attribute name="controlType" select="local-name()"/>
             <xsl:attribute name="appearance" select="$appearance"/>
             <xsl:attribute name="dojoAttachEvent">onfocus:_onFocus</xsl:attribute>

             <xsl:element name="{$htmlElem}">
	             <xsl:attribute name="class">xfGroupLabel</xsl:attribute>
                 <xsl:apply-templates select="xforms:label"/>
             </xsl:element>

             <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-full-prototype"/>
         </xsl:element>


    </xsl:template>

    <xsl:template match="xforms:switch"
                  mode="repeated-full-prototype"
                  priority="10">
            <xsl:variable name="switch-id" select="@id"/>
            <xsl:variable name="switch-classes">
                <xsl:call-template name="assemble-compound-classes">
                    <xsl:with-param name="appearance" select="@appearance"/>
                </xsl:call-template>
            </xsl:variable>
            <div id="{$switch-id}" class="{$switch-classes}" dojoType="betterform.ui.container.Switch">
                <xsl:apply-templates mode="repeated-full-prototype"/>
            </div>
    </xsl:template>


    <xsl:template match="xforms:case[bf:data/@bf:selected='true']"
                  mode="repeated-full-prototype"
                  priority="10">
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'xfCase xfSelectedCase'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-full-prototype"/>
        </div>
    </xsl:template>

    <!-- ### DE-SELECTED/NON-SELECTED CASE ### -->
    <xsl:template match="xforms:case"
                  mode="repeated-full-prototype"
                  priority="10">
        <!-- render only in scripted environment -->
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'xfCase xfDeselectedCase'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" mode="repeated-full-prototype"/>
        </div>
    </xsl:template>

    <xsl:template match="xforms:repeat"
                  mode="repeated-full-prototype"
                  priority="10">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>

        <div id="{$id}" class="{$control-classes} xfRepeated" controlType="{local-name()}" appearance="{@appearance}" dojoAttachEvent='onfocus:_onFocus' repeatId="{$id}">
        </div>
    </xsl:template>

    <xsl:template match="xforms:trigger"  mode="repeated-full-prototype" priority="10">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="appearance">
            <xsl:choose>
            <xsl:when test="string-length(@appearance) &gt;0"><xsl:value-of select="@appearance"/></xsl:when>
            <xsl:otherwise>full</xsl:otherwise>    
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="exists(@ref) or exists(@bind)">
                <div id="{$id}" class="{$control-classes} xfRepeated"  controlType="{local-name()}" appearance="{$appearance}" ><xsl:value-of select="xforms:label"/></div>
            </xsl:when>
            <xsl:otherwise>
                <div id="{$id}" class="{$control-classes} xfRepeated" unbound="true">
                      <div id="{$id}-value" class="xfValue" appearance="{$appearance}" controlType="trigger" label="{xforms:label}" name="d_{$id}"  title="" navindex="" accesskey="" source=""> </div>
                </div>                
            </xsl:otherwise>
        </xsl:choose>

    </xsl:template>

    <!-- copys non xform nodes into the full protoype -->
    <xsl:template match="xhtml:*"
                  mode="repeated-full-prototype"
                  priority="1">
        <xsl:copy><xsl:copy-of select="@*" /><xsl:apply-templates mode="repeated-full-prototype"/></xsl:copy>
    </xsl:template>

    
    <!-- children for compact repeat -->
    <xsl:template name="processCompactChildren">
        <xsl:for-each select="xforms:*">
            <xsl:variable name="col-classes">
                <xsl:choose>
                    <xsl:when test="./bf:data/@bf:enabled='false'"><xsl:value-of select="concat('caTableCol-',position(),' ','xfDisabled')"/></xsl:when>
                    <xsl:otherwise><xsl:value-of select="concat('caTableCol-',position())"/></xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <td valign="top" class="{$col-classes}">
                <xsl:apply-templates select="." mode="compact-repeat"/>
           </td>
        </xsl:for-each>
    </xsl:template>

    <!-- overridden control template for compact repeat -->
    <xsl:template match="xforms:input|xforms:output|xforms:range|xforms:secret|xforms:select|xforms:select1|xforms:textarea|xforms:upload" mode="compact-repeat">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>

        <div id="{$id}" class="{$control-classes} xfRepeated" dojoType="betterform.ui.Control"  dojoAttachEvent='onfocus:_onFocus' >
            <label for="{$id}-value" id="{$id}-label" style="display:none">
                <xsl:apply-templates select="xforms:label"/>
            </label>

            <xsl:call-template name="buildControl"/>
            <xsl:apply-templates select="xforms:alert"/>
            <xsl:apply-templates select="xforms:hint"/>
            <xsl:apply-templates select="xforms:help"/>


        </div>
    </xsl:template>

    <!-- overridden group template for compact repeat -->
    <xsl:template match="xforms:group" mode="compact-repeat">
        <xsl:variable name="group-id" select="@id"/>
        <xsl:variable name="group-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:call-template name="group-body">
            <xsl:with-param name="group-id" select="$group-id"/>
            <xsl:with-param name="group-classes" select="$group-classes"/>
            <xsl:with-param name="group-label" select="false()"/>
        </xsl:call-template>
    </xsl:template>

    <!-- default templates for compact repeat -->
    <xsl:template match="xforms:*" mode="compact-repeat">
        <xsl:apply-templates select="."/>
    </xsl:template>


    <!-- ### FULL REPEAT ### -->
    <xsl:template match="xforms:repeat[@appearance='full']" name="full-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'full'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:if test="not(ancestor::xforms:repeat)">
            <!-- generate prototype(s) for scripted environment -->
            <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']">
                <xsl:call-template name="processFullPrototype">
                    <xsl:with-param name="id" select="$repeat-id"/>
                </xsl:call-template>
            </xsl:for-each>
            <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']//xforms:repeat">
                <xsl:call-template name="processRepeatPrototype"/>
            </xsl:for-each>
            <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']//xforms:itemset">
                <xsl:call-template name="processItemsetPrototype"/>
            </xsl:for-each>
        </xsl:if><script type="text/javascript">
                  dojo.require("betterform.ui.container.Repeat");
                  dojo.require("betterform.ui.container.RepeatItem");</script><xsl:text>
</xsl:text>
        
        <div repeatId="{$repeat-id}" class="{$repeat-classes}" dojoType="betterform.ui.container.Repeat">
            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-id" select="@id"/>
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                    <xsl:variable name="group-label" select="true()"/>

                    <div repeatItemId="{$repeat-item-id}"
                         class="{$repeat-item-classes}"
                         dojoType="betterform.ui.container.RepeatItem"
                         appearance="full">
                        <div class="legend">
                            <xsl:choose>
                                <xsl:when test="$group-label and xforms:label">
                                    <xsl:attribute name="id">
                                        <xsl:value-of select="concat($repeat-item-id, '-label')"/>
                                    </xsl:attribute>
                                    <xsl:attribute name="class">
                                        <xsl:call-template name="assemble-group-label-classes"/>
                                    </xsl:attribute>
                                    <xsl:apply-templates select="xforms:label"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="style">display:none;</xsl:attribute>
                                </xsl:otherwise>
                            </xsl:choose>
                        </div>

                        <xsl:apply-templates select="*[not(self::xforms:label)]"/>
                    </div>

            </xsl:for-each>
        </div>
    </xsl:template>

    <!-- #### caObjectContainer #### -->
<!--
    <xsl:template match="xforms:repeat[@appearance='caRepeatedTab']">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'caObjectContainer'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']">
            <xsl:call-template name="processFullPrototype">
                <xsl:with-param name="id" select="$repeat-id"/>
            </xsl:call-template>
        </xsl:for-each>


        <div id="{$repeat-id}" repeatId="{$repeat-id}" class="{$repeat-classes}" dojoType="betterform.ui.container.RepeatTabContainer" doLayout="false">           ´

            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-id" select="@id"/>
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                    <xsl:variable name="group-label" select="true()"/>

                    <div repeatItemId="{$repeat-item-id}"
                         class="{$repeat-item-classes}"
                         title="{.//xforms:output[1]/bf:data}"
                         appearance="full">
                        <div class="legend">
                            <xsl:choose>
                                <xsl:when test="$group-label and xforms:label">
                                    <xsl:attribute name="id">
                                        <xsl:value-of select="concat($repeat-item-id, '-label')"/>
                                    </xsl:attribute>
                                    <xsl:attribute name="class">
                                        <xsl:call-template name="assemble-group-label-classes"/>
                                    </xsl:attribute>
                                    <xsl:apply-templates select="xforms:label"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="style">display:none;</xsl:attribute>
                                </xsl:otherwise>
                            </xsl:choose>
                        </div>

                        <xsl:apply-templates select="*[not(self::xforms:label)]"/>
                    </div>

            </xsl:for-each>
        </div>
    </xsl:template>
-->


    <!-- prototype for full repeat -->
    <xsl:template name="processFullPrototype">
        <xsl:param name="id"/>

        <xsl:call-template name="group-body-repeated">
            <xsl:with-param name="group-id" select="concat($id, '-prototype')"/>
            <xsl:with-param name="group-classes" select="'xfRepeatPrototype xfDisabled xfReadWrite xfOptional xfValid'"/>
        </xsl:call-template>
    </xsl:template>


    <!-- ### DEFAULT REPEAT ### -->
    <xsl:template match="xforms:repeat" name="repeat">
        <!-- full appearance as default -->
        <xsl:call-template name="full-repeat"/>
    </xsl:template>

    <!-- ### FOREIGN NAMESPACE REPEAT ### -->
    <xsl:template match="*[@xforms:repeat-bind]|*[@xforms:repeat-nodeset]|@repeat-bind|@repeat-nodeset" name="generic-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'full'"/>
            </xsl:call-template>
        </xsl:variable>

         <xsl:element name="{local-name(.)}" namespace="">
                <xsl:attribute name="repeatId"><xsl:value-of select="$repeat-id"/></xsl:attribute>
                <xsl:attribute name="jsId"><xsl:value-of select="@id"/></xsl:attribute>
                <xsl:attribute name="dojoType">betterform.ui.container.Repeat</xsl:attribute>
                <xsl:attribute name="class"><xsl:value-of select="$repeat-classes"/></xsl:attribute>
             <xsl:copy-of select="@*"/>

            <xsl:if test="not(ancestor::xforms:repeat)">
                <!-- generate prototype(s) for scripted environment -->
                <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']">
                    <xsl:call-template name="processTableRepeatPrototype">
                        <xsl:with-param name="id" select="$repeat-id"/>
                    </xsl:call-template>
                </xsl:for-each>
                <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']//xforms:repeat">
                    <xsl:call-template name="processRepeatPrototype"/>
                </xsl:for-each>
                <xsl:for-each select="bf:data/xforms:group[@appearance='repeated']//xforms:itemset">
                    <xsl:call-template name="processItemsetPrototype"/>
                </xsl:for-each>
            </xsl:if>

            <xsl:for-each select="xforms:group[@appearance='repeated']">
                    <xsl:variable name="id" select="@id"/>

                 <xsl:variable name="repeat-item-classes">
                     <xsl:call-template name="assemble-repeat-item-classes">
                         <xsl:with-param name="selected" select="$repeat-index=position()"/>
                     </xsl:call-template>
                 </xsl:variable>

                    <xsl:for-each select="xhtml:tr">

                        <tr repeatItemId="{$id}"
                            class="{$repeat-item-classes}"
                            dojoType="betterform.ui.container.RepeatItem"
                            appearance="compact">
                            <xsl:apply-templates select="*" mode="compact-repeat"/>
                        </tr>
                 </xsl:for-each>
             </xsl:for-each>
         </xsl:element>
    </xsl:template>

    <xsl:template name="processTableRepeatPrototype">
        <xsl:param name="id"/>
        <xsl:variable name="col-classes">
            <xsl:choose>
                <xsl:when test="./bf:data/@bf:enabled='false'">
                    <xsl:value-of select="concat('caTableCol-',position(),' ','xfDisabled')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="concat('caTableCol-',position())"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:for-each select="*">
            <xsl:element name="{local-name(.)}" namespace="">
                <xsl:attribute name="id"><xsl:value-of select="$id"/>-prototype</xsl:attribute>
                <xsl:attribute name="class">xfRepeatPrototype xfDisabled xfReadWrite xfOptional xfValid <xsl:value-of select="$col-classes"/></xsl:attribute>
                <xsl:apply-templates select="*" mode="repeated-compact-prototype"/>
            </xsl:element>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="*|@*|text()|comment()" mode="repeated-compact-prototype">
        <xsl:choose>
            <xsl:when test="namespace-uri(.)='http://www.w3.org/1999/xhtml'">
                <xsl:element name="{local-name(.)}" namespace="">
                    <xsl:apply-templates select="*|@*|text()|comment()" mode="repeated-compact-prototype"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="*|@*|text()|comment()" mode="repeated-compact-prototype"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

     <xsl:template match="*|@*|text()|comment()" mode="compact-repeat">
        <xsl:choose>
            <xsl:when test="namespace-uri(.)='http://www.w3.org/1999/xhtml'">
                <xsl:element name="{local-name(.)}" namespace="">
                    <xsl:apply-templates select="*|@*|text()|comment()" mode="compact-repeat"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="*|@*|text()|comment()" mode="compact-repeat"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- repeat prototype helper -->
    <xsl:template name="processRepeatPrototype">
        <xsl:variable name="id" select="@id"/>

        <xsl:choose>
            <xsl:when test="@appearance='compact'">
                <xsl:call-template name="processCompactPrototype">
                    <xsl:with-param name="id" select="$id"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="processFullPrototype">
                    <xsl:with-param name="id" select="$id"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- itemset prototype helper -->
    <xsl:template name="processItemsetPrototype">
        <xsl:variable name="item-id" select="$betterform-pseudo-item"/>
        <xsl:variable name="itemset-id" select="@id"/>
        <xsl:variable name="name" select="concat($data-prefix,../@id)"/>
        <xsl:variable name="parent" select=".."/>

        <xsl:choose>
            <xsl:when test="local-name($parent)='select1' and $parent/@appearance='full'">
                <xsl:call-template name="build-radiobutton-prototype">
                    <xsl:with-param name="item-id" select="$item-id"/>
                    <xsl:with-param name="itemset-id" select="$itemset-id"/>
                    <xsl:with-param name="name" select="$name"/>
                    <xsl:with-param name="parent" select="$parent"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="local-name($parent)='select' and $parent/@appearance='full'">
                <xsl:call-template name="build-checkbox-prototype">
                    <xsl:with-param name="item-id" select="$item-id"/>
                    <xsl:with-param name="itemset-id" select="$itemset-id"/>
                    <xsl:with-param name="name" select="$name"/>
                    <xsl:with-param name="parent" select="$parent"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="build-item-prototype">
                    <xsl:with-param name="item-id" select="$item-id"/>
                    <xsl:with-param name="itemset-id" select="$itemset-id"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>


    <!-- ######################################################################################################## -->
    <!-- ####################################### SWITCH ######################################################### -->
    <!-- ######################################################################################################## -->

    <!-- ### FULL SWITCH ### -->
    <!--
        Renders a tabsheet. This template requires that the author sticks to an
        authoring convention: The triggers for toggling the different cases MUST
        all appear in a case with id 'switch-toggles'. This convention makes it
        easier to maintain the switch cause all relevant markup is kept under the
        same root element.
    -->

    <!-- ### DEFAULT SWITCH ### -->
    <xsl:template match="xforms:switch">
        <xsl:variable name="switch-id" select="@id"/>
        <xsl:variable name="switch-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable><script type="text/javascript">dojo.require("betterform.ui.container.Switch");</script><xsl:text>
</xsl:text>

        <div id="{$switch-id}" class="{$switch-classes}" dojoType="betterform.ui.container.Switch">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <!-- ### SELECTED CASE ### -->
    <xsl:template match="xforms:case[bf:data/@bf:selected='true']" name="selected-case">
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'xfCase xfSelectedCase'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" />
        </div>
    </xsl:template>

    <!-- ### DE-SELECTED/NON-SELECTED CASE ### -->
    <xsl:template match="xforms:case" name="deselected-case">
        <!-- render only in scripted environment -->
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'xfCase xfDeselectedCase'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" />
        </div>
    </xsl:template>


	<xsl:template match="xforms:switch[@appearance='dijit:AccordionContainer']">
		<xsl:variable name="switch-id" select="@id"/>
		<xsl:variable name="switch-classes">
		    <xsl:call-template name="assemble-compound-classes">
		        <xsl:with-param name="appearance" select="@appearance"/>
		    </xsl:call-template>
		</xsl:variable><script type="text/javascript">dojo.require("dijit.layout.AccordionContainer");</script><xsl:text>
</xsl:text>

		<div id="{$switch-id}" class="{$switch-classes}" dojoType="dijit.layout.AccordionContainer" duration="200"
                style="float: left; margin-right: 30px; width: 400px; height: 300px; overflow: hidden">
			<xsl:for-each select="xforms:case[.//xforms:label]">
				<div dojoType="dijit.layout.AccordionPane" selected="{@selected}" title="{.//xforms:label[1]}">
                    <xsl:apply-templates select="*[not(self::xforms:label)]"/>
                </div>
			</xsl:for-each>
		</div>
	</xsl:template>

	<xsl:template match="xforms:switch[@appearance='bf:AccordionContainer']">
		<xsl:variable name="switch-id" select="@id"/>
		<xsl:variable name="switch-classes">
		    <xsl:call-template name="assemble-compound-classes">
		        <xsl:with-param name="appearance" select="@appearance"/>
		    </xsl:call-template>
		</xsl:variable>
        <div style="display:none">
            <xsl:for-each select="xforms:case[@name='switch-toggles']/xforms:trigger">
                <xsl:call-template name="trigger"/>
            </xsl:for-each>
        </div>
        <script type="text/javascript">dojo.require("betterform.ui.container.AccordionSwitch");</script><xsl:text>
</xsl:text>
        <div id="{$switch-id}" class="{$switch-classes} xfAccordion" dojoType="betterform.ui.container.AccordionSwitch" duration="200" >
<!--
		<div id="{$switch-id}" class="{$switch-classes}" dojoType="betterform.ui.container.TabSwitch"
                style="width: 900px; height: 400px;">
-->
			<xsl:for-each select="xforms:case[./xforms:label]">
                <xsl:variable name="selected">
                    <xsl:choose>
                        <xsl:when test="@selected='true'">true</xsl:when>
                        <xsl:otherwise>false</xsl:otherwise>
                    </xsl:choose>
                </xsl:variable><script>dojo.require("betterform.ui.container.AccordionSwitchPane");</script><xsl:text>
</xsl:text>
                <div dojoType="betterform.ui.container.AccordionSwitchPane" class="xfCase" caseId="{@id}" selected="{$selected}" title="{xforms:label}">
                    <xsl:apply-templates select="*[not(self::xforms:label)]"/>
                </div>
			</xsl:for-each>
		</div>
	</xsl:template>

    <xsl:template match="xforms:switch[@appearance='dijit:TabContainer']">
		<xsl:variable name="switch-id" select="@id"/>
		<xsl:variable name="switch-classes">
		    <xsl:call-template name="assemble-compound-classes">
		        <xsl:with-param name="appearance" select="@appearance"/>
		    </xsl:call-template>
		</xsl:variable>
        <script type="text/javascript">dojo.require("betterform.ui.container.TabSwitch");</script><xsl:text>
</xsl:text>
        <div style="display:none">
            <xsl:for-each select="xforms:case[@name='switch-toggles']/xforms:trigger">
                <xsl:call-template name="trigger"/>
            </xsl:for-each>
        </div>
		<div id="{$switch-id}" class="{$switch-classes} caTabContainer" dojoType="betterform.ui.container.TabSwitch">
			<xsl:for-each select="xforms:case[./xforms:label]">
                <xsl:variable name="selected">
                    <xsl:choose>
                        <xsl:when test="@selected='true'">true</xsl:when>
                        <xsl:otherwise>false</xsl:otherwise>
                    </xsl:choose>
                </xsl:variable><script type="text/javascript">dojo.require("dijit.layout.ContentPane");</script><xsl:text>
</xsl:text>
                <!--<div dojoType="dijit.layout.ContentPane" style="width:100%;height:100%;" class="xfCase" caseId="{@id}" selected="{$selected}" title="{xforms:label}" onscroll="betterform.ui.util.closeSelect1(this);">-->
                <div dojoType="dijit.layout.ContentPane" style="width:100%;height:100%;" class="xfCase" caseId="{@id}" selected="{$selected}" title="{xforms:label}">
                    <xsl:apply-templates select="*[not(self::xforms:label)]"/>
                </div>
			</xsl:for-each>
		</div>
	</xsl:template>

	<xsl:template match="xforms:switch[@appearance='dijit:TitlePane']">
		<xsl:variable name="switch-id" select="@id"/>
		<xsl:variable name="switch-classes">
		    <xsl:call-template name="assemble-compound-classes">
		        <xsl:with-param name="appearance" select="@appearance"/>
		    </xsl:call-template>
		</xsl:variable><script type="text/javascript">dojo.require("betterform.ui.container.TitlePaneGroup");</script><xsl:text>
</xsl:text>

		<div id="{$switch-id}" class="{$switch-classes}"
                style="width: 600px; height: 300px;">
            <xsl:for-each select="xforms:case[.//xforms:label]">
                <div dojoType="betterform.ui.container.TitlePaneGroup" title="{.//xforms:label[1]}">
                    <xsl:apply-templates select="*[not(self::xforms:label)]"/>
                </div>
            </xsl:for-each>
        </div>
	</xsl:template>

</xsl:stylesheet>

