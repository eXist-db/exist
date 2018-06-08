<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:webxml="http://xmlns.jcp.org/xml/ns/javaee"
    xmlns="http://xmlns.jcp.org/xml/ns/javaee"
    exclude-result-prefixes="webxml xs"
    version="2.0">

    <xsl:output method="xml" indent="yes" omit-xml-declaration="no" encoding="UTF-8" />

    <xsl:param name="bf-resource-servlet" as="xs:boolean" select="false()"/>
    <xsl:param name="bf-full" as="xs:boolean" select="false()"/>

    <xsl:variable name="fqn-resource-servlet" select="'de.betterform.agent.web.resources.ResourceServlet'"/>
    <xsl:variable name="fqn-forms-servlet" select="'de.betterform.agent.web.servlet.FormsServlet'"/>

    <xsl:template match="webxml:display-name[empty(following-sibling::webxml:context-param[webxml:param-name eq 'betterform.configfile'])]">
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
        <xsl:if test="$bf-full">
            <xsl:call-template name="bf-config"/>
        </xsl:if>
    </xsl:template>


    <xsl:template name="bf-config">
<xsl:text>

    </xsl:text><xsl:comment>
        betterFORM configuration file
    </xsl:comment><xsl:text>
    </xsl:text><context-param>
            <param-name>betterform.configfile</param-name>
            <param-value>WEB-INF/betterform-config.xml</param-value>
        </context-param>
    </xsl:template>



    <xsl:template match="webxml:servlet[last()]">
        <xsl:copy>
            <xsl:apply-templates/>
        </xsl:copy>
        <xsl:if test="$bf-resource-servlet or $bf-full">
            <xsl:if test="webxml:no-existing-betterform(.)">
<xsl:text>
    
    </xsl:text><xsl:comment>
        betterFORM servlets
    </xsl:comment><xsl:text>
    </xsl:text>
            </xsl:if>
            <xsl:if test="$bf-resource-servlet and webxml:no-existing-resource-servlet(.)">
                <xsl:call-template name="bf-resource-servlet"/>
            </xsl:if>
            <xsl:if test="$bf-full and webxml:no-existing-full-servlets(.)">
                <xsl:call-template name="bf-full-servlets"/>
            </xsl:if>
            <xsl:if test="webxml:no-existing-betterform(.)">
