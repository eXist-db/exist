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
    xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:jee="https://jakarta.ee/xml/ns/jakartaee"
    version="2.0">
    
    <xsl:output method="xml" indent="yes" omit-xml-declaration="no"/>
    
    <xsl:template match="jee:servlet[jee:servlet-name eq 'JMXServlet']" exclude-result-prefixes="jee">
        <xsl:copy-of select="."/>

        <xsl:comment> Jackrabbit provides the WebDAV interface </xsl:comment>
        <servlet>
            <servlet-name>webdav</servlet-name>
            <servlet-class>org.exist.webdav.ExistWebdavServlet</servlet-class>
            <load-on-startup>3</load-on-startup>
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
    
    <!-- Map WebDAV servlet directly — bypasses XQueryURLRewrite -->
    <xsl:template match="jee:servlet-mapping[jee:servlet-name eq 'XQueryURLRewrite']" exclude-result-prefixes="jee">
        <servlet-mapping>
            <servlet-name>webdav</servlet-name>
            <url-pattern>/webdav/*</url-pattern>
        </servlet-mapping>
        <xsl:copy-of select="."/>
    </xsl:template>

    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>