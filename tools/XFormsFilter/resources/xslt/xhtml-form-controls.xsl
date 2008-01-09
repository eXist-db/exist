<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xpath-default-namespace="http://www.w3.org/1999/xhtml"
    xmlns:chiba="http://chiba.sourceforge.net/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    exclude-result-prefixes="xforms chiba">
    
    <!-- ############################################ VARIABLES ################################################ -->

    <xsl:variable name="data-prefix" select="'d_'"/>
    <xsl:variable name="trigger-prefix" select="'t_'"/>
    <xsl:variable name="remove-upload-prefix" select="'ru_'"/>

    <xsl:variable name="compositeDate-prefix" as="xs:string" select="'c_dt_'"/>
    <xsl:variable name="compositeTime-prefix" as="xs:string" select="'c_tm_'"/>
    <xsl:variable name="compositeDateTime-prefix" as="xs:string" select="'c_dttm_'"/>
    <xsl:variable name="compositeDayTimeDuration-prefix" as="xs:string" select="'c_datmd_'"/>
            
    <!-- change this to your ShowAttachmentServlet -->
    <xsl:variable name="show-attachment-action" select="'http://localhost:8080/exist/servlet/db/CommunityDirectory/index.xql?action=getimage'"/>
    
    
    <!-- ############################################ OUTPUT ################################################### -->
    
    <xsl:output method="xhtml" version="1.0" encoding="UTF-8" indent="yes"/>
    
    <xsl:preserve-space elements="*"/>
    
    <!-- ############################################ FUNCTION TEMPLATES ################################################### -->
    
    <!-- ######################################################################################################## -->
    <!-- This stylesheet serves as a 'library' for HTML form controls. It contains only named templates and may   -->
    <!-- be re-used in different layout-stylesheets to create the naked controls.                                 -->
    <!-- ######################################################################################################## -->

    <!-- build input control -->
    <xsl:template name="input">
        
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id" />
        <xsl:variable name="pos" select="position()" />
        <xsl:variable name="id" select="@id" />
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>

        <!-- this is only an interims solution until Schema type and base type handling has been clarified -->
        <xsl:variable name="type">
            <xsl:call-template name="getType"/>
        </xsl:variable>

        <!--<xsl:value-of select="$type"/>-->
        <xsl:choose>
            <!-- input bound to 'date' or 'dateTime' type -->
            <!--<xsl:when test="chiba:data[@chiba:type='date' or @chiba:type='dateTime']">-->
            <xsl:when test="($type='date' or $type='dateTime') and $scripted='true'">
                <script type="text/javascript">
                    dojo.require("dojo.widget.DropdownDatePicker");
                    dojo.require("dojo.widget.Button");
                    dojo.require("chiba.DropdownDatePicker");

                </script>
                <input id="{concat($id,'-value')}" type="text" name="{$name}" value="" readonly="" class="value">
                    <xsl:if test="$scripted='true'">
                        <xsl:attribute name="dojoType">XFDropdownDatePicker</xsl:attribute>
                        <xsl:attribute name="value">
                            <xsl:value-of select="chiba:data/text()"/>
                        </xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                </input>
            </xsl:when>
            <xsl:when test="$type='dateTime' and $scripted = 'true'">
                <script type="text/javascript">
                    dojo.require("chiba.Time");
                </script>
                <input id="{concat($id,'-value')}" type="text" name="{$name}" value="" readonly="" class="value">
                    <xsl:attribute name="dojoType">XFTime</xsl:attribute>
                    <xsl:attribute name="value">21</xsl:attribute>
                    <xsl:apply-templates select="xforms:hint"/>
                </input>
            </xsl:when>
            <!-- dayTimeDuration control -->
            <xsl:when test="$type='dayTimeDuration'">
                <!-- days value -->
                <xsl:variable name="days" as="xs:integer">
                    <xsl:choose>
                        <xsl:when test="string-length(chiba:data/text()) = 0">
                            <!-- default days -->0
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="days-from-duration(chiba:data/text())"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <!-- hours value -->
                <xsl:variable name="hours" as="xs:integer">
                    <xsl:choose>
                        <xsl:when test="string-length(chiba:data/text()) = 0">
                            <!-- default hours -->0
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="hours-from-duration(chiba:data/text())"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <!-- minutes value -->
                <xsl:variable name="minutes" as="xs:integer">
                    <xsl:choose>
                        <xsl:when test="string-length(chiba:data/text()) = 0">
                            <!-- default minutes -->0
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="minutes-from-duration(chiba:data/text())"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <!-- seconds value -->
                <xsl:variable name="seconds" as="xs:integer">
                    <xsl:choose>
                        <xsl:when test="string-length(chiba:data/text()) = 0">
                            <!-- seconds hours -->0
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="seconds-from-duration(chiba:data/text())"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <fieldset>
                    <!-- days input -->
                    Days
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($compositeDayTimeDuration-prefix,'days_',$id)"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="build-items">
                            <xsl:with-param name="parent">
                                <xsl:for-each select="(1 to 31)">
                                    <xforms:item id="">
                                        <xsl:if test=". = $days">
                                            <xsl:attribute name="selected">true</xsl:attribute>
                                        </xsl:if>
                                        <xforms:label><xsl:value-of select="."/></xforms:label>
                                        <xforms:value><xsl:value-of select="."/></xforms:value>
                                    </xforms:item>
                                </xsl:for-each>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:element>
                    <!-- Hours input -->
                    Hours
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($compositeDayTimeDuration-prefix,'hours_',$id)"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="build-items">
                            <xsl:with-param name="parent">
                                <xsl:for-each select="(1 to 23)">
                                    <xforms:item id="">
                                        <xsl:if test=". = $hours">
                                            <xsl:attribute name="selected">true</xsl:attribute>
                                        </xsl:if>
                                        <xforms:label><xsl:value-of select="."/></xforms:label>
                                        <xforms:value><xsl:value-of select="."/></xforms:value>
                                    </xforms:item>
                                </xsl:for-each>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:element>
                    <!-- minutes input -->
                    Minutes
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($compositeDayTimeDuration-prefix,'minutes_',$id)"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="build-items">
                            <xsl:with-param name="parent">
                                <xsl:for-each select="(1 to 59)">
                                    <xforms:item id="">
                                        <xsl:if test=". = $minutes">
                                            <xsl:attribute name="selected">true</xsl:attribute>
                                        </xsl:if>
                                        <xforms:label><xsl:value-of select="."/></xforms:label>
                                        <xforms:value><xsl:value-of select="."/></xforms:value>
                                    </xforms:item>
                                </xsl:for-each>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:element>
                    <!-- seconds input -->
                    Seconds
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($compositeDayTimeDuration-prefix,'seconds_',$id)"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="build-items">
                            <xsl:with-param name="parent">
                                <xsl:for-each select="(1 to 59)">
                                    <xforms:item id="">
                                        <xsl:if test=". = $seconds">
                                            <xsl:attribute name="selected">true</xsl:attribute>
                                        </xsl:if>
                                        <xforms:label><xsl:value-of select="."/></xforms:label>
                                        <xforms:value><xsl:value-of select="."/></xforms:value>
                                    </xforms:item>
                                </xsl:for-each>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:element>
                </fieldset>
            </xsl:when>
            <!-- Date drop down controls -->
            <xsl:when test="($type='date' or $type='dateTime') and $scripted != 'true'">
                <xsl:variable name="prefix" as="xs:string">
	         <xsl:choose>
                        <xsl:when test="$type='date'"><xsl:value-of select="$compositeDate-prefix"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="$compositeDateTime-prefix"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <xsl:variable name="year">
                    <xsl:choose>
                        <xsl:when test="chiba:data/text() = ''">
                            <!-- default year (this year) -->
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:choose>
                                <xsl:when test="$type='date'"><xsl:value-of select="year-from-date(chiba:data/text())"/></xsl:when>
                                <xsl:otherwise><xsl:value-of select="year-from-dateTime(chiba:data/text())"/></xsl:otherwise>    
                            </xsl:choose>                            
                        </xsl:otherwise>
                    </xsl:choose> 
                </xsl:variable>
                <xsl:variable name="month" as="xs:integer">
                    <xsl:choose>
                        <xsl:when test="chiba:data/text() = ''">
                            <!-- default month -->
                            0
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:choose>
                                <xsl:when test="$type='date'"><xsl:value-of select="month-from-date(chiba:data/text())"/></xsl:when>
                                <xsl:otherwise><xsl:value-of select="month-from-dateTime(chiba:data/text())"/></xsl:otherwise>    
                            </xsl:choose>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <xsl:variable name="day" as="xs:integer">
                    <xsl:choose>
                        <xsl:when test="chiba:data/text() = ''">
                            <!-- default day -->
                            0
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:choose>
                                <xsl:when test="$type='date'"><xsl:value-of select="day-from-date(chiba:data/text())"/></xsl:when>
                                <xsl:otherwise><xsl:value-of select="day-from-dateTime(chiba:data/text())"/></xsl:otherwise>    
                            </xsl:choose>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <fieldset>
                    <!-- year input -->
                    Year
                    <xsl:element name="input">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($prefix,'year_',$id)"/>
                        </xsl:attribute>
                        <xsl:attribute name="type">text</xsl:attribute>
                        <xsl:attribute name="value">
                            <xsl:value-of select="$year"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="size">4</xsl:attribute>
                        <xsl:attribute name="maxlength">4</xsl:attribute>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:if test="$scripted='true'">
                            <xsl:choose>
                                <xsl:when test="$incremental='true'">
                                    <xsl:attribute name="onkeyup">setXFormsValue(this);</xsl:attribute>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="onchange">setXFormsValue(this);</xsl:attribute>
                                </xsl:otherwise>
                            </xsl:choose>
                            <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                        </xsl:if>
                        <xsl:apply-templates select="xforms:hint"/>
                    </xsl:element>
                    <!-- month select -->
                    Month
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($prefix,'month_',$id)"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="build-items">
                            <xsl:with-param name="parent">
                                <xsl:for-each select="('January','February','March','April','May','June','July','August','September','October','November','December')">
                                    <xforms:item id="">
                                        <xsl:if test="position() = $month">
                                            <xsl:attribute name="selected">true</xsl:attribute>
                                        </xsl:if>"
                                        <xforms:label><xsl:value-of select="."/></xforms:label>
                                        <xforms:value><xsl:value-of select="position()"/></xforms:value>
                                    </xforms:item>
                                </xsl:for-each>                                
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:element>
                    <!-- day select -->
                    Day
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($prefix,'day_',$id)"/>
                        </xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="build-items">
                            <xsl:with-param name="parent">
                                <xsl:for-each select="(1 to 31)">
                                    <xforms:item id="">
                                        <xsl:if test=". = $day">
                                            <xsl:attribute name="selected">true</xsl:attribute>
                                        </xsl:if>
                                        <xforms:label><xsl:value-of select="."/></xforms:label>
                                        <xforms:value><xsl:value-of select="."/></xforms:value>
                                    </xforms:item>
                                </xsl:for-each>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:element>
                </fieldset>
                <!-- Time control for datetime -->
                <xsl:if test="$type='dateTime'">
                    <xsl:variable name="hour" as="xs:integer">
                        <xsl:choose>
                            <xsl:when test="chiba:data/text() = ''">
                                <!-- default hour value -->
                                0
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="hours-from-dateTime(chiba:data/text())"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:variable name="minute" as="xs:integer">
                        <xsl:choose>
                            <xsl:when test="chiba:data/text() = ''">
                                <!-- default minute value -->
                                0
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="minutes-from-dateTime(chiba:data/text())"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:variable name="second" as="xs:integer">
                        <xsl:choose>
                            <xsl:when test="chiba:data/text() = ''">
                                <!-- default second value -->
                                0
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="xs:integer(seconds-from-dateTime(chiba:data/text()))"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:variable name="timezone" as="xs:integer">
                        <xsl:choose>
                            <xsl:when test="chiba:data/text() = '' or empty(timezone-from-dateTime(chiba:data/text()))">
                                <!-- default timezone value (this timezone) -->
                                <xsl:value-of select="hours-from-duration(timezone-from-dateTime(current-dateTime()))"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="hours-from-duration(timezone-from-dateTime(chiba:data/text()))"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <fieldset>
                        <!-- hour select -->
                        Hour
                        <xsl:element name="select">
                            <xsl:attribute name="id">
                                <xsl:value-of select="concat($id,'-value')"/>
                            </xsl:attribute>
                            <xsl:attribute name="name">
                                <xsl:value-of select="concat($compositeDateTime-prefix,'hour_',$id)"/>
                            </xsl:attribute>
                            <xsl:if test="chiba:data/@chiba:readonly='true'">
                                <xsl:attribute name="disabled">disabled</xsl:attribute>
                            </xsl:if>
                            <xsl:attribute name="class">value</xsl:attribute>
                            <xsl:apply-templates select="xforms:hint"/>
                            <xsl:call-template name="build-items">
                                <xsl:with-param name="parent">
                                    <xsl:for-each select="(0 to 23)">
                                        <xforms:item id="">
                                            <xsl:if test=". = $hour">
                                                <xsl:attribute name="selected">true</xsl:attribute>
                                            </xsl:if>
                                            <xforms:label><xsl:value-of select="."/></xforms:label>
                                            <xforms:value><xsl:value-of select="."/></xforms:value>
                                        </xforms:item>
                                    </xsl:for-each>
                                </xsl:with-param>
                            </xsl:call-template>
                        </xsl:element>
                        <!-- minute select -->
                        Minute
                        <xsl:element name="select">
                            <xsl:attribute name="id">
                                <xsl:value-of select="concat($id,'-value')"/>
                            </xsl:attribute>
                            <xsl:attribute name="name">
                                <xsl:value-of select="concat($compositeDateTime-prefix,'minute_',$id)"/>
                            </xsl:attribute>
                            <xsl:if test="chiba:data/@chiba:readonly='true'">
                                <xsl:attribute name="disabled">disabled</xsl:attribute>
                            </xsl:if>
                            <xsl:attribute name="class">value</xsl:attribute>
                            <xsl:apply-templates select="xforms:hint"/>
                            <xsl:call-template name="build-items">
                                <xsl:with-param name="parent">
                                    <xsl:for-each select="(0 to 59)">
                                        <xforms:item id="">
                                            <xsl:if test=". = $minute">
                                                <xsl:attribute name="selected">true</xsl:attribute>
                                            </xsl:if>
                                            <xforms:label><xsl:value-of select="."/></xforms:label>
                                            <xforms:value><xsl:value-of select="."/></xforms:value>
                                        </xforms:item>
                                    </xsl:for-each>
                                </xsl:with-param>
                            </xsl:call-template>
                        </xsl:element>
                        <!-- second select -->
                        Second
                        <xsl:element name="select">
                            <xsl:attribute name="id">
                                <xsl:value-of select="concat($id,'-value')"/>
                            </xsl:attribute>
                            <xsl:attribute name="name">
                                <xsl:value-of select="concat($compositeDateTime-prefix,'second_',$id)"/>
                            </xsl:attribute>
                            <xsl:if test="chiba:data/@chiba:readonly='true'">
                                <xsl:attribute name="disabled">disabled</xsl:attribute>
                            </xsl:if>
                            <xsl:attribute name="class">value</xsl:attribute>
                            <xsl:apply-templates select="xforms:hint"/>
                            <xsl:call-template name="build-items">
                                <xsl:with-param name="parent">
                                    <xsl:for-each select="(0 to 59)">
                                        <xforms:item id="">
                                            <xsl:if test=". = $second">
                                                <xsl:attribute name="selected">true</xsl:attribute>
                                            </xsl:if>
                                            <xforms:label><xsl:value-of select="."/></xforms:label>
                                            <xforms:value><xsl:value-of select="."/></xforms:value>
                                        </xforms:item>
                                    </xsl:for-each>
                                </xsl:with-param>
                            </xsl:call-template>
                        </xsl:element>
                        <!-- timezone select -->
                        Timezone
                        <xsl:element name="select">
                            <xsl:attribute name="id">
                                <xsl:value-of select="concat($id,'-value')"/>
                            </xsl:attribute>
                            <xsl:attribute name="name">
                                <xsl:value-of select="concat($compositeDateTime-prefix,'timezone_',$id)"/>
                            </xsl:attribute>
                            <xsl:if test="chiba:data/@chiba:readonly='true'">
                                <xsl:attribute name="disabled">disabled</xsl:attribute>
                            </xsl:if>
                            <xsl:attribute name="class">value</xsl:attribute>
                            <xsl:apply-templates select="xforms:hint"/>
                            <xsl:call-template name="build-items">
                                <xsl:with-param name="parent">
                                    <xsl:for-each select="(-12 to 12)">
                                        <xforms:item id="">
                                            <!--<xsl:choose>
                                                <xsl:when test="$timezone != ''"> -->
                                                    <xsl:if test=". = $timezone">
                                                        <xsl:attribute name="selected">true</xsl:attribute>
                                                    </xsl:if>
                                                <!--</xsl:when>
                                                <xsl:otherwise>
                                                    <xsl:if test=". = 0"><xsl:attribute name="selected">true</xsl:attribute></xsl:if>
                                                </xsl:otherwise>
                                            </xsl:choose>-->
                                            <xforms:label><xsl:if test=". > -1">+</xsl:if><xsl:value-of select="."/>:00</xforms:label>
                                            <xforms:value><xsl:if test=". > -1">+</xsl:if><xsl:value-of select="."/>:00</xforms:value>
                                        </xforms:item>
                                    </xsl:for-each>
                                </xsl:with-param>
                            </xsl:call-template>
                        </xsl:element>
                    </fieldset>
                </xsl:if>
            </xsl:when>
            <xsl:when test="$type='boolean'">
                <xsl:if test="$scripted='true'">
                    <script type="text/javascript">
                        dojo.require("chiba.Boolean");
                    </script>
                </xsl:if>
                <input id="{$id}-value" type="checkbox"  name="{$name}" class="value">
                    <xsl:if test="chiba:data/@chiba:readonly='true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:choose>
                        <xsl:when test="chiba:data='true'">
                            <xsl:attribute name="checked">true</xsl:attribute>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:attribute name="value">true</xsl:attribute>
                        </xsl:otherwise>
                    </xsl:choose>
                    <xsl:if test="$scripted='true'">
                        <xsl:attribute name="dojoType">XFBoolean</xsl:attribute>
                        <xsl:attribute name="xfreadonly"><xsl:value-of select="chiba:data/@chiba:readonly"/></xsl:attribute>
                        <xsl:attribute name="xfincremental"><xsl:value-of select="$incremental"/></xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                </input>
                <!-- create hidden parameter for deselection -->
                <xsl:if test="chiba:data='true' and $scripted='true'">
                    <input type="hidden" name="{$name}" value="false"/>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <xsl:element name="input">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="name">
                        <xsl:value-of select="concat($data-prefix,$id)"/>
                    </xsl:attribute>
                    <xsl:attribute name="type">text</xsl:attribute>
                    <xsl:attribute name="value">
                        <xsl:value-of select="chiba:data/text()"/>
                    </xsl:attribute>
                    <xsl:if test="chiba:data/@chiba:readonly='true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:attribute name="class">value</xsl:attribute>
                    <xsl:if test="$scripted='true'">
                        <xsl:choose>
                            <xsl:when test="$incremental='true'">
                                <xsl:attribute name="onkeyup">setXFormsValue(this);</xsl:attribute>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="onchange">setXFormsValue(this);</xsl:attribute>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                </xsl:element>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="getType">
        <xsl:choose>
            <xsl:when test="contains(chiba:data/@chiba:type,':')">
                <xsl:value-of select="substring-after(chiba:data/@chiba:type,':')"/>
            </xsl:when>
            <xsl:when test="chiba:data/@chiba:type">
                <xsl:value-of select="chiba:data/@chiba:type"/>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <!-- build image trigger / submit [deprecated]-->