<xsl:text>
    
    </xsl:text><xsl:comment>
        betterFORM servlet mappings
    </xsl:comment><xsl:text>
    </xsl:text>
            </xsl:if>
            <xsl:if test="$bf-resource-servlet and webxml:no-existing-resource-servlet(.)">
                <xsl:call-template name="bf-resource-servlet-mapping"/>
            </xsl:if>
            <xsl:if test="$bf-full and webxml:no-existing-full-servlets(.)">
                <xsl:call-template name="bf-full-servlet-mappings"/>
                <xsl:text>
    
    </xsl:text><xsl:comment>
        betterFORM filter
    </xsl:comment><xsl:text>
    </xsl:text>
                <xsl:call-template name="bf-full-filter"/>
                <xsl:text>
    
    </xsl:text><xsl:comment>
        betterFORM filter mappings
    </xsl:comment><xsl:text>
    </xsl:text>
                <xsl:call-template name="bf-full-filter-mappings"/>
                <xsl:text>
    
    </xsl:text>
            </xsl:if>
        </xsl:if>
    </xsl:template>
    
    <xsl:template name="bf-resource-servlet">
        <servlet>
            <servlet-name>ResourceServlet</servlet-name>
            <servlet-class>de.betterform.agent.web.resources.ResourceServlet</servlet-class>
            <init-param>
                <param-name>caching</param-name>
                <param-value>true</param-value>
            </init-param>
        </servlet>
    </xsl:template>
    
    <xsl:template name="bf-resource-servlet-mapping">
        <servlet-mapping>
            <servlet-name>ResourceServlet</servlet-name>
            <url-pattern>/bfResources/*</url-pattern>
        </servlet-mapping>
    </xsl:template>
    
    <xsl:template name="bf-full-servlets">
        <servlet>
            <servlet-name>Flux</servlet-name>
            <servlet-class>org.directwebremoting.servlet.DwrServlet</servlet-class>
            <init-param>
                <param-name>debug</param-name>
                <param-value>true</param-value>
            </init-param>
        </servlet>
        <servlet>
            <servlet-name>XFormsPostServlet</servlet-name>
            <servlet-class>de.betterform.agent.web.servlet.XFormsPostServlet</servlet-class>
        </servlet>
        <servlet>
            <servlet-name>FormsServlet</servlet-name>
            <servlet-class>de.betterform.agent.web.servlet.FormsServlet</servlet-class>
        </servlet>
        <servlet>
            <servlet-name>inspector</servlet-name>
            <servlet-class>de.betterform.agent.web.servlet.XFormsInspectorServlet</servlet-class>
        </servlet>
        <servlet>
            <servlet-name>error</servlet-name>
            <servlet-class>de.betterform.agent.web.servlet.ErrorServlet</servlet-class>
        </servlet>
    </xsl:template>
    
    <xsl:template name="bf-full-servlet-mappings">
        <servlet-mapping>
            <servlet-name>Flux</servlet-name>
            <url-pattern>/Flux/*</url-pattern>
        </servlet-mapping>
        <servlet-mapping>
            <servlet-name>XFormsPostServlet</servlet-name>
            <url-pattern>/XFormsPost</url-pattern>
        </servlet-mapping>
        <servlet-mapping>
            <servlet-name>XQueryServlet</servlet-name>
            <url-pattern>*.xql</url-pattern>
        </servlet-mapping>
        <servlet-mapping>
            <servlet-name>FormsServlet</servlet-name>
            <url-pattern>/forms/formslist</url-pattern>
        </servlet-mapping>
        <servlet-mapping>
            <servlet-name>inspector</servlet-name>
            <url-pattern>/inspector/*</url-pattern>
        </servlet-mapping>
        <servlet-mapping>
            <servlet-name>error</servlet-name>
            <url-pattern>/error/*</url-pattern>
        </servlet-mapping>
    </xsl:template>
    
    <xsl:template name="bf-full-filter">
        <filter>
            <filter-name>XFormsFilter</filter-name>
            <filter-class>de.betterform.agent.web.filter.XFormsFilter</filter-class>
        </filter>
    </xsl:template>
    
    <xsl:template name="bf-full-filter-mappings">
        <filter-mapping>
            <filter-name>XFormsFilter</filter-name>
            <url-pattern>/apps/*</url-pattern>
        </filter-mapping>
        <filter-mapping>
            <filter-name>XFormsFilter</filter-name>
            <servlet-name>XFormsPostServlet</servlet-name>
        </filter-mapping>

        <listener>
            <listener-class>de.betterform.agent.web.servlet.BfServletContextListener</listener-class>
        </listener>
    </xsl:template>
    
    <xsl:function name="webxml:no-existing-betterform" as="xs:boolean">
        <xsl:param name="context" as="element(webxml:servlet)"/>
        <xsl:value-of select="empty($context/parent::webxml:web-app/webxml:servlet[webxml:servlet-class = ($fqn-resource-servlet, $fqn-forms-servlet)])"/>
    </xsl:function>
    
    <xsl:function name="webxml:no-existing-resource-servlet" as="xs:boolean">
        <xsl:param name="context" as="element(webxml:servlet)"/>
        <xsl:value-of select="empty($context/parent::webxml:web-app/webxml:servlet[webxml:servlet-class = $fqn-resource-servlet])"/>
    </xsl:function>
    
    <xsl:function name="webxml:no-existing-full-servlets" as="xs:boolean">
        <xsl:param name="context" as="element(webxml:servlet)"/>
        <xsl:value-of select="empty($context/parent::webxml:web-app/webxml:servlet[webxml:servlet-class = $fqn-forms-servlet])"/>
    </xsl:function>

    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
