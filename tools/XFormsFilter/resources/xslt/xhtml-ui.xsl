<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xpath-default-namespace="http://www.w3.org/1999/xhtml"
    xmlns:chiba="http://chiba.sourceforge.net/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    exclude-result-prefixes="xforms chiba">

    <!-- ####################################################################################################### -->
    <!-- This stylesheet handles the XForms UI constructs [XForms 1.0, Chapter 9]'group', 'repeat' and           -->
    <!-- 'switch' and offers some standard interpretations for the appearance attribute.                         -->
    <!-- author: joern turner                                                                                    -->
    <!-- ####################################################################################################### -->

    <xsl:param name="chiba-pseudo-item" select="'chiba-pseudo-item'"/>
    <!-- ############################################ PARAMS ################################################### -->
    <!-- ##### should be declared in html4.xsl ###### -->
    <!-- ############################################ VARIABLES ################################################ -->


    <xsl:output method="xhtml" version="1.0" encoding="UTF-8" indent="yes"/>

    <xsl:preserve-space elements="*"/>

    <!-- ####################################################################################################### -->
    <!-- #################################### GROUPS ########################################################### -->
    <!-- ####################################################################################################### -->

    <!-- ### DEFAULT GROUP ### -->
    <xsl:template match="xforms:group" name="group">
        <xsl:variable name="group-id" select="@id"/>
        <xsl:variable name="group-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="@xforms:appearance | @appearance"/>
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

        <fieldset id="{$group-id}" class="{$group-classes}">
            <legend>
                <xsl:choose>
                    <xsl:when test="$group-label and xforms:label">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($group-id, '-label')"/>
                        </xsl:attribute>
                        <xsl:attribute name="class">
                            <xsl:call-template name="assemble-label-classes"/>
                        </xsl:attribute>
                        <xsl:apply-templates select="xforms:label"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="style">display:none;</xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
            </legend>

            <xsl:apply-templates select="*[not(self::xforms:label)]"/>
        </fieldset>
    </xsl:template>

    <!-- ######################################################################################################## -->
    <!-- ####################################### REPEAT ######################################################### -->
    <!-- ######################################################################################################## -->

    <!-- ### MINIMAL REPEAT ### -->
    <xsl:template match="xforms:repeat[@xforms:appearance='minimal'] | xforms:repeat[@appearance='minimal']" name="minimal-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="chiba:data/@chiba:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'minimal'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
            <!-- generate prototype(s) for scripted environment -->
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']">
                <xsl:call-template name="processMinimalPrototype">
                    <xsl:with-param name="id" select="$repeat-id"/>
                </xsl:call-template>
            </xsl:for-each>
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']//xforms:repeat">
                <xsl:call-template name="processRepeatPrototype"/>
            </xsl:for-each>
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']//xforms:itemset">
                <xsl:call-template name="processItemsetPrototype"/>
            </xsl:for-each>
        </xsl:if>

        <div id="{$repeat-id}" class="{$repeat-classes}">
            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <div id="{@id}" class="{$repeat-item-classes}">
                    <xsl:if test="not($scripted='true')">
                        <div class="repeat-selector">
                            <xsl:variable name="outermost-id" select="ancestor-or-self::xforms:repeat/@id"/>
                            <input type="radio" name="{$selector-prefix}{$outermost-id}" value="{$repeat-id}:{position()}">
                                <xsl:if test="string($outermost-id)=string($repeat-id) and string($repeat-index)=string(position())">
                                    <xsl:attribute name="checked">checked</xsl:attribute>
                                </xsl:if>
                            </input>
                        </div>
                    </xsl:if>
                    <xsl:apply-templates/>
                </div>
            </xsl:for-each>
        </div>
        <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
            <!-- register index event handler -->
            <xsl:variable name="function-name" select="concat('register', generate-id())"/>
            <script type="text/javascript">
                dojo.event.connect(dojo.byId("<xsl:value-of select="$repeat-id"/>"),"onclick",setRepeatIndex);
            </script>
        </xsl:if>
    </xsl:template>

    <!-- prototype for minimal repeat -->
    <xsl:template name="processMinimalPrototype">
        <xsl:param name="id"/>

        <div id="{$id}-prototype" class="repeat-prototype enabled readwrite optional valid" style="display:none;">
            <xsl:apply-templates/>
        </div>
    </xsl:template>


    <!-- ### COMPACT REPEAT ### -->
    <xsl:template match="xforms:repeat[@xforms:appearance='compact'] | xforms:repeat[@appearance='compact']" name="compact-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="chiba:data/@chiba:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'compact'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
            <!-- generate prototype(s) for scripted environment -->
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']">
                <xsl:call-template name="processCompactPrototype">
                    <xsl:with-param name="id" select="$repeat-id"/>
                </xsl:call-template>
            </xsl:for-each>
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']//xforms:repeat">
                <xsl:call-template name="processRepeatPrototype"/>
            </xsl:for-each>
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']//xforms:itemset">
                <xsl:call-template name="processItemsetPrototype"/>
            </xsl:for-each>
        </xsl:if>

        <table id="{$repeat-id}" class="{$repeat-classes}">
            <!-- build table header -->
            <xsl:for-each select="xforms:group[@appearance='repeated'][1]">
                <tr class="repeat-header">
                    <xsl:if test="not($scripted ='true')">
                        <td class="repeat-selector"><xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text></td>
                    </xsl:if>
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
                    <xsl:if test="not($scripted='true')">
                        <td class="repeat-selector">
                            <xsl:variable name="outermost-id" select="ancestor-or-self::xforms:repeat/@id"/>
                            <input type="radio" name="{$selector-prefix}{$outermost-id}" value="{$repeat-id}:{position()}">
                                <xsl:if test="string($outermost-id)=string($repeat-id) and string($repeat-index)=string(position())">
                                    <xsl:attribute name="checked">checked</xsl:attribute>
                                </xsl:if>
                            </input>
                        </td>
                    </xsl:if>
                    <xsl:call-template name="processCompactChildren"/>
                </tr>
            </xsl:for-each>
        </table>
        <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
            <!-- register index event handler -->
            <xsl:variable name="function-name" select="concat('register', generate-id())"/>
            <script type="text/javascript">
                dojo.event.connect(dojo.byId("<xsl:value-of select="$repeat-id"/>"),"onclick",setRepeatIndex);
            </script>
        </xsl:if>
    </xsl:template>

    <!-- header for compact repeat -->
    <xsl:template name="processCompactHeader">
        <xsl:for-each select="*[not(self::chiba:*)]">
            <td class="{concat('col-',position())}">
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

        <table class="repeat-prototype">
            <tr id="{$id}-prototype" class="repeat-prototype enabled readwrite optional valid">
                <xsl:call-template name="processCompactChildren"/>
            </tr>
        </table>
    </xsl:template>

    <!-- children for compact repeat -->
    <xsl:template name="processCompactChildren">
        <xsl:for-each select="*[not(self::chiba:*)]">
            <td valign="top" class="{concat('col-',position())}">
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
                <xsl:with-param name="appearance" select="@xforms:appearance | @appearance"/>
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
    <xsl:template match="xforms:repeat[@xforms:appearance='full'] | xforms:repeat[@appearance='full']" name="full-repeat">
        <xsl:variable name="repeat-id" select="@id"/>
        <xsl:variable name="repeat-index" select="chiba:data/@chiba:index"/>
        <xsl:variable name="repeat-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'full'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
            <!-- generate prototype(s) for scripted environment -->
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']">
                <xsl:call-template name="processFullPrototype">
                    <xsl:with-param name="id" select="$repeat-id"/>
                </xsl:call-template>
            </xsl:for-each>
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']//xforms:repeat">
                <xsl:call-template name="processRepeatPrototype"/>
            </xsl:for-each>
            <xsl:for-each select="chiba:data/xforms:group[@appearance='repeated']//xforms:itemset">
                <xsl:call-template name="processItemsetPrototype"/>
            </xsl:for-each>
        </xsl:if>

        <div id="{$repeat-id}" class="{$repeat-classes}">
            <!-- loop repeat entries -->
            <xsl:for-each select="xforms:group[@appearance='repeated']">
                <xsl:variable name="repeat-item-id" select="@id"/>
                <xsl:variable name="repeat-item-classes">
                    <xsl:call-template name="assemble-repeat-item-classes">
                        <xsl:with-param name="selected" select="$repeat-index=position()"/>
                    </xsl:call-template>
                </xsl:variable>

                <xsl:choose>
                    <xsl:when test="not($scripted='true')">
                        <xsl:variable name="outermost-id" select="ancestor-or-self::xforms:repeat/@id"/>
                        <fieldset id="{$repeat-item-id}" class="{$repeat-item-classes}">
                            <legend id="{$repeat-item-id}-label" style="display:none;"/>
                            <div class="repeat-selector">
                                <input type="radio" name="{$selector-prefix}{$outermost-id}" value="{$repeat-id}:{position()}">
                                    <xsl:if test="string($outermost-id)=string($repeat-id) and string($repeat-index)=string(position())">
                                        <xsl:attribute name="checked">checked</xsl:attribute>
                                    </xsl:if>
                                </input>
                            </div>
                            <xsl:apply-templates/>
                        </fieldset>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:call-template name="group-body">
                            <xsl:with-param name="group-id" select="$repeat-item-id"/>
                            <xsl:with-param name="group-classes" select="$repeat-item-classes"/>
                        </xsl:call-template>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:for-each>
        </div>
        <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
            <!-- register index event handler -->
            <xsl:variable name="function-name" select="concat('register', generate-id())"/>
            <script type="text/javascript">
                dojo.event.connect(dojo.byId("<xsl:value-of select="$repeat-id"/>"),"onclick",setRepeatIndex);
            </script>
        </xsl:if>
    </xsl:template>

    <!-- prototype for full repeat -->
    <xsl:template name="processFullPrototype">
        <xsl:param name="id"/>

        <xsl:call-template name="group-body">
            <xsl:with-param name="group-id" select="concat($id, '-prototype')"/>
            <xsl:with-param name="group-classes" select="'repeat-prototype enabled readwrite optional valid'"/>
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
        <xsl:variable name="repeat-index" select="chiba:data/@chiba:index"/>

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
            <xsl:when test="@xforms:appearance='full' or @appearance='full'">
                <xsl:call-template name="processFullPrototype">
                    <xsl:with-param name="id" select="$id"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="@xforms:appearance='compact' or @appearance='compact'">
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
        <xsl:variable name="item-id" select="$chiba-pseudo-item"/>
        <xsl:variable name="itemset-id" select="@id"/>
        <xsl:variable name="name" select="concat($data-prefix,../@id)"/>
        <xsl:variable name="parent" select=".."/>

        <xsl:choose>
            <xsl:when test="local-name($parent)='select1' and ($parent/@xforms:appearance='full' or $parent/@appearance='full')">
                <xsl:call-template name="build-radiobutton-prototype">
                    <xsl:with-param name="item-id" select="$item-id"/>
                    <xsl:with-param name="itemset-id" select="$itemset-id"/>
                    <xsl:with-param name="name" select="$name"/>
                    <xsl:with-param name="parent" select="$parent"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="local-name($parent)='select' and ($parent/@xforms:appearance='full' or $parent/@appearance='full')">
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
    <xsl:template match="xforms:switch[@xforms:appearance='full'] | xforms:switch[@appearance='full']" name="full-switch">
        <xsl:variable name="switch-id" select="@id"/>
        <xsl:variable name="switch-classes">
            <xsl:call-template name="assemble-compound-classes">
                <xsl:with-param name="appearance" select="'full'"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="selected-id" select="xforms:case[chiba:data/@chiba:selected='true']/@id"/>

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
                <xsl:with-param name="appearance" select="@xforms:appearance | @appearance"/>
            </xsl:call-template>
        </xsl:variable>

        <div id="{$switch-id}" class="{$switch-classes}">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <!-- ### SELECTED CASE ### -->
    <xsl:template match="xforms:case[chiba:data/@chiba:selected='true']" name="selected-case">
        <xsl:variable name="case-id" select="@id"/>
        <xsl:variable name="case-classes" select="'case selected-case'"/>

        <div id="{$case-id}" class="{$case-classes}">
            <xsl:apply-templates select="*[not(self::xforms:label)]" />
        </div>
    </xsl:template>

    <!-- ### DE-SELECTED/NON-SELECTED CASE ### -->
    <xsl:template match="xforms:case" name="deselected-case">
        <!-- render only in scripted environment -->
        <xsl:if test="$scripted='true'">
            <xsl:variable name="case-id" select="@id"/>
            <xsl:variable name="case-classes" select="'case deselected-case'"/>

            <div id="{$case-id}" class="{$case-classes}">
                <xsl:apply-templates select="*[not(self::xforms:label)]" />
            </div>
        </xsl:if>
    </xsl:template>

    <!-- ### CSS CLASS ASSEMBLY ### -->

    <!-- assembles form control classes -->
    <xsl:template name="assemble-control-classes">
        <xsl:param name="appearance"/>

        <xsl:variable name="name-classes">
            <xsl:call-template name="get-name-classes">
                <xsl:with-param name="appearance" select="$appearance"/>
            </xsl:call-template>
        </xsl:variable>
        
        <xsl:variable name="type">
            <xsl:call-template name="getType"/>
        </xsl:variable>

        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes"/>
        </xsl:variable>

        <xsl:variable name="author-classes">
            <xsl:call-template name="get-author-classes"/>
        </xsl:variable>

        <xsl:variable name="incremental">
            <xsl:choose>
                <xsl:when test="@xforms:incremental | @incremental ='true'">incremental</xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:variable>


        <!-- *** this is to ease styling of repeated controls and scripting *** -->
        <xsl:variable name="repeat-id" select="ancestor::*[name(.)='xforms:repeat'][1]/@id" />
        <xsl:variable name="pos" select="position()" />

        <xsl:variable name="repeatClasses">
            <xsl:choose>
                <xsl:when test="boolean(string-length($repeat-id) > 0)">
                    <xsl:value-of select="concat($repeat-id,'-',$pos,' repeated')"/>
                </xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:variable>


        <xsl:value-of select="normalize-space(concat($name-classes, ' ',$type,' ',$mip-classes, ' ', $author-classes,' ',$incremental,' ',$repeatClasses))"/>
    </xsl:template>

    <!-- assembles label classes -->
    <xsl:template name="assemble-label-classes">
        <xsl:for-each select="xforms:label[1]">
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

        <xsl:value-of select="normalize-space(concat($name-classes, ' ', $mip-classes, ' ', $author-classes))"/>
    </xsl:template>

    <!-- assembles repeat item classes -->
    <xsl:template name="assemble-repeat-item-classes">
        <xsl:param name="selected"/>

        <xsl:variable name="name-classes">
            <xsl:choose>
                <xsl:when test="boolean($selected)">
                    <xsl:text>repeat-item repeat-index</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text>repeat-item</xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="mip-classes">
            <xsl:call-template name="get-mip-classes"/>
        </xsl:variable>

        <xsl:value-of select="normalize-space(concat($name-classes, ' ', $mip-classes))"/>
    </xsl:template>

    <!-- ### CSS CLASS ASSEMBLY HELPERS ### -->
    <xsl:template name="get-author-classes">
        <xsl:choose>
            <xsl:when test="@class">
                <xsl:value-of select="@class"/>
            </xsl:when>
            <!--<xsl:when test="@class">
                <xsl:value-of select="@class"/>
            </xsl:when>-->
        </xsl:choose>
    </xsl:template>

    <xsl:template name="get-name-classes">
        <xsl:param name="name" select="local-name()"/>
        <xsl:param name="appearance"/>

        <xsl:choose>
            <xsl:when test="$appearance">
                <xsl:value-of select="concat($name, ' ', $appearance, '-', $name)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$name"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="get-mip-classes">
        <xsl:param name="limited" select="false()"/>

        <xsl:if test="chiba:data">
            <xsl:choose>
                <xsl:when test="boolean($limited)">
                    <xsl:choose>
                        <xsl:when test="chiba:data/@chiba:enabled='false'">
                            <xsl:text>disabled</xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>enabled</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:variable name="enabled">
                        <xsl:choose>
                            <xsl:when test="chiba:data/@chiba:enabled='false'">
                                <xsl:text>disabled</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>enabled</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="readonly">
                        <xsl:choose>
                            <xsl:when test="chiba:data/@chiba:readonly='true'">
                                <xsl:text>readonly</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>readwrite</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="required">
                        <xsl:choose>
                            <xsl:when test="chiba:data/@chiba:required='true'">
                                <xsl:text>required</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>optional</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="valid">
                        <xsl:choose>
                            <xsl:when test="chiba:data/@chiba:valid='false'">
                                <xsl:text>invalid</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>valid</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:value-of select="concat($enabled,' ',$readonly,' ',$required, ' ', $valid)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