<!--
    <xsl:template name="image-trigger">
        <xsl:element name="input">
            <xsl:variable name="id" select="@id"/>
            <xsl:variable name="repeat-id" select="ancestor::*[name(.)='xforms:repeat'][1]/@id"/>
            <xsl:attribute name="id">
                <xsl:value-of select="concat($id,'-value')"/>
            </xsl:attribute>
            <xsl:attribute name="name">
                <xsl:value-of select="concat($trigger-prefix,$id)"/>
            </xsl:attribute>
            <xsl:attribute name="type">image</xsl:attribute>
            <xsl:attribute name="value">
                <xsl:value-of select="xforms:label"/>
            </xsl:attribute>
            <xsl:attribute name="src">
                <xsl:value-of select="xforms:label/@xlink:href"/>
            </xsl:attribute>
            <xsl:attribute name="class">value</xsl:attribute>
            <xsl:if test="chiba:data/@chiba:readonly='true'">
                <xsl:attribute name="disabled">disabled</xsl:attribute>
            </xsl:if>
            <xsl:if test="$scripted='true'">
                <xsl:attribute name="onclick">activate(this);</xsl:attribute>
            </xsl:if>
            <xsl:apply-templates select="xforms:hint"/>
        </xsl:element>

    </xsl:template>
-->

    <!-- build output -->
    <xsl:template name="output">

        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="css" select="@class"/>
        <xsl:choose>
