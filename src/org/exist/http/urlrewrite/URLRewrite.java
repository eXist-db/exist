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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.Namespaces;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * Base class for all rewritten URLs.
 */
public abstract class URLRewrite {

    private final static String UNSET = "";
    
    protected String uri;
    protected String target;
    protected String prefix = null;
    protected String method = null;
    protected Map<String, String> attributes = null;
    protected Map<String, List<String>> parameters = null;
    protected Map<String, String> headers = null;
    protected boolean absolute = false;
    
    protected URLRewrite(Element config, String uri) {
        this.uri = uri;
        if (config != null && config.hasAttribute("absolute"))
        	absolute = "yes".equals(config.getAttribute("absolute"));
        if (config != null && config.hasAttribute("method"))
        	{method = config.getAttribute("method").toUpperCase();}
        // Check for add-parameter elements etc.
        if (config != null && config.hasChildNodes()) {
            Node node = config.getFirstChild();
            while (node != null) {
                if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
                    final Element elem = (Element) node;
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
            {request.removePathPrefix(prefix);}
    }

    protected void rewriteRequest(XQueryURLRewrite.RequestWrapper request) {
        // do nothing by default
    }

    protected void setAbsolutePath(XQueryURLRewrite.RequestWrapper request) {
    	request.setPaths(target, null);
    }

    protected String getMethod() {
    	return method;
    }
    
    protected boolean doResolve() {
		return !absolute;
	}

	/**
     * Resolve the target of this rewrite rule against the current request context.
     *
     * @return the new target path excluding context path
     */
    protected String resolve(XQueryURLRewrite.RequestWrapper request) throws ServletException {
        final String path = request.getInContextPath();
        if (target == null)
            {return path;}
        String fixedTarget;
        if (request.getBasePath() != null && target.startsWith("/")) {
            fixedTarget = request.getBasePath() + target;
        } else
            {fixedTarget = target;}
        try {
            final URI reqURI = new URI(path);
            return reqURI.resolve(fixedTarget).toASCIIString();
        } catch (final URISyntaxException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void copyFrom(URLRewrite other) {
        this.headers = other.headers;
        this.parameters = other.parameters;
        this.attributes = other.attributes;
    }

    private void setHeader(String key, String value) {
        if(headers == null) {
            headers = new HashMap<String, String>();
        }
        headers.put(key, value);
    }

    private void addNameValue(String name, String value, Map<String, List<String>> map) {
        List<String> values = map.get(name);
        if(values == null) {
            values = new ArrayList<String>();
        }
        values.add(value);
        map.put(name, values);
    }

    private void addParameter(String name, String value) {
        if(parameters == null){
            parameters = new HashMap<String, List<String>>();
        }
        addNameValue(name, value, parameters);
    }

    private void setAttribute(String name, String value) {
        if(attributes == null) {
            attributes = new HashMap<String, String>();
        }
        attributes.put(name, value);
    }

    private void unsetAttribute(String name) {
        if(attributes == null){
            attributes = new HashMap<String, String>();
        }
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
            {prefix = prefix.replaceFirst("/+$", "");}
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public abstract void doRewrite(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;

    public void prepareRequest(XQueryURLRewrite.RequestWrapper request) {
        if (parameters != null) {
            for(final Map.Entry<String, List<String>> param : parameters.entrySet()) {
                for(final String paramValue : param.getValue()) {
                    request.addParameter(param.getKey().toString(), paramValue);
                }
            }
        }
        if (attributes != null) {
            for (final Map.Entry<String, String> entry : attributes.entrySet()) {
            	final String value = entry.getValue();
                if(value.equals(UNSET)) {
                    request.removeAttribute(entry.getKey().toString());
                } else {
                    request.setAttribute(entry.getKey().toString(), entry.getValue());
                }
            }
        }
    }

    protected void setHeaders(HttpResponseWrapper response) {
        if (headers != null) {
            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                response.setHeader(entry.getKey().toString(), entry.getValue().toString());
            }
        }
    }

    public boolean isControllerForward() {
        return false;
    }
    
    protected static String normalizePath(String path) {
        final StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '/') {
                if (i == 0 || path.charAt(i - 1) != '/')
                    {sb.append(c);}
            } else
                {sb.append(c);}
        }
        return sb.toString();
    }
}