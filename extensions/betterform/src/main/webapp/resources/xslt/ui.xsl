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
    <!-- ####################################################################################################### -->

    <xsl:param name="betterform-pseudo-item" select="'betterform-pseudo-item'"/>
    <!-- ############################################ PARAMS ################################################### -->
    <!-- ##### should be declared in html4.xsl ###### -->
    <!-- ############################################ VARIABLES ################################################ -->


    <xsl:output method="html" indent="yes"/>

    <xsl:preserve-space elements="*"/>

    <!-- ####################################################################################################### -->
    <!-- #################################### GROUPS ########################################################### -->
    <!-- ####################################################################################################### -->

    <!-- ### DEFAULT GROUP - this is used if no @apprearance has been defined ### -->
    <xsl:template match="xforms:group">
        <xsl:variable name="group-id" select="@id"/>
        <xsl:variable name="group-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:call-template name="group-body-div">
            <xsl:with-param name="group-id" select="$group-id"/>
            <xsl:with-param name="group-classes" select="$group-classes"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="group-body-div">
        <xsl:param name="group-id"/>
        <xsl:param name="group-classes"/>
        <xsl:param name="group-label" select="true()"/>

        <div id="{$group-id}" class="{$group-classes}">
            <div>
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

            <xsl:apply-templates select="*[not(self::xforms:label)]"/>
        </div>
    </xsl:template>

    <!-- this template is used for all groups with an appearance -->
    <xsl:template match="xforms:group[@appearance]" name="group">
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

        <div id="{$group-id}" class="{$group-classes}">
            <div class="group-label">
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

            <xsl:apply-templates select="*[not(self::xforms:label)]"/>
        </div>
    </xsl:template>


    <!-- ######################################################################################################## -->
    <!-- ####################################### REPEAT ######################################################### -->
    <!-- ######################################################################################################## -->

	<!-- ### MINIMAL REPEAT ### -->
    <xsl:template match="xforms:repeat[@appearance='minimal']" name="minimal-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'minimal'"/>
            </xsl:call-template>
        </xsl:variable>

        <div id="{$repeat-id}" class="{$repeat-classes}">
            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <div id="{@id}" class="{$repeat-item-classes}">
                    <div class="repeat-selector">
                        <xsl:variable name="outermost-id" select="ancestor-or-self::xforms:repeat/@id"/>
                        <input type="radio" name="{$selector-prefix}{$outermost-id}" value="{$repeat-id}:{position()}">
                            <xsl:if test="string($outermost-id)=string($repeat-id) and string($repeat-index)=string(position())">
                                <xsl:attribute name="checked">checked</xsl:attribute>
                            </xsl:if>
                        </input>
                    </div>
                    <xsl:apply-templates/>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template>

    <!-- prototype for minimal repeat -->
    <xsl:template name="processMinimalPrototype">
        <xsl:param name="id"/>

        <div id="{$id}-prototype" class="repeat-prototype disabled readwrite optional valid" style="display:none;">
            <xsl:apply-templates/>
        </div>
    </xsl:template>


    <!-- ### COMPACT REPEAT ### -->
    <xsl:template match="xforms:repeat[@appearance='compact']" name="compact-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'compact'"/>
            </xsl:call-template>
        </xsl:variable>

        <table id="{$repeat-id}" class="{$repeat-classes}">
            <!-- build table header -->
            <xsl:for-each select="xforms:group[@appearance='repeated'][1]">
                <tr class="repeat-header">
                    <td class="repeat-selector"><xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text></td>
                    <xsl:call-template name="processCompactHeader"/>
                </tr>
            </xsl:for-each>

            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <tr id="{@id}" class="{$repeat-item-classes}">
                    <td class="repeat-selector">
                        <xsl:variable name="outermost-id" select="ancestor-or-self::xforms:repeat/@id"/>
                        <input type="radio" name="{$selector-prefix}{$outermost-id}" value="{$repeat-id}:{position()}">
                            <xsl:if test="string($outermost-id)=string($repeat-id) and string($repeat-index)=string(position())">
                                <xsl:attribute name="checked">checked</xsl:attribute>
                            </xsl:if>
                        </input>
                    </td>
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
                    <xsl:when test="./bf:data/@bf:enabled='false'"><xsl:value-of select="concat('col-',position(),' ','disabled')"/></xsl:when>
                    <xsl:otherwise><xsl:value-of select="concat('col-',position())"/></xsl:otherwise>
                </xsl:choose>
            </xsl:variable>
            <td class="{$col-classes}">
                <xsl:choose>
                    <xsl:when test="self::xforms:*[local-name(.)!='trigger' and local-name(.)!='submit'][xforms:label]">
                        <xsl:variable name="label-classes">
                            <xsl:call-template name="assemble-label-classes"/>
                        </xsl:variable>

                        <label id="{@id}-label" class="{$label-classes}">
                            <xsl:apply-templates select="xforms:label"/>
                        </label>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text>
                    </xsl:otherwise>
                </xsl:choose>
            </td>
        </xsl:for-each>
    </xsl:template>

    <!-- prototype for compact repeat -->
    <xsl:template name="processCompactPrototype">
        <xsl:param name="id"/>

        <table style="display:none;">
            <tr id="{$id}-prototype" class="repeat-prototype disabled readwrite optional valid">
                <xsl:call-template name="processCompactChildren"/>
            </tr>
        </table>
    </xsl:template>

    <!-- children for compact repeat -->
    <xsl:template name="processCompactChildren">
        <xsl:for-each select="xforms:*">
            <xsl:variable name="col-classes">
                <xsl:choose>
                    <xsl:when test="./bf:data/@bf:enabled='false'"><xsl:value-of select="concat('col-',position(),' ','disabled')"/></xsl:when>
                    <xsl:otherwise><xsl:value-of select="concat('col-',position())"/></xsl:otherwise>
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

        <div id="{$id}" class="{$control-classes}">
            <xsl:call-template name="buildControl"/>
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

        <div id="{$repeat-id}" class="{$repeat-classes}">
            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-id" select="@id"/>
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <xsl:call-template name="group-body">
                    <xsl:with-param name="group-id" select="$repeat-item-id"/>
                    <xsl:with-param name="group-classes" select="$repeat-item-classes"/>
                </xsl:call-template>
            </xsl:for-each>
        </div>
    </xsl:template>

    <!-- prototype for full repeat -->
    <xsl:template name="processFullPrototype">
        <xsl:param name="id"/>

        <xsl:call-template name="group-body">
            <xsl:with-param name="group-id" select="concat($id, '-prototype')"/>
            <xsl:with-param name="group-classes" select="'repeat-prototype disabled readwrite optional valid'"/>
        </xsl:call-template>
    </xsl:template>


    <!-- ### DEFAULT REPEAT ### -->
    <xsl:template match="xforms:repeat" name="repeat">
        <!-- compact appearance as default -->
        <xsl:call-template name="compact-repeat"/>
    </xsl:template>

    <!-- ### FOREIGN NAMESPACE REPEAT ### -->