<!--
            <xsl:when test="@xforms:appearance='minimal'">
                <span id="{concat($id,'-value')}">
                    <xsl:value-of select="chiba:data/text()"/>
                </span>
            </xsl:when>
-->
            <xsl:when test="contains(@xforms:mediatype,'image/') or contains(@mediatype,'image/')">
                <xsl:element name="img">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="src">
                        <xsl:variable name="src" select="substring-after($action-url, '&amp;page=')"/>
                        <xsl:variable name="path" select="@ref"/>
                        <xsl:value-of select="concat($show-attachment-action, '&amp;src=', $src, '&amp;image=', $path) "/>
                    </xsl:attribute>
                    <xsl:attribute name="alt"><xsl:value-of select="xforms:label"/></xsl:attribute>
                    <xsl:apply-templates select="xforms:hint"/>
                </xsl:element>
            </xsl:when>
            <xsl:when test="contains(@xforms:mediatype,'text/html') or contains(@mediatype,'text/html')">
                <span id="{concat($id,'-value')}" class="mediatype-text-html">
                    <xsl:value-of select="chiba:data/text()" disable-output-escaping="yes"/>
                </span>
            </xsl:when>
            <xsl:when test="chiba:data[@chiba:type='anyURI'] and (not(@xforms:mediatype or @mediatype))">
                <xsl:element name="a">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="href">
                        <xsl:value-of select="chiba:data/text()"/>
                    </xsl:attribute>
                    <xsl:apply-templates select="xforms:hint"/>
                    <xsl:value-of select="chiba:data/text()"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates select="xforms:hint"/>
                <span id="{concat($id,'-value')}">
                    <xsl:value-of select="chiba:data/text()"/>
                </span>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- build range -->
    <!--
         todo: add input/output control at the side of slider in scripted mode
         todo: support different appearances ?
         todo: support incremental ?
    -->
    <xsl:template name="range">
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>
        <xsl:variable name="start" select="@xforms:start | @start"/>
        <xsl:variable name="end" select="@xforms:end | @end"/>
        <xsl:variable name="step" select="@xforms:step | @step"/>
        <xsl:variable name="showInput">
            <xsl:choose>
                <xsl:when test="@xforms:appearance='full' or @appearance='full'">true</xsl:when>
                <xsl:otherwise>false</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>
        <xsl:variable name="value" select="chiba:data/text()"/>

        <div>
            <xsl:choose>
                <xsl:when test="$scripted='true'">
                    <input type="hidden" id="{$id}-value" value="$value"/>
                    <!--<table border="0" cellpadding="0" cellspacing="1" id="{$id}-value" class="range-widget">-->
                    <table border="0" cellpadding="0" cellspacing="1" class="range-widget">
                        <tr class="rangesteps" bgcolor="silver">
                            <xsl:call-template name="drawRangeScripted">
                                <xsl:with-param name="rangeId" select="$id"/>
                                <xsl:with-param name="value" select="$value"/>
                                <xsl:with-param name="current" select="$start"/>
                                <xsl:with-param name="step" select="$step"/>
                                <xsl:with-param name="end" select="$end"/>
                            </xsl:call-template>
                        </tr>
                    </table>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:element name="select">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
