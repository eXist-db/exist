<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:bf="http://betterform.sourceforge.net/xforms"
    exclude-result-prefixes="xhtml xf bf">

    <xsl:import href="common.xsl"/>
    <xsl:include href="ui.xsl"/>
    <xsl:include href="html-form-controls.xsl"/>

    <!-- ####################################################################################################### -->
    <!-- This stylesheet transcodes a XTHML2/XForms input document to HTML 4.0.                                  -->
    <!-- It serves as a reference for customized stylesheets which may import it to overwrite specific templates -->
    <!-- or completely replace it.                                                                               -->
    <!-- This is the most basic transformator for HTML browser clients and assumes support for HTML 4 tagset     -->
    <!-- but does NOT rely on javascript.                                                                        -->
    <!-- author: joern turner                                                                                    -->
    <!-- ####################################################################################################### -->

    <!-- ############################################ PARAMS ################################################### -->
    <xsl:param name="contextroot" select="''"/>

    <xsl:param name="sessionKey" select="''"/>

    <!-- ### this url will be used to build the form action attribute ### -->
    <!--<xsl:param name="action-url" select="''"/>-->

    <xsl:param name="form-id" select="'betterform'"/>
    <xsl:param name="form-name" select="//xhtml:title"/>
    <xsl:param name="debug-enabled" select="'true'"/>

    <!-- ### specifies the parameter prefix for repeat selectors ### -->
    <xsl:param name="selector-prefix" select="'s_'"/>

    <xsl:param name="scripted" select="'false'"/>

    <xsl:param name="CSS-managed-alerts" select="'true'"/>

    <!-- path to core CSS file -->
    <xsl:param name="CSSPath" select="''"/>

     <!-- ############################################ VARIABLES ################################################ -->
    <!-- ### checks, whether this form uses uploads. Used to set form enctype attribute ### -->
    <xsl:variable name="uses-upload" select="boolean(//*/xf:upload)"/>

    <!-- ### checks, whether this form makes use of date types and needs datecontrol support ### -->
    <!-- this is only an interims solution until Schema type and base type handling has been clarified -->
    <xsl:variable name="uses-dates">
        <xsl:choose>
            <xsl:when test="boolean(//bf:data/bf:type='date')">true()</xsl:when>
            <xsl:when test="boolean(//bf:data/bf:type='dateTime')">true()</xsl:when>
            <xsl:when test="boolean(substring-after(//bf:data/bf:type,':') ='date')">true()</xsl:when>
            <xsl:when test="boolean(substring-after(//bf:data/bf:type,':') ='dateTime')">true()</xsl:when>
            <xsl:otherwise>false()</xsl:otherwise>
        </xsl:choose>
    </xsl:variable>

	<!-- ### checks, whether this form makes use of <textarea xf:mediatype='text/html'/> ### -->
	<xsl:variable name="uses-html-textarea" select="boolean(//xf:textarea[@mediatype='text/html'])"/>

    <!-- ### the CSS stylesheet to use ### -->
    <xsl:variable name="default-css" select="concat($contextroot,$CSSPath,'xforms.css')"/>
    <xsl:variable name="betterform-css"  select="concat($contextroot,$CSSPath,'betterform.css')"/>

    <xsl:variable name="default-hint-appearance" select="'bubble'"/>

    <xsl:output method="html" version="4.01" encoding="UTF-8" indent="yes"
                doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN"
                doctype-system="http://www.w3.org/TR/html4/loose.dtd"/>
    <!-- ### transcodes the XHMTL namespaced elements to HTML ### -->
    <!--<xsl:namespace-alias stylesheet-prefix="xhtml" result-prefix="#default"/>-->

    <xsl:preserve-space elements="*"/>
    <xsl:strip-space elements="xf:action"/>

    <!-- ####################################################################################################### -->
    <!-- ##################################### TEMPLATES ####################################################### -->
    <!-- ####################################################################################################### -->

    <xsl:template match="xhtml:head">
        <head>
            <!-- copy all meta tags except 'contenttype' -->
            <xsl:call-template name="getMeta" />

            <title>
                <xsl:value-of select="$form-name"/>
            </title>

            <!-- copy base if present -->
            <xsl:if test="xhtml:base">
                <base>
                    <xsl:attribute name="href">
                        <xsl:value-of select="xhtml:base/@href"/>
                    </xsl:attribute>
                </base>
            </xsl:if>

            <!-- include betterForm default stylesheet -->
            <link rel="stylesheet" type="text/css" href="{$default-css}"/>
            <link rel="stylesheet" type="text/css" href="{$betterform-css}"/>

            <!-- copy user-defined stylesheets and inline styles -->
            <xsl:call-template name="getLinkAndStyle"/>

        </head>
    </xsl:template>

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
                <a href="jsp/forms.jsp">
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

    <xsl:template match="xf:group[not(ancestor::xf:*)][1] | xf:repeat[not(ancestor::xf:*)][1] | xf:switch[not(ancestor::xf:*)][1]" mode="inline">
        <xsl:element name="form">
            <xsl:attribute name="name">
                <xsl:value-of select="$form-id"/>
            </xsl:attribute>

            <xsl:attribute name="action">
                    <xsl:value-of select="concat($action-url,'?sessionKey=',$sessionKey)"/>
            </xsl:attribute>
            <xsl:attribute name="onSubmit">return submitFunction();</xsl:attribute>

            <xsl:attribute name="method">POST</xsl:attribute>
            <xsl:attribute name="enctype">application/x-www-form-urlencoded</xsl:attribute>
            <xsl:if test="$uses-upload">
                <xsl:attribute name="enctype">multipart/form-data</xsl:attribute>
            </xsl:if>
            <input type="hidden" id="bfSessionKey" name="sessionKey" value="{$sessionKey}"/>
            <input type="submit" value="refresh page" class="caRefreshButton"/>

            <xsl:apply-templates select="."/>
            <input type="submit" value="refresh page" class="caRefreshButton"/>
        </xsl:element>
    </xsl:template>

    <!-- this template is called when there's no single outermost XForms element meaning there are
     several blocks of XForms markup scattered in the body of the host document. -->
    <xsl:template name="createForm">
        <xsl:element name="form">
            <xsl:attribute name="name">
                <xsl:value-of select="$form-id"/>
            </xsl:attribute>

            <xsl:attribute name="action">
                <xsl:value-of select="concat($action-url,'?sessionKey=',$sessionKey)"/>
            </xsl:attribute>
            <xsl:attribute name="onSubmit">return submitFunction();</xsl:attribute>
            <xsl:attribute name="method">POST</xsl:attribute>
            <xsl:attribute name="enctype">application/x-www-form-urlencoded</xsl:attribute>
            <xsl:if test="$uses-upload">
                <xsl:attribute name="enctype">multipart/form-data</xsl:attribute>
            </xsl:if>
            <input type="hidden" id="bfSessionKey" name="sessionKey" value="{$sessionKey}"/>

            <xsl:for-each select="*">
                <xsl:apply-templates select="."/>
            </xsl:for-each>

        </xsl:element>
    </xsl:template>

    <!-- ######################################################################################################## -->
    <!-- #####################################  CONTROLS ######################################################## -->
    <!-- ######################################################################################################## -->

    <xsl:template match="xf:input|xf:range|xf:secret|xf:select|xf:select1|xf:textarea|xf:upload">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:variable name="label-classes">
            <xsl:call-template name="assemble-label-classes"/>
        </xsl:variable>

        <div id="{$id}" class="{$control-classes}">
			<xsl:if test="@style">
				<xsl:attribute name="style"><xsl:value-of select="@style"/></xsl:attribute>
			</xsl:if>
            <label for="{$id}-value" id="{$id}-label" class="{$label-classes}"><xsl:apply-templates select="xf:label"/></label>
            <xsl:call-template name="buildControl"/>
        </div>
    </xsl:template>

    <!-- cause outputs can be inline they should not use a block element wrapper -->
    <xsl:template match="xf:output">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:variable name="label-classes">
            <xsl:call-template name="assemble-label-classes"/>
        </xsl:variable>

        <span id="{$id}" class="{$control-classes}">
			<label for="{$id}-value" id="{$id}-label" class="{$label-classes}"><xsl:apply-templates select="xf:label"/></label>
            <xsl:call-template name="buildControl"/>
        </span>
    </xsl:template>

    <xsl:template match="xf:output[string-length(xf:label)!=0]">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:variable name="label-classes">
            <xsl:call-template name="assemble-label-classes"/>
        </xsl:variable>

        <div id="{$id}" class="{$control-classes}">
			<label for="{$id}-value" id="{$id}-label" class="{$label-classes}"><xsl:apply-templates select="xf:label"/></label>
            <xsl:call-template name="buildControl"/>
        </div>
    </xsl:template>

    <xsl:template match="xf:trigger|xf:submit">
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>

        <xsl:call-template name="trigger">
            <xsl:with-param name="classes" select="$control-classes"/>
        </xsl:call-template>
    </xsl:template>

    <!-- ######################################################################################################## -->
    <!-- #####################################  CHILD ELEMENTS ################################################## -->
    <!-- ######################################################################################################## -->

    <!-- ### handle label ### -->
    <xsl:template match="xf:label">
        <!-- match all inline markup and content -->
        <xsl:apply-templates/>

        <!-- check for requiredness -->
        <xsl:if test="../bf:data/@bf:required='true'"><span class="required-symbol">*</span></xsl:if>
    </xsl:template>

    <!-- ### handle hint ### -->
    <xsl:template match="xf:hint">
        <xsl:attribute name="title">
            <xsl:value-of select="normalize-space(.)"/>
        </xsl:attribute>
    </xsl:template>

    <!-- ### handle help ### -->
    <!-- ### only reacts on help elements with a 'src' attribute and interprets it as html href ### -->
    <xsl:template match="xf:help">

        <!-- help in repeats not supported yet due to a cloning problem with help elements -->
        <xsl:if test="string-length(.) != 0 and not(ancestor::xf:repeat)">
                <xsl:element name="a">
                    <xsl:attribute name="href">#</xsl:attribute>
                    <xsl:attribute name="style">text-decoration:none;</xsl:attribute>
                    <xsl:attribute name="class">help-icon</xsl:attribute>
                    <img src="{concat($contextroot,'/resources/images/help_icon.gif')}" class="help-symbol" alt="?" border="0"/>
                </xsl:element>
                <!--<span id="{../@id}-helptext" class="help-text" style="position:absolute;display:none;width:250px;border:thin solid gray 1px;background:lightgrey;padding:5px;">-->
                <span id="{../@id}-helptext" class="help-text">
                    <xsl:apply-templates/>
                </span>
        </xsl:if>

    </xsl:template>

    <!-- ### handle explicitely enabled alert ### -->
    <!--    <xsl:template match="xf:alert[../bf:data/@bf:valid='false']">-->
    <xsl:template match="xf:alert">
        <xsl:choose>
            <xsl:when test="$CSS-managed-alerts='true'">
                <span id="{../@id}-alert" class="alert">
                    <xsl:value-of select="."/>
                </span>
            </xsl:when>
            <xsl:otherwise>
                <xsl:if test="../bf:data/@bf:valid='false'">
                    <span id="{../@id}-alert" class="alert">
                        <xsl:value-of select="."/>
                    </span>
                </xsl:if>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ####################################################################################################### -->
    <!-- #####################################  HELPER TEMPLATES '############################################## -->
    <!-- ####################################################################################################### -->

    <xsl:template name="buildControl">
        <xsl:choose>
            <xsl:when test="local-name()='input'">
                <xsl:call-template name="input"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='output'">
                <xsl:call-template name="output"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='range'">
                <xsl:call-template name="range"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='secret'">
                <xsl:call-template name="secret"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='select'">
                <xsl:call-template name="select"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='select1'">
                <xsl:call-template name="select1"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='submit'">
                <xsl:call-template name="submit"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='trigger'">
                <xsl:call-template name="trigger"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='textarea'">
                <xsl:call-template name="textarea"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='upload'">
                <xsl:call-template name="upload"/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='repeat'">
                <xsl:apply-templates select="."/>
            </xsl:when>
            <xsl:when test="local-name()='group'">
                <xsl:apply-templates select="."/>
                <xsl:apply-templates select="xf:help"/>
                <xsl:apply-templates select="xf:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='switch'">
                <xsl:apply-templates select="."/>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>