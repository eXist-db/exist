<?xml version="1.0" encoding="UTF-8"?>
<!--

    eXist-db Open Source Native XML Database
    Copyright (C) 2001 The eXist-db Authors

    info@exist-db.org
    http://www.exist-db.org

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

-->
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