<!--
                        <xsl:attribute name="name">
                            <xsl:value-of select="$name"/>
                        </xsl:attribute>
-->
                        <xsl:attribute name="size">1</xsl:attribute>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:call-template name="drawRangeBasic">
                            <xsl:with-param name="rangeId" select="$id"/>
                            <xsl:with-param name="value" select="$value"/>
                            <xsl:with-param name="current" select="$start"/>
                            <xsl:with-param name="step" select="$step"/>
                            <xsl:with-param name="end" select="$end"/>
                        </xsl:call-template>
                    </xsl:element>
                </xsl:otherwise>
            </xsl:choose>
        </div>
    </xsl:template>

    <!-- *** graphical representation of range as slider component *** -->
    <xsl:template name="drawRangeScripted">
        <xsl:param name="rangeId"/>
        <xsl:param name="value"/>
        <xsl:param name="current"/>
        <xsl:param name="step"/>
        <xsl:param name="end"/>

        <xsl:if test="$current &lt;= $end">
            <xsl:variable name="classes">
                <xsl:choose>
                    <xsl:when test="$value = $current">step rangevalue</xsl:when>
                    <xsl:otherwise>step</xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <xsl:element name="td">
                <!-- mark the currently active value with name -->
                <!-- todo: change this - this breaks html conformity! -->
