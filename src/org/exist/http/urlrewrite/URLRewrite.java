/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.http.urlrewrite;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.exist.Namespaces;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Base class for all rewritten URLs.
 */
public abstract class URLRewrite {

    private final static Object UNSET = new Object();
    
    protected String uri;
    protected String target;
    protected String prefix = null;
    protected Map attributes = null;
    protected Map parameters = null;
    protected Map headers = null;

    protected URLRewrite(Element config, String uri) {
        this.uri = uri;
        // Check for add-parameter elements etc.
        if (config != null && config.hasChildNodes()) {
            Node node = config.getFirstChild();
            while (node != null) {
                if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
                    Element elem = (Element) node;
                    if ("add-parameter".equals(elem.getLocalName())) {
                        addParameter(elem.getAttribute("name"), elem.getAttribute("value"));
                    } else if ("set-attribute".equals(elem.getLocalName())) {
                        setAttribute(elem.getAttribute("name"), elem.getAttribute("value"));
                    } else if ("clear-attribute".equals(elem.getLocalName())) {
                        unsetAttribute(elem.getAttribute("name"));
                    } else if ("set-header".equals(elem.getLocalName())) {
                       setHeader(elem.getAttribute("name"), elem.getAttribute("value"));
                    }
                }
                node = node.getNextSibling();
            }
        }
    }

    protected void updateRequest(XQueryURLRewrite.RequestWrapper request) {
        if (prefix != null)
            request.removePathPrefix(prefix);
    }

    protected void rewriteRequest(XQueryURLRewrite.RequestWrapper request) {
        // do nothing by default
    }

    /**
     * Resolve the target of this rewrite rule against the current request context.
     *
     * @return the new target path excluding context path
     */
    protected String resolve(XQueryURLRewrite.RequestWrapper request) throws ServletException {
        String path = request.getInContextPath();
        if (target == null)
            return path;
        String fixedTarget;
        if (request.getBasePath() != null && target.startsWith("/")) {
            fixedTarget = request.getBasePath() + target;
        } else
            fixedTarget = target;
        try {
            URI reqURI = new URI(path);
            return reqURI.resolve(fixedTarget).toASCIIString();
        } catch (URISyntaxException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void copyFrom(URLRewrite other) {
        this.headers = other.headers;
        this.parameters = other.parameters;
        this.attributes = other.attributes;
    }

    private void setHeader(String key, String value) {
        if (headers == null)
            headers = new HashMap();
        headers.put(key, value);
    }

    private void addParameter(String name, String value) {
        if (parameters == null)
            parameters = new HashMap();
        parameters.put(name, value);
    }

    private void setAttribute(String name, String value) {
        if (attributes == null)
            attributes = new HashMap();
        attributes.put(name, value);
    }

    private void unsetAttribute(String name) {
        if (attributes == null)
            attributes = new HashMap();
        attributes.put(name, UNSET);
    }
    
    public void setTarget(String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public void setURI(String uri) {
        this.uri = uri;
    }

    public String getURI() {
        return uri;
    }

    public void setPrefix(String prefix) {
        if (prefix.endsWith("/"))
            prefix = prefix.replaceFirst("/+$", "");
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public abstract void doRewrite(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException;

    public void prepareRequest(XQueryURLRewrite.RequestWrapper request) {
        if (parameters != null) {
            for (Iterator iterator = parameters.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                request.addParameter(entry.getKey().toString(), (String) entry.getValue());
            }
        }
        if (attributes != null) {
            for (Iterator iterator = attributes.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object value = entry.getValue();
                if (value == UNSET)
                    request.removeAttribute(entry.getKey().toString());
                else
                    request.setAttribute(entry.getKey().toString(), entry.getValue());
            }
        }
    }

    protected void setHeaders(HttpServletResponse response) {
        if (headers != null) {
            for (Iterator iterator = headers.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                response.setHeader(entry.getKey().toString(), entry.getValue().toString());
            }
        }
    }

    public boolean isControllerForward() {
        return false;
    }
    
    protected static String normalizePath(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') {
                if (i == 0 || path.charAt(i - 1) != '/')
                    sb.append(c);
            } else
                sb.append(c);
        }
        return sb.toString();
    }
}