<!--
    <xsl:template match="*[@xforms:repeat-bind]|*[@xforms:repeat-nodeset]|@repeat-bind|@repeat-nodeset">
        <xsl:variable name="repeat-index" select="bf:data/@bf:index"/>

        <xsl:copy>
            <xsl:copy-of select="@*"/>

            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-id" select="@id"/>
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <xsl:for-each select="*">
                    <xsl:apply-templates select="."/>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>
-->

    <!-- repeat prototype helper -->
    <xsl:template name="processRepeatPrototype">
        <xsl:variable name="id" select="@id"/>

        <xsl:choose>
            <xsl:when test="@appearance='full'">
                <xsl:call-template name="processFullPrototype">
                    <xsl:with-param name="id" select="$id"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="@appearance='compact'">
                <xsl:call-template name="processCompactPrototype">
                    <xsl:with-param name="id" select="$id"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="processMinimalPrototype">
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
    <xsl:template match="xforms:switch[@appearance='full']" name="full-switch">
        <xsl:variable name="switch-id" select="@id"/>
        <xsl:variable name="switch-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'full'"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="selected-id" select="xforms:case[bf:data/@bf:selected='true']/@id"/>

        <table id="{$switch-id}" class="{$switch-classes}">
            <tr>
                <xsl:for-each select="xforms:case[@id='switch-toggles']/xforms:trigger">
                    <xsl:variable name="case-id" select=".//xforms:toggle/@xforms:case | .//xforms:toggle/@case"/>
                    <xsl:choose>
                        <xsl:when test="$case-id=$selected-id">
                            <td id="{concat($case-id, '-tab')}" class="active-tab">
                                <xsl:call-template name="trigger"/>
                            </td>
                        </xsl:when>
                        <xsl:otherwise>
                            <td id="{concat($case-id, '-tab')}" class="inactive-tab">
                                <xsl:call-template name="trigger"/>
                            </td>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:for-each>
                <td class="filler-tab">
                    <xsl:value-of select="'&amp;nbsp;'" disable-output-escaping="yes"/>
                </td>
            </tr>
            <tr>
                <td colspan="{count(xforms:case[@id='switch-toggles']/xforms:trigger) + 1}" class="full-switch-body">
                    <xsl:apply-templates select="xforms:case[not(@id='switch-toggles')]"/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <!-- ### DEFAULT SWITCH ### -->
    <xsl:template match="xforms:switch">
        <xsl:variable name="switch-id" select="@id"/>
        <xsl:variable name="switch-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <div id="{$switch-id}" class="{$switch-classes}">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <!-- ### SELECTED CASE ### -->
    <xsl:template match="xforms:case[bf:data/@bf:selected='true']" name="selected-case">
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'case selected-case'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" />
        </div>
    </xsl:template>

    <!-- ### DE-SELECTED/NON-SELECTED CASE ### -->
    <xsl:template match="xforms:case" name="deselected-case">
    </xsl:template>

</xsl:stylesheet>
