/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.http.webdav;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class WebDAVUtil {
    
    public final static String PARSE_ERR = "Request content could not be parsed: ";
    public final static String XML_CONFIGURATION_ERR = "Failed to create XML parser: ";
    public final static String UNEXPECTED_ELEMENT_ERR = "Unexpected element found: ";
    
    public static Document parseRequestContent(HttpServletRequest request,
            HttpServletResponse response, DocumentBuilder docBuilder)
            throws ServletException, IOException {
        if(request.getContentLength() == 0)
            return null;
        try {
            String content = getRequestContent(request);
            if(content.length() == 0)
                return null;
            return docBuilder.parse(new InputSource(new StringReader(content)));
        } catch (SAXException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    PARSE_ERR + e.getMessage());
            return null;
        }
    }
    
    public static String getRequestContent(HttpServletRequest request) throws IOException {
        String encoding = request.getCharacterEncoding();
        if(encoding == null)
            encoding = "UTF-8";
        try {
            ServletInputStream is = request.getInputStream();
            Reader  reader = new InputStreamReader(is, encoding);
            StringWriter content = new StringWriter();
            char ch[] = new char[4096];
            int len = 0;
            while((len = reader.read(ch)) > -1)
                content.write(ch, 0, len);
            return content.toString();
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Unsupported character encoding in request content: " + encoding);
        }
    }
    
    public static Node firstElementNode(Node node) {
        node = node.getFirstChild();
        while(node != null) {
            if(node.getNodeType() == Node.ELEMENT_NODE &&
                    node.getNamespaceURI().equals(WebDAV.DAV_NS))
                break;
        }
        return node;
    }
    
    public static String getElementContent(Node node) {
        StringBuilder content = new StringBuilder();
        node = node.getFirstChild();
        while(node != null) {
            if(node.getNodeType() == Node.TEXT_NODE)
                content.append(((Text)node).getData());
        }
        return content.toString();
    }
}
