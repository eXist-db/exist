<xsl:stylesheet version="2.0"
xmlns:webxml="http://java.sun.com/xml/ns/j2ee"
xmlns="http://java.sun.com/xml/ns/j2ee"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
exclude-result-prefixes="webxml">
  <xsl:output method="xml" indent="yes" />
  <xsl:param name="webxml.path" select="''" />
  <xsl:template match="/">
    <xsl:choose>
      <xsl:when test="exists(/webxml:web-app/webxml:context-param/webxml:param-name[.='betterform.configfile'])">

        <xsl:message terminate="yes">betterFORM is already
        installed. Please run 'ant uninstall' before installing it
        again</xsl:message>
      </xsl:when>
      <xsl:otherwise>
        <xsl:copy>
          <xsl:apply-templates />
        </xsl:copy>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="/webxml:web-app/webxml:display-name">
    <xsl:copy-of select="." />
    <xsl:text>
    
</xsl:text>
    <context-param>
      <param-name>betterform.configfile</param-name>
      <param-value>WEB-INF/betterform-config.xml</param-value>
    </context-param>
  </xsl:template>
  <xsl:template match="/webxml:web-app/webxml:filter[last()]">
    <xsl:comment>XForms Filter</xsl:comment>
    <xsl:text>
    
</xsl:text>
    <filter>
      <filter-name>XFormsFilter</filter-name>
      <filter-class>
      de.betterform.agent.web.filter.XFormsFilter</filter-class>
      <init-param>
        <param-name>useragent</param-name>
        <param-value>dojo</param-value>
      </init-param>
    </filter>
    <xsl:text>
    
</xsl:text>
    <xsl:copy-of select="." />
  </xsl:template>
  <xsl:template match="/webxml:web-app/webxml:servlet[last()]">
    <xsl:copy-of select="." />
    <xsl:text></xsl:text>
    <xsl:comment>betterFORM servlets</xsl:comment>
    <xsl:text>
    
</xsl:text>
    <servlet>
      <servlet-name>Flux</servlet-name>
      <servlet-class>
      org.directwebremoting.servlet.DwrServlet</servlet-class>
      <init-param>
        <param-name>debug</param-name>
        <param-value>true</param-value>
      </init-param>
    </servlet>
    <servlet>
      <servlet-name>XFormsPostServlet</servlet-name>
      <servlet-class>
      de.betterform.agent.web.servlet.XFormsPostServlet</servlet-class>
    </servlet>
    <servlet>
      <servlet-name>FormsServlet</servlet-name>
      <servlet-class>
      de.betterform.agent.web.servlet.FormsServlet</servlet-class>
    </servlet>
    <servlet>
      <servlet-name>inspector</servlet-name>
      <servlet-class>
      de.betterform.agent.web.servlet.XFormsInspectorServlet</servlet-class>
    </servlet>
    <servlet>
      <servlet-name>ResourceServlet</servlet-name>
      <servlet-class>
      de.betterform.agent.web.resources.ResourceServlet</servlet-class>
    </servlet>
    <servlet>
      <servlet-name>error</servlet-name>
      <servlet-class>de.betterform.agent.web.servlet.ErrorServlet</servlet-class>
    </servlet>
  </xsl:template>
  <xsl:template match="/webxml:web-app/webxml:filter-mapping[webxml:filter-name/text()='XQueryURLRewrite']">

    <xsl:text></xsl:text>
	
	
    <filter-mapping>
      <filter-name>XFormsFilter</filter-name>
      <url-pattern>/rest/db/*</url-pattern>
    </filter-mapping>
    <filter-mapping>
      <filter-name>XFormsFilter</filter-name>
      <servlet-name>XFormsPostServlet</servlet-name>
    </filter-mapping>
    <xsl:text>
    
</xsl:text>
    <xsl:copy-of select="." />
    <xsl:text>
    
</xsl:text>
    <xsl:comment>betterFORM Flux Servlet Mapping</xsl:comment>
    <xsl:text>
    
</xsl:text>
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
      <servlet-name>ResourceServlet</servlet-name>
      <url-pattern>/bfResources/*</url-pattern>
    </servlet-mapping>
    <servlet-mapping>
      <servlet-name>error</servlet-name>
      <url-pattern>/error/*</url-pattern>
    </servlet-mapping>
  </xsl:template>
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*" />
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
