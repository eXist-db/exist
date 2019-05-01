<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="http://xmlns.jcp.org/xml/ns/javaee"
    xmlns:jee="http://xmlns.jcp.org/xml/ns/javaee" 
    version="2.0">
    
    <xsl:output method="xml" indent="yes" omit-xml-declaration="no"/>
    
    <xsl:template match="jee:servlet[jee:servlet-name eq 'JMXServlet']" exclude-result-prefixes="jee">
        <xsl:copy-of select="."/>
        
        <xsl:comment>
            Milton provides the WebDAV interface
   </xsl:comment>
        <servlet>
            <servlet-name>milton</servlet-name>
            <servlet-class>org.exist.webdav.MiltonWebDAVServlet</servlet-class>

            <init-param>
                <param-name>resource.factory.class</param-name>
                <param-value>org.exist.webdav.ExistResourceFactory</param-value>
            </init-param>
            
            <xsl:comment>
                Some WebDAV clients send a "Expect: 100-continue" header before 
                uploading body data. Servlet containers (like tomcat and jetty) handle 
                the header in a wrong way, making a client not work OK.
                Set value to TRUE to restore old behavior (FALSE is the new default 
                value, hardcoded in MiltonWebDAVServlet).       
            </xsl:comment>
            <xsl:text disable-output-escaping="yes">
      &lt;!-- </xsl:text>
            <init-param>
                <param-name>enable.expect.continue</param-name>
                <param-value>false</param-value>
            </init-param>
            <xsl:text disable-output-escaping="yes">
      --&gt;    
    </xsl:text>
            <xsl:comment>
                Uncomment to enable debugging
            </xsl:comment>
            <xsl:text disable-output-escaping="yes">
      &lt;!-- </xsl:text>
            <init-param>
                <param-name>filter_0</param-name>
                <param-value>com.bradmcevoy.http.DebugFilter</param-value>
            </init-param>
            <xsl:text disable-output-escaping="yes">
      --&gt;
    </xsl:text>
        </servlet>
    </xsl:template>
    
    <xsl:template match="jee:servlet[jee:servlet-name eq 'XSLTServlet']" exclude-result-prefixes="jee">
        <xsl:copy-of select="."/>
        
        <xsl:comment>
        EXQuery - RESTXQ
    </xsl:comment>
        <servlet>
            <servlet-name>RestXqServlet</servlet-name>
            <servlet-class>org.exist.extensions.exquery.restxq.impl.RestXqServlet</servlet-class>
        </servlet>
    </xsl:template>
    
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>