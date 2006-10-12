<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xpath-default-namespace="http://www.w3.org/1999/xhtml"
    xmlns:chiba="http://chiba.sourceforge.net/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    exclude-result-prefixes="ev xforms chiba">
    
    <!-- ############################################ INCLUDES ################################################### -->
    
    <xsl:include href="xhtml-form-controls.xsl"/>
    <xsl:include href="xhtml-ui.xsl"/>
    
    
    <!-- ############################################ PARAMS ################################################### -->
    
    <xsl:param name="contextroot" select="''"/>
    
    <xsl:param name="sessionKey" select="''"/>
    
    <!-- ### this url will be used to build the form action attribute ### -->
    <xsl:param name="action-url" select="'http://localhost:8080/chiba-1.0.0/XFormsServlet'"/>
    
    
    <xsl:param name="form-id" select="'chibaform'"/>
    <xsl:param name="form-name" select="//title"/>
    <xsl:param name="debug-enabled" select="'true'"/>
    
    <!-- ### specifies the parameter prefix for repeat selectors ### -->
    <xsl:param name="selector-prefix" select="'s_'"/>
    
    <!-- ### contains the full user-agent string as received from the servlet ### -->
    <xsl:param name="user-agent" select="'default'"/>
    
    <!-- ### this parameter is used when the Adapter wants to specify the CSS to use ### -->
    <xsl:param name="css-file" select="''"/>
    
    <xsl:param name="scripted" select="'false'"/>
    
    <xsl:param name="CSS-managed-alerts" select="'true'"/>
    
    <!--- path to javascript files -->
    <xsl:param name="scriptPath" select="''"/>
    
    <!-- path to core CSS file -->
    <xsl:param name="CSSPath" select="''"/>
    
    
    <!-- ############################################ VARIABLES ################################################ -->
    
    <!-- ### checks, whether this form uses uploads. Used to set form enctype attribute ### -->
    <xsl:variable name="uses-upload" select="boolean(//*/xforms:upload)"/>
    
    <!-- ### checks, whether this form makes use of date types and needs datecontrol support ### -->
    <!-- this is only an interims solution until Schema type and base type handling has been clarified -->    
    <xsl:variable name="uses-dates">
        <xsl:choose>
            <xsl:when test="boolean(//chiba:data/chiba:type='date')">true()</xsl:when>
            <xsl:when test="boolean(//chiba:data/chiba:type='dateTime')">true()</xsl:when>
            <xsl:when test="boolean(substring-after(//chiba:data/chiba:type,':') ='date')">true()</xsl:when>
            <xsl:when test="boolean(substring-after(//chiba:data/chiba:type,':') ='dateTime')">true()</xsl:when>
            <xsl:otherwise>false()</xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    
    <!-- ### checks, whether this form makes use of <textarea xforms:mediatype='text/html'/> ### -->
    <xsl:variable name="uses-html-textarea" select="boolean(//xforms:textarea[@xforms:mediatype='text/html'])"/>
    
    <!-- ### the CSS stylesheet to use ### -->
    <xsl:variable name="default-css" select="concat($contextroot,$CSSPath,'xforms.css')"/>
    
    <xsl:variable name="default-hint-appearance" select="'bubble'"/>
    
    <!-- ############################################ OUTPUT ################################################### -->
    
    <!-- <xsl:output method="xhtml" version="1.0" encoding="UTF-8" indent="yes" doctype-public="-//W3C//DTD XHTML 1.1//EN" doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd" media-type="text/xhtml" omit-xml-declaration="no"/> -->
    <xsl:output method="xhtml" version="1.0" encoding="UTF-8" indent="yes" omit-xml-declaration="no" media-type="text/xhtml" doctype-public="-//W3C//DTD XHTML 1.1//EN" doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"/>
    
    <xsl:preserve-space elements="*"/>
    <xsl:strip-space elements="xforms:action"/>
    
    <!-- ############################################ TEMPLATES ################################################### -->
    
    <xsl:template match="/">
            <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="head">
        <head>
            
            <!-- copy all meta tags except 'contenttype' -->
            <xsl:call-template name="getMeta" />

            <!-- copy title if present -->
            <xsl:copy-of select="title" copy-namespaces="no"/>
            
            <!-- copy base if present -->
            <xsl:copy-of select="base" copy-namespaces="no"/>
            
            <!-- get the Chiba CSS -->
            <xsl:call-template name="getChibaCSS"/>
            
            <!-- get any other CSS/link -->
            <xsl:copy-of select="link" copy-namespaces="no"/>
            <!-- if we are using scripted chiba -->
            <xsl:if test="$scripted='true' and $uses-dates">
                <link type="text/css" rel="stylesheet" href="{concat($contextroot,$scriptPath,'jscalendar/calendar-green.css')}"></link>
            </xsl:if>
            <xsl:if test="$scripted='true'and $uses-html-textarea">
                <!-- Insert here a custom CSS for textarea mediatype='text/html'-->
            </xsl:if>
            <xsl:copy-of select="style" copy-namespaces="no"/>
            
            <!-- if we are using scripted chiba, include needed javascript files -->
           <xsl:if test="$scripted='true'">
               <xsl:call-template name="getChibaScript"/>
           </xsl:if> 

            <!-- copy scripts if present -->
            <xsl:copy-of select="script" copy-namespaces="no"/>
            
        </head>
    </xsl:template>
    
    <!-- copy unmatched mixed markup, comments, whitespace, and text -->
    <!-- ### copy elements from the xhtml2 namespace to html (without any namespace) by re-creating the     ### -->
    <!-- ### elements. Other Elements are just copied with their original namespaces.                       ### -->
    <xsl:template match="*|@*|text()" name="handle-foreign-elements">
        <xsl:choose>
            <xsl:when test="namespace-uri(.)='http://www.w3.org/1999/xhtml'">
                <!--
                 <xsl:element name="{local-name(.)}" namespace="">
                -->
                <xsl:element name="{local-name(.)}">
                    <xsl:apply-templates select="*|@*|text()"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="*|@*|text()"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="*|@*|text()" mode="inline">
        <xsl:choose>
            <xsl:when test="namespace-uri(.)='http://www.w3.org/1999/xhtml'">
                <!--
                <xsl:element name="{local-name(.)}" namespace="">
                -->
                <xsl:element name="{local-name(.)}">
                    <xsl:apply-templates select="*|@*|text()" mode="inline"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="*|@*|text()" mode="inline"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template match="html">
        <html>
            <xsl:apply-templates/>
        </html>
    </xsl:template>
    
    <xsl:template match="body">
        <body>
            <xsl:copy-of select="@*"/>
            <div id="loading">
                <img src="forms/images/indicator.gif" class="disabled" id="indicator" alt="loading" />
            </div>
            
            <xsl:variable name="outermostNodeset" select=".//xforms:*[not(xforms:model)][not(ancestor::xforms:*)]"/>
            
            <!-- detect how many outermost XForms elements we have in the body -->
            <xsl:choose>
                <xsl:when test="count($outermostNodeset) = 1">
                    <!-- match any body content and defer creation of form tag for XForms processing.
                        This option allows to mix HTML forms with XForms markup. -->
                    <xsl:apply-templates mode="inline"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- in case there are multiple outermost xforms elements we are forced to create
                        the form tag for the XForms processing.-->
                    <xsl:call-template name="createForm"/>
                </xsl:otherwise>
            </xsl:choose>
            
            <!-- legend -->
            <div id="legend">
                <span id="required-msg">
                    <span style="color:#A42322;">*</span> - required</span> |
                <b>?</b> - help
            </div>
            
            <!-- chiba logo -->
            
            <!--
            <div id="chiba-logo">
                <a href="jsp/forms.jsp">
                    <img src="forms/images/poweredby_sw.gif" style="border:none;" alt="powered by Chiba"/>
                </a>
            </div>
            -->
            
            <!-- copyright -->
            <!--
            <div id="copyright">
            -->
                <!--  &#169; -->
            <!--
                <xsl:text disable-output-escaping="yes">2001-2005 Chiba Project</xsl:text>
             </div>
             -->
            
            <!-- debug -->
            <xsl:if test="$scripted='true' and $debug-enabled='true'">
                <script type="text/javascript">
                    dojo.require("dojo.widget.DebugConsole");
                </script>
                
                <div dojoType="DebugConsole"
                    style="position:absolute;right:0px;top:0px;width:600px;height:400px;"
                    title="DEBUG"
                    hasShadow="true"
                    displayCloseAction="true"></div>
            </xsl:if>
            
        </body>
    </xsl:template>
    
    <!--
        match outermost group of XForms markup. An outermost group is necessary to allow standard HTML forms
        to coexist with XForms markup and still produce non-nested form tags in the output.
    -->
    <xsl:template match="xforms:group[not(ancestor::xforms:*)][1] | xforms:repeat[not(ancestor::xforms:*)][1] | xforms:switch[not(ancestor::xforms:*)][1]" mode="inline">
        <xsl:element name="form">
            <xsl:attribute name="id">
                <xsl:value-of select="$form-id"/>
            </xsl:attribute>
            <xsl:attribute name="action">
                <xsl:choose>
                    <xsl:when test="$uses-upload">
                        <xsl:choose>
                            <xsl:when test="contains($action-url, '?')">
                                <xsl:value-of select="concat($action-url,'&amp;sessionKey=',$sessionKey)"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="concat($action-url,'?sessionKey=',$sessionKey)"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$action-url"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:attribute name="method">post</xsl:attribute>
            <xsl:attribute name="enctype">application/x-www-form-urlencoded</xsl:attribute>
            <xsl:if test="$uses-upload">
                <xsl:attribute name="enctype">multipart/form-data</xsl:attribute>
            </xsl:if>
            
            <div id="chibaformdiv">
                <input type="hidden" id="chibaSessionKey" name="sessionKey" value="{$sessionKey}"/>
                
                <!--
                <xsl:if test="$scripted != 'true'">
                    <input type="submit" value="refresh page" class="refresh-button"/>
                </xsl:if>
                -->
                
                <xsl:apply-templates select="."/>
                
                <xsl:if test="$scripted != 'true'">
                    <input type="submit" value="refresh page" class="refresh-button"/>
                </xsl:if>
                
            </div>
            
        </xsl:element>
    </xsl:template>

    <xsl:template match="span">
        <span>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates/>
        </span>
    </xsl:template>
    
    <!-- ### skip chiba:data elements ### -->
    <xsl:template match="chiba:data"/>
    
    <!-- ### skip model section ### -->
    <xsl:template match="xforms:model"/>
    <xsl:template match="xforms:model" mode="inline"/>
    
    
    <!-- ######################################################################################################## -->
    <!-- #####################################  CONTROLS ######################################################## -->
    <!-- ######################################################################################################## -->
    
    <xsl:template match="xforms:input|xforms:range|xforms:secret|xforms:select|xforms:select1|xforms:textarea|xforms:upload">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:variable name="label-classes">
            <xsl:call-template name="assemble-label-classes"/>
        </xsl:variable>
        
        <div id="{$id}" class="{$control-classes}">
            <label for="{$id}-value" id="{$id}-label" class="{$label-classes}"><xsl:apply-templates select="xforms:label"/></label>
            <xsl:call-template name="buildControl"/>
        </div>
    </xsl:template>
    
    <!-- cause outputs can be inline they should not use a block element wrapper -->
    <xsl:template match="xforms:output">
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes"/>
        </xsl:variable>
        <xsl:variable name="label-classes">
            <xsl:call-template name="assemble-label-classes"/>
        </xsl:variable>
        
        <span id="{$id}" class="{$control-classes}">
            <label for="{$id}-value" id="{$id}-label" class="{$label-classes}"><xsl:apply-templates select="xforms:label"/></label>
            <xsl:call-template name="buildControl"/>
        </span>
    </xsl:template>
    
    <xsl:template match="xforms:trigger|xforms:submit">
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
    <xsl:template match="xforms:label">
        <!-- match all inline markup and content -->
        <xsl:apply-templates/>
        
        <!-- check for requiredness -->
        <xsl:if test="../chiba:data/@chiba:required='true'"><span class="required-symbol">*</span></xsl:if>
    </xsl:template>
    
    <!-- ### handle hint ### -->
    <xsl:template match="xforms:hint">
        <xsl:attribute name="title">
            <xsl:value-of select="normalize-space(.)"/>
        </xsl:attribute>
    </xsl:template>
    
    <!-- ### handle help ### -->
    <!-- ### only reacts on help elements with a 'src' attribute and interprets it as html href ### -->
    <xsl:template match="xforms:help">
        
        <!-- help in repeats not supported yet due to a cloning problem with help elements -->
        <xsl:if test="string-length(.) != 0 and not(ancestor::xforms:repeat)">
            <xsl:element name="a">
                <xsl:attribute name="onclick">javascript:document.getElementById('<xsl:value-of select="../@id"/>' + '-helptext').style.display='block';</xsl:attribute>
                <xsl:attribute name="onblur">javascript:document.getElementById('<xsl:value-of select="../@id"/>' + '-helptext').style.display='none';</xsl:attribute>
                <xsl:attribute name="href">javascript:void(0);</xsl:attribute>
                <xsl:attribute name="style">text-decoration:none;</xsl:attribute>
                <img src="forms/images/help_icon.gif" class="help-symbol" alt="?" border="0"/>
            </xsl:element>
            <!--<span id="{../@id}-helptext" class="help-text" style="position:absolute;display:none;width:250px;border:thin solid gray 1px;background:lightgrey;padding:5px;">-->
            <span id="{../@id}-helptext" class="help-text">
                <xsl:apply-templates/>
            </span>
        </xsl:if>
        
    </xsl:template>
    
    <!-- ### handle explicitely enabled alert ### -->
    <!--    <xsl:template match="xforms:alert[../chiba:data/@chiba:valid='false']">-->
    <xsl:template match="xforms:alert">
        <xsl:choose>
            <xsl:when test="$CSS-managed-alerts='true'">
                <span id="{../@id}-alert" class="alert">
                    <xsl:value-of select="."/>
                </span>
            </xsl:when>
            <xsl:otherwise>
                <xsl:if test="../chiba:data/@chiba:valid='false'">
                    <span id="{../@id}-alert" class="alert">
                        <xsl:value-of select="."/>
                    </span>
                </xsl:if>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- ### handle extensions ### -->
    <xsl:template match="xforms:extension">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="chiba:selector">
    </xsl:template>
    
    
    <!-- ########################## ACTIONS ####################################################### -->
    <!-- these templates serve no real purpose here but are shown for reference what may be over-   -->
    <!-- written by customized stylesheets importing this one. -->
    <!-- ########################## ACTIONS ####################################################### -->
    
    <xsl:template match="xforms:action"/>
    <xsl:template match="xforms:dispatch"/>
    <xsl:template match="xforms:rebuild"/>
    <xsl:template match="xforms:recalculate"/>
    <xsl:template match="xforms:revalidate"/>
    <xsl:template match="xforms:refresh"/>
    <xsl:template match="xforms:setfocus"/>
    <xsl:template match="xforms:load"/>
    <xsl:template match="xforms:setvalue"/>
    <xsl:template match="xforms:send"/>
    <xsl:template match="xforms:reset"/>
    <xsl:template match="xforms:message"/>
    <xsl:template match="xforms:toggle"/>
    <xsl:template match="xforms:insert"/>
    <xsl:template match="xforms:delete"/>
    <xsl:template match="xforms:setindex"/>
    
    
    <!-- ####################################################################################################### -->
    <!-- #####################################  HELPER TEMPLATES '############################################## -->
    <!-- ####################################################################################################### -->
    
    <xsl:template name="buildControl">
        <xsl:choose>
            <xsl:when test="local-name()='input'">
                <xsl:call-template name="input"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='output'">
                <xsl:call-template name="output"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='range'">
                <xsl:call-template name="range"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='secret'">
                <xsl:call-template name="secret"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='select'">
                <xsl:call-template name="select"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='select1'">
                <xsl:call-template name="select1"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='submit'">
                <xsl:call-template name="submit"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='trigger'">
                <xsl:call-template name="trigger"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='textarea'">
                <xsl:call-template name="textarea"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='upload'">
                <xsl:call-template name="upload"/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='repeat'">
                <xsl:apply-templates select="."/>
            </xsl:when>
            <xsl:when test="local-name()='group'">
                <xsl:apply-templates select="."/>
                <xsl:apply-templates select="xforms:help"/>
                <xsl:apply-templates select="xforms:alert"/>
            </xsl:when>
            <xsl:when test="local-name()='switch'">
                <xsl:apply-templates select="."/>
            </xsl:when>
        </xsl:choose>
    </xsl:template>
    
    
    <!-- ############################################ FUNCTION TEMPLATES ################################################### -->
    
    <!-- retreives all meta tag's except content type -->
    <xsl:template name="getMeta">
        <xsl:variable name="uc">ABCDEFGHIJKLMNOPQRSTUVWXYZ</xsl:variable>
        <xsl:variable name="lc">abcdefghijklmnopqrstuvwxyz</xsl:variable>
        <xsl:for-each select="meta">
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
    
    <!-- chooses the CSS stylesheet to use -->
    <xsl:template name="getChibaCSS">
        <xsl:choose>
            <!-- if the 'css-file' parameter has been set this takes precedence -->
            <xsl:when test="string-length($css-file) > 0">
                <link rel="stylesheet" type="text/css" href="{$css-file}"/>
            </xsl:when>
            <!--  if nothings present standard stylesheets for Mozilla and IE are choosen. -->
            <xsl:otherwise>
                <link rel="stylesheet" type="text/css" href="{$default-css}"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- java scripts for Chiba when it is in scripted mode -->
    <xsl:template name="getChibaScript">
            <!-- PLEASE DON'T CHANGE THE FORMATTING OF THE XSL:TEXT ELEMENTS - THEY PROVIDE CLEAN LINE BREAKS IN THE OUTPUT -->
            
            <!-- dojo init -->
            <script type="text/javascript">
                var djConfig = {
                baseRelativePath: "<xsl:value-of select="concat($contextroot,$scriptPath,'dojo-0.3.1')"/>",
                debugAtAllCost: true,
                isDebug: <xsl:value-of select="$debug-enabled"/> };
            </script>
            
            <!-- dojo lib -->
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'dojo-0.3.1/dojo.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <script type="text/javascript">
                dojo.setModulePrefix("chiba","chiba/");
                dojo.require("dojo.event.*");
                
                var calendarInstance = false;
                var calendarActiveInstance = null;
                var clicked = false;
                var closedByOnIconClick = false;
                this.onclick = function(evt) {
                if(closedByOnIconClick == true) {
                closedByOnIconClick = false;
                } else {
                if(calendarInstance==true) {
                if(clicked == true) {
                calendarInstance=false;
                clicked = false;
                calendarActiveInstance.hideContainer();
                } else {
                clicked = true;
                }
                }
                }
                }
            </script>
            
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'prototype.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- for DateControl - only included if dates are used in the form -->
            <xsl:if test="$uses-dates">
                <script type="text/javascript" src="{concat($contextroot,$scriptPath,'jscalendar/calendar.js')}">&#160;</script>
                <xsl:text>
                </xsl:text>
                <script type="text/javascript" src="{concat($contextroot,$scriptPath,'jscalendar/calendar-setup.js')}">&#160;</script>
                <xsl:text>
                </xsl:text>
                <script type="text/javascript" src="{concat($contextroot,$scriptPath,'jscalendar/lang/calendar-en.js')}">&#160;</script>
                <xsl:text>
                </xsl:text>
            </xsl:if>
            
            
            <!-- for DWR AJAX -->
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'FluxInterface.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- for DWR AJAX -->
            <script type="text/javascript" src="{concat($contextroot,'/Flux/engine.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- for DWR AJAX -->
            <script type="text/javascript" src="{concat($contextroot,'/Flux/interface/Flux.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- for DWR AJAX -->
            <script type="text/javascript" src="{concat($contextroot,'/Flux/util.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- XForms Client -->
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'PresentationContext.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- general xforms utils -->
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'xforms-util.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <!-- scriptaculous lib -->
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'scriptaculous/src/scriptaculous.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            <script type="text/javascript" src="{concat($contextroot,$scriptPath,'scriptaculous/src/effects.js')}">&#160;</script>
            <xsl:text>
            </xsl:text>
            
            <!-- import fckeditor for <textarea xforms:mediatype='html/text'/> -->
            <xsl:if test="$uses-html-textarea">
                <script type="text/javascript" src="{concat($contextroot,$scriptPath,'fckeditor/fckeditor.js')}">&#160;</script>
                <xsl:text>
                </xsl:text>
                <script type="text/javascript" src="{concat($contextroot,$scriptPath,'htmltext.js')}">&#160;</script>
                <xsl:text>
                </xsl:text>
                <script type="text/javascript"><xsl:text>
                </xsl:text>_setStyledTextareaGlobalProperties("Chiba",200,"<xsl:value-of select="concat($contextroot,$scriptPath,'fckeditor/')"/>",1000);<xsl:text>
                </xsl:text>
                </script>
            </xsl:if>
    </xsl:template>
    
    <!-- this template is called when there's no single outermost XForms element meaning there are
        several blocks of XForms markup scattered in the body of the host document. -->
    <xsl:template name="createForm">
        <xsl:element name="form">
            <xsl:attribute name="name">
                <xsl:value-of select="$form-id"/>
            </xsl:attribute>
            <xsl:attribute name="action">
                <xsl:choose>
                    <xsl:when test="$uses-upload">
                        <xsl:value-of select="concat($action-url,'?sessionKey=',$sessionKey)"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$action-url"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:attribute name="method">POST</xsl:attribute>
            <xsl:attribute name="enctype">application/x-www-form-urlencoded</xsl:attribute>
            <xsl:if test="$uses-upload">
                <xsl:attribute name="enctype">multipart/form-data</xsl:attribute>
                <xsl:if test="$scripted = 'true'">
                    <iframe id="UploadTarget" name="UploadTarget" src="" style="width:0px;height:0px;border:0"></iframe>
                </xsl:if>
            </xsl:if>
            <input type="hidden" id="chibaSessionKey" name="sessionKey" value="{$sessionKey}"/>
            
            <!--
            <xsl:if test="$scripted != 'true'">
                <input type="submit" value="refresh page" class="refresh-button"/>
            </xsl:if>
            -->
            
            <xsl:for-each select="*">
                <xsl:apply-templates select="."/>
            </xsl:for-each>
            
            <xsl:if test="$scripted != 'true'">
                <input type="submit" value="refresh page" class="refresh-button"/>
            </xsl:if>
            
        </xsl:element>
    </xsl:template>
    
</xsl:stylesheet>