<!--
                <xsl:if test="$value = $current">
                    <xsl:attribute name="name"><xsl:value-of select="concat($rangeId,'-value')"/></xsl:attribute>
                </xsl:if>
-->
                <xsl:attribute name="id"><xsl:value-of select="concat($rangeId,$current)"/></xsl:attribute>
                <xsl:attribute name="class"><xsl:value-of select="$classes"/></xsl:attribute>
                <!-- todo: change to use 'this' instead of 'rangeId' -->
                <a href="javascript:setRange('{$rangeId}',{$current});"><img alt="" src="images/trans.gif" height="25" width="6" title="{$current}"/></a>
            </xsl:element>

        </xsl:if>

        <xsl:variable name="newStep" select="$current + $step"/>
        <xsl:if test="$newStep &lt;= $end">
            <xsl:call-template name="drawRangeScripted">
                <xsl:with-param name="rangeId" select="$rangeId"/>
                <xsl:with-param name="value" select="$value"/>
                <xsl:with-param name="current" select="$newStep"/>
                <xsl:with-param name="step" select="$step"/>
                <xsl:with-param name="end" select="$end"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <!-- *** fallback template for representing range as a combobox in non-scripted mode *** -->
    <xsl:template name="drawRangeBasic">
        <xsl:param name="rangeId"/>
        <xsl:param name="value"/>
        <xsl:param name="current"/>
        <xsl:param name="step"/>
        <xsl:param name="end"/>

        <xsl:if test="$current &lt;= $end">
            <xsl:variable name="classes">
                <xsl:choose>
                    <xsl:when test="$value = $current">step rangevalue</xsl:when>
                    <xsl:otherwise>step</xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <option id="{$rangeId}-value" value="{$current}" title="{xforms:hint}" class="selector-item">
                <xsl:if test="$value = $current">
                    <xsl:attribute name="selected">selected</xsl:attribute>
                </xsl:if>
                <xsl:value-of select="$current"/>
            </option>
        </xsl:if>

        <xsl:variable name="newStep" select="$current + $step"/>
        <xsl:if test="$newStep &lt;= $end">
            <xsl:call-template name="drawRangeBasic">
                <xsl:with-param name="rangeId" select="$rangeId"/>
                <xsl:with-param name="value" select="$value"/>
                <xsl:with-param name="current" select="$newStep"/>
                <xsl:with-param name="step" select="$step"/>
                <xsl:with-param name="end" select="$end"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <!-- build secret control -->
    <xsl:template name="secret">
        <xsl:param name="maxlength"/>

        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>

        <xsl:element name="input">
            <xsl:attribute name="id">
                <xsl:value-of select="concat($id,'-value')"/>
            </xsl:attribute>
            <xsl:attribute name="name">
                <xsl:value-of select="concat($data-prefix,$id)"/>
            </xsl:attribute>
            <xsl:attribute name="class">value</xsl:attribute>
            <xsl:attribute name="type">password</xsl:attribute>
            <xsl:attribute name="value">
                <xsl:value-of select="chiba:data/text()"/>
            </xsl:attribute>
            <xsl:if test="$maxlength">
                <xsl:attribute name="maxlength">
                    <xsl:value-of select="$maxlength"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:if test="chiba:data/@chiba:readonly='true'">
                <xsl:attribute name="disabled">disabled</xsl:attribute>
            </xsl:if>
            <xsl:if test="$scripted='true'">
                <xsl:choose>
                    <xsl:when test="$incremental='true'">
                        <xsl:attribute name="onkeyup">setXFormsValue(this);</xsl:attribute>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="onchange">setXFormsValue(this);</xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
            </xsl:if>
            <xsl:apply-templates select="xforms:hint"/>
        </xsl:element>
    </xsl:template>


    <xsl:template name="select1">
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>
        <xsl:variable name="parent" select="."/>
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>
        <xsl:variable name="handler">
            <xsl:choose>
                <xsl:when test="$incremental='false'">onblur</xsl:when>
                <xsl:otherwise>onchange</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="@xforms:appearance='compact' or @appearance='compact'">
                <xsl:element name="select">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="name">
                        <xsl:value-of select="$name"/>
                    </xsl:attribute>
                    <xsl:attribute name="size">5</xsl:attribute>
                    <xsl:attribute name="class">value</xsl:attribute>
                    <xsl:if test="chiba:data/@chiba:readonly='true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:if test="$scripted='true'">
                        <xsl:attribute name="{$handler}">setXFormsValue(this);</xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                    <xsl:call-template name="build-items">
                        <xsl:with-param name="parent" select="$parent"/>
                    </xsl:call-template>
                </xsl:element>
                <!-- handle itemset prototype -->
                <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/chiba:data/xforms:item">
                        <xsl:call-template name="build-item-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
                <!-- create hidden parameter for deselection -->
                <input type="hidden" name="{$name}" value=""/>
            </xsl:when>
            <xsl:when test="@xforms:appearance='full' or @appearance='full'">
                <xsl:call-template name="build-radiobuttons">
                    <xsl:with-param name="id" select="$id"/>
                    <xsl:with-param name="name" select="$name"/>
                    <xsl:with-param name="parent" select="$parent"/>
                </xsl:call-template>
                <!-- handle itemset prototype -->
                <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/chiba:data/xforms:item">
                        <xsl:call-template name="build-radiobutton-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                            <xsl:with-param name="name" select="$name"/>
                            <xsl:with-param name="parent" select="$parent"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
                <!-- create hidden parameter for identification and deselection -->
                <input type="hidden" id="{$id}-value" name="{$name}" value=""/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:element name="select">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="name">
                        <xsl:value-of select="$name"/>
                    </xsl:attribute>
                    <xsl:attribute name="size">1</xsl:attribute>
                    <xsl:attribute name="class">value</xsl:attribute>
                    <xsl:if test="chiba:data/@chiba:readonly='true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:if test="$scripted='true'">
                        <xsl:attribute name="{$handler}">setXFormsValue(this);</xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                    <xsl:call-template name="build-items">
                        <xsl:with-param name="parent" select="$parent"/>
                    </xsl:call-template>
                </xsl:element>
                <!-- handle itemset prototype -->
                <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/chiba:data/xforms:item">
                        <xsl:call-template name="build-item-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
                <!-- create hidden parameter for deselection -->
                <input type="hidden" name="{$name}" value=""/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>


    <xsl:template name="select">

        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>
        <xsl:variable name="parent" select="."/>
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>
        <xsl:variable name="handler">
            <xsl:choose>
                <xsl:when test="$incremental='false'">onblur</xsl:when>
                <xsl:otherwise>onchange</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="@xforms:appearance='compact' or @appearance='compact'">
                <xsl:element name="select">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="name">
                        <xsl:value-of select="$name"/>
                    </xsl:attribute>
                    <xsl:attribute name="multiple">multiple</xsl:attribute>
                    <xsl:attribute name="size">5</xsl:attribute>
                    <xsl:if test="chiba:data/@chiba:readonly='true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:attribute name="class">value</xsl:attribute>
                    <xsl:if test="$scripted='true'">
                        <xsl:attribute name="{$handler}">setXFormsValue(this);</xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                    <xsl:call-template name="build-items">
                        <!-- <xsl:with-param name="value" select="chiba:data/text()"/> -->
                        <xsl:with-param name="parent" select="$parent"/>
                    </xsl:call-template>
                </xsl:element>
                <!-- handle itemset prototype -->
                <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/chiba:data/xforms:item">
                        <xsl:call-template name="build-item-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
                <!-- create hidden parameter for deselection -->
                <input type="hidden" name="{$name}" value=""/>
            </xsl:when>
            <xsl:when test="@xforms:appearance='full' or @appearance='full'">
                <xsl:call-template name="build-checkboxes">
                    <xsl:with-param name="id" select="$id"/>
                    <xsl:with-param name="name" select="$name"/>
                    <xsl:with-param name="parent" select="$parent"/>
                </xsl:call-template>
                <!-- handle itemset prototype -->
                <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/chiba:data/xforms:item">
                        <xsl:call-template name="build-checkbox-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                            <xsl:with-param name="name" select="$name"/>
                            <xsl:with-param name="parent" select="$parent"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
                <!-- create hidden parameter for identification and deselection -->
                <input type="hidden" id="{$id}-value" name="{$name}" value=""/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:element name="select">
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat($id,'-value')"/>
                    </xsl:attribute>
                    <xsl:attribute name="name">
                        <xsl:value-of select="$name"/>
                    </xsl:attribute>
                   <xsl:attribute name="multiple">multiple</xsl:attribute>
                    <xsl:attribute name="size">3</xsl:attribute>
                    <xsl:if test="chiba:data/@chiba:readonly='true'">
                        <xsl:attribute name="disabled">disabled</xsl:attribute>
                    </xsl:if>
                    <xsl:attribute name="class">value</xsl:attribute>
                    <xsl:if test="$scripted='true'">
                        <xsl:attribute name="{$handler}">setXFormsValue(this);</xsl:attribute>
                    </xsl:if>
                    <xsl:apply-templates select="xforms:hint"/>
                    <xsl:call-template name="build-items">
                        <!-- <xsl:with-param name="value" select="chiba:data/text()"/> -->
                        <xsl:with-param name="parent" select="$parent"/>
                    </xsl:call-template>
                </xsl:element>
                <!-- handle itemset prototype -->
                <xsl:if test="$scripted='true' and not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/chiba:data/xforms:item">
                        <xsl:call-template name="build-item-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
                <!-- create hidden parameter for deselection -->
                <input type="hidden" name="{$name}" value=""/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- build textarea control -->
    <xsl:template name="textarea">
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>

		<xsl:variable name="html-mediatype-class">
			<xsl:choose>
				<xsl:when test="@xforms:mediatype='text/html' and $scripted='true'"><xsl:text> </xsl:text>mediatype-text-html</xsl:when>
				<xsl:otherwise/>
			</xsl:choose>
		</xsl:variable>


        <xsl:element name="textarea">
            <xsl:attribute name="id">
                <xsl:value-of select="concat($id,'-value')"/>
            </xsl:attribute>
            <xsl:attribute name="name">
                <xsl:value-of select="concat($data-prefix,$id)"/>
            </xsl:attribute>
            <xsl:if test="chiba:data/@chiba:readonly='true'">
                <xsl:attribute name="disabled">disabled</xsl:attribute>
            </xsl:if>
            <xsl:attribute name="rows">5</xsl:attribute>
            <xsl:attribute name="cols">30</xsl:attribute>
            <xsl:attribute name="class"><xsl:value-of select="concat('value',$html-mediatype-class)"/></xsl:attribute>
            <xsl:if test="$scripted='true'">
                <xsl:choose>
                    <xsl:when test="$incremental='true'">
                        <xsl:attribute name="onkeyup">setXFormsValue(this);</xsl:attribute>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="onchange">setXFormsValue(this);</xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:if>
            <xsl:apply-templates select="xforms:hint"/>
            <xsl:value-of select="chiba:data/text()"/>
        </xsl:element>
    </xsl:template>

    <!-- build submit -->
    <!-- todo: align with trigger template -->
    <xsl:template name="submit">
        <xsl:param name="classes"/>
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>

        <span id="{$id}" class="{$classes}">
            <xsl:element name="input">
                <xsl:attribute name="id">
                    <xsl:value-of select="concat($id,'-value')"/>
                </xsl:attribute>
                <xsl:choose>
                    <xsl:when test="$scripted='true'">
                        <xsl:attribute name="type">button</xsl:attribute>
                        <xsl:attribute name="onclick">activate(this);</xsl:attribute>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="type">submit</xsl:attribute>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:attribute name="name">
                    <xsl:value-of select="concat($trigger-prefix,$id)"/>
                </xsl:attribute>
                <xsl:attribute name="value">
                    <xsl:value-of select="xforms:label"/>
                </xsl:attribute>
                <xsl:if test="chiba:data/@chiba:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <!--            <xsl:if test="chiba:data/@chiba:enabled='false'">-->
                <!--                <xsl:attribute name="disabled">true</xsl:attribute>-->
                <!--            </xsl:if>-->
                <xsl:attribute name="class">value</xsl:attribute>
                <xsl:apply-templates select="xforms:hint"/>
            </xsl:element>
        </span>
    </xsl:template>

    <!-- build trigger -->
    <xsl:template name="trigger">
        <xsl:param name="classes"/>
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>

        <xsl:variable name="type">
            <xsl:call-template name="getType"/>
        </xsl:variable>

        <xsl:choose>
            <!-- minimal appearance only supported in scripted mode -->
            <xsl:when test="(@xforms:appearance='minimal' or @appearance='minimal') and $scripted='true'">
                <span id="{$id}" class="{$classes}">
                    <xsl:element name="a">
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:attribute name="id"><xsl:value-of select="concat($id,'-value')"/></xsl:attribute>
                        <xsl:attribute name="href">javascript:void(0);</xsl:attribute>
                        <xsl:if test="not(chiba:data/@chiba:readonly='true')">
                            <xsl:attribute name="onclick">javascript:activate(this);</xsl:attribute>
                        </xsl:if>
                        <xsl:apply-templates select="xforms:hint"/>
                        <xsl:apply-templates select="xforms:label"/>
                    </xsl:element>
                </span>
            </xsl:when>
            <xsl:otherwise>
                <span id="{$id}" class="{$classes}">
                    <xsl:element name="input">
                        <xsl:attribute name="id">
                            <xsl:value-of select="concat($id,'-value')"/>
                        </xsl:attribute>
                        <xsl:attribute name="name">
                            <xsl:value-of select="concat($trigger-prefix,$id)"/>
                        </xsl:attribute>
                        <xsl:attribute name="type">
                            <xsl:choose>
                                <xsl:when test="$scripted='true'">
                                    <xsl:value-of select="'button'"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="'submit'"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:attribute>
                        <xsl:attribute name="value">
                            <xsl:value-of select="xforms:label"/>
                        </xsl:attribute>
                        <xsl:attribute name="class">value</xsl:attribute>
                        <xsl:if test="chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:if test="@xforms:accesskey or @accesskey">
                            <xsl:attribute name="accesskey">
                                <xsl:value-of select="@xforms:accesskey | @accesskey"/>
                            </xsl:attribute>
                            <xsl:attribute name="title">
                                <xsl:value-of select="normalize-space(xforms:hint)"/> - KEY: [ALT]+
                                <xsl:value-of select="@xforms:accesskey | @accesskey"/>
                            </xsl:attribute>
                        </xsl:if>
                        <xsl:if test="$scripted='true'">
                            <xsl:attribute name="onclick">activate(this);</xsl:attribute>

                        </xsl:if>
                        <xsl:apply-templates select="xforms:hint"/>
<!--
                        <xsl:if test="contains(@xforms:src,'.gif') or contains(@xforms:src,'.jpg') or contains(@xforms:src,'.png')">
                            <img alt="{xforms:label}" src="{@xforms:src}" id="{@id}-label"/>
                        </xsl:if>
-->
                    </xsl:element>
                    <xsl:if test="$repeat-id and $scripted = 'true'">
                        <script type="text/javascript">
                            dojo.event.connect("before",dojo.byId("<xsl:value-of select="concat($id,'-value')"/>"),"onclick","setRepeatIndex");
                        </script>
                    </xsl:if>

                </span>
            </xsl:otherwise>
        </xsl:choose>

    </xsl:template>

    <!-- build upload control -->
    <xsl:template name="upload">
        <!-- the stylesheet using this template has to take care, that form enctype is set to 'multipart/form-data' -->
        <xsl:variable name="repeat-id" select="ancestor::*[local-name(.)='repeat'][1]/@id"/>
        <xsl:variable name="pos" select="position()"/>
        <xsl:variable name="id" select="@id"/>

        <!-- todo: support incremental -->
        <xsl:variable name="incremental" select="@xforms:incremental | @incremental"/>

        <xsl:element name="input">
            <xsl:attribute name="id">
                <xsl:value-of select="concat($id,'-value')"/>
            </xsl:attribute>
            <xsl:attribute name="name">
                <xsl:value-of select="concat($data-prefix,$id)"/>
            </xsl:attribute>
            <xsl:attribute name="type">file</xsl:attribute>
            <xsl:attribute name="value"></xsl:attribute>
            <xsl:if test="chiba:data/@chiba:readonly='true'">
                <xsl:attribute name="disabled">disabled</xsl:attribute>
            </xsl:if>
            <xsl:attribute name="class">value</xsl:attribute>
            <!-- Content types accepted, from mediatype xforms:upload attribute
            to accept input attribute -->

            <xsl:attribute name="accept">
                <xsl:value-of select="translate(normalize-space(@mediatype),' ',',')"/>
            </xsl:attribute>

            <xsl:if test="$scripted='true'">
                <!--<xsl:attribute name="onchange">submitFile(this);</xsl:attribute>-->
                <xsl:attribute name="dojoType">XFUpload</xsl:attribute>
                <xsl:attribute name="xfreadonly"><xsl:value-of select="chiba:data/@chiba:readonly"/></xsl:attribute>                
            </xsl:if>
            <!--<xsl:apply-templates select="xforms:hint"/>-->
        </xsl:element>

        <xsl:if test="$scripted='true'">
            <script type="text/javascript">
                dojo.require("chiba.Upload");
            </script>

            <iframe id="UploadTarget" name="UploadTarget" src="" style="width:0px;height:0px;border:0"></iframe>
            <div class="progressbar" style="display:none;" id="{$id}-progress"><div class="border"><div id="{$id}-progress-bg" class="background"></div></div></div>
        </xsl:if>
        <xsl:if test="xforms:filename">
            <input type="hidden" id="{xforms:filename/@id}" value="{xforms:filename/chiba:data}"/>
        </xsl:if>
        <xsl:if test="@chiba:destination">
            <!-- create hidden parameter for destination -->
            <input type="hidden" id="{$id}-destination" value="{@chiba:destination}"/>
        </xsl:if>
    </xsl:template>


    <!-- ######################################################################################################## -->
    <!-- ########################################## HELPER TEMPLATES FOR SELECT, SELECT1 ######################## -->
    <!-- ######################################################################################################## -->

    <xsl:template name="build-items">
        <xsl:param name="parent"/>

        <!-- todo: refactor to handle xforms:choice / xforms:itemset by matching -->
        <optgroup id="{xforms:itemset/@id}" label="">
            <!-- add an empty item, cause otherwise deselection is not possible -->
            <option value="">
                <xsl:if test="string-length($parent/chiba:data/text()) = 0">
                    <xsl:attribute name="selected">selected</xsl:attribute>
                </xsl:if>
            </option>

            <!-- handle items, items in choices, and items in itemsets, but neither of these in chiba:data  -->
            <xsl:for-each select="$parent/xforms:item|$parent/xforms:choices/xforms:item|$parent/xforms:itemset/xforms:item">
                <option id="{@id}-value" class="selector-item">
                    <xsl:choose>
                        <xsl:when test="xforms:copy">
                            <xsl:attribute name="value" select="xforms:copy/@id"/>
                            <xsl:attribute name="title" select="xforms:copy/@id"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:attribute name="value" select="xforms:value"/>
                            <xsl:attribute name="title" select="xforms:hint"/>
                        </xsl:otherwise>
                    </xsl:choose>
                    <xsl:if test="@selected='true'">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                    </xsl:if>
                    <xsl:value-of select="xforms:label"/>
                </option>
            </xsl:for-each>
        </optgroup>
    </xsl:template>

    <xsl:template name="build-item-prototype">
        <xsl:param name="item-id"/>
        <xsl:param name="itemset-id"/>

        <select id="{$itemset-id}-prototype" class="selector-prototype">
            <option id="{$item-id}-value" class="selector-prototype">
                <xsl:choose>
                    <xsl:when test="xforms:copy">
                        <xsl:attribute name="value" select="xforms:copy/@id"/>
                        <xsl:attribute name="title" select="xforms:copy/@id"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="value" select="xforms:value"/>
                        <xsl:attribute name="title" select="xforms:hint"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="selected">selected</xsl:attribute>
                </xsl:if>
                <xsl:value-of select="xforms:label"/>
            </option>
        </select>
    </xsl:template>

    <xsl:template name="build-checkboxes">
        <xsl:param name="id"/>
        <xsl:param name="name"/>
        <xsl:param name="parent"/>

        <!-- todo: refactor to handle xforms:choice / xforms:itemset by matching -->
        <span id="{xforms:itemset/@id}">
            <!-- handle items, items in choices, and items in itemsets, but neither of these in chiba:data  -->
            <xsl:for-each select="$parent/xforms:item|$parent/xforms:choices/xforms:item|$parent/xforms:itemset/xforms:item">
                <span id="{@id}" class="selector-item">
                    <input id="{@id}-value" class="value" type="checkbox" name="{$name}">
                        <xsl:choose>
                            <xsl:when test="xforms:copy">
                                <xsl:attribute name="value" select="xforms:copy/@id"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="value" select="xforms:value"/>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:choose>
                            <xsl:when test="xforms:hint">
                                <xsl:apply-templates select="xforms:hint"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:apply-templates select="$parent/xforms:hint"/>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:if test="@selected='true'">
                            <xsl:attribute name="checked">checked</xsl:attribute>
                        </xsl:if>
                        <xsl:if test="$scripted='true'">
                            <xsl:attribute name="onclick">setXFormsValue(this);</xsl:attribute>
                            <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                        </xsl:if>
                    </input>
                    <span id="{@id}-label" class="label">
                        <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:apply-templates select="xforms:label"/>
                    </span>
                </span>
            </xsl:for-each>
        </span>
    </xsl:template>

    <xsl:template name="build-checkbox-prototype">
        <xsl:param name="item-id"/>
        <xsl:param name="itemset-id"/>
        <xsl:param name="name"/>
        <xsl:param name="parent"/>

        <xsl:variable name="title">
            <xsl:choose>
                <xsl:when test="xforms:hint">
                    <xsl:value-of select="xforms:hint"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$parent/xforms:hint"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <span id="{$itemset-id}-prototype" class="selector-prototype">
            <input id="{$item-id}-value" class="value" type="checkbox" name="{$name}">
                <xsl:choose>
                    <xsl:when test="xforms:copy">
                        <xsl:attribute name="value" select="xforms:copy/@id"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="value" select="xforms:value"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="checked">checked</xsl:attribute>
                </xsl:if>
                <xsl:if test="$scripted='true'">
                    <xsl:attribute name="onclick">setXFormsValue(this);</xsl:attribute>
                    <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                </xsl:if>
                <xsl:choose>
                    <xsl:when test="xforms:hint">
                        <xsl:value-of select="xforms:hint"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$parent/xforms:hint"/>
                    </xsl:otherwise>
                </xsl:choose>
            </input>
            <span id="{@id}-label" class="label">
                <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="xforms:label"/>
            </span>
        </span>
    </xsl:template>

    <!-- overwrite/change this template, if you don't like the way labels are rendered for checkboxes -->
    <xsl:template name="build-radiobuttons">
        <xsl:param name="id"/>
        <xsl:param name="name"/>
        <xsl:param name="parent"/>

        <!-- todo: refactor to handle xforms:choice / xforms:itemset by matching -->
        <span id="{xforms:itemset/@id}">
            <!-- handle items, items in choices, and items in itemsets, but neither of these in chiba:data  -->
            <xsl:for-each select="$parent/xforms:item|$parent/xforms:choices/xforms:item|$parent/xforms:itemset/xforms:item">
                <span id="{@id}" class="selector-item">
                    <input id="{@id}-value" class="value" type="radio" name="{$name}">
                        <xsl:choose>
                            <xsl:when test="xforms:copy">
                                <xsl:attribute name="value" select="xforms:copy/@id"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="value" select="xforms:value"/>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:choose>
                            <xsl:when test="xforms:hint">
                                <xsl:apply-templates select="xforms:hint"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:apply-templates select="$parent/xforms:hint"/>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:if test="@selected='true'">
                            <xsl:attribute name="checked">checked</xsl:attribute>
                        </xsl:if>
                        <xsl:if test="$scripted='true'">
                            <xsl:attribute name="onclick">setXFormsValue(this);</xsl:attribute>
                            <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                        </xsl:if>
                    </input>
                    <span id="{@id}-label" class="label">
                        <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                            <xsl:attribute name="disabled">disabled</xsl:attribute>
                        </xsl:if>
                        <xsl:apply-templates select="xforms:label"/>
                    </span>
                </span>
            </xsl:for-each>
        </span>
    </xsl:template>
    
    <xsl:template name="build-radiobutton-prototype">
        <xsl:param name="item-id"/>
        <xsl:param name="itemset-id"/>
        <xsl:param name="name"/>
        <xsl:param name="parent"/>

        <span id="{$itemset-id}-prototype" class="selector-prototype">
            <input id="{$item-id}-value" class="value" type="radio" name="{$name}">
                <xsl:choose>
                    <xsl:when test="xforms:copy">
                        <xsl:attribute name="value" select="xforms:copy/@id"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="value" select="xforms:value"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:choose>
                    <xsl:when test="xforms:hint">
                        <xsl:apply-templates select="xforms:hint"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="$parent/xforms:hint"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="checked">checked</xsl:attribute>
                </xsl:if>
                <xsl:if test="$scripted='true'">
                    <xsl:attribute name="onclick">setXFormsValue(this);</xsl:attribute>
                    <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                </xsl:if>
            </input>
            <span id="{@id}-label" class="label">
                <xsl:if test="$parent/chiba:data/@chiba:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="xforms:label"/>
            </span>
        </span>
    </xsl:template>


</xsl:stylesheet>
