/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.urlrewrite;

import org.exist.Namespaces;
import org.exist.http.servlets.HttpResponseWrapper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Base class for all rewritten URLs.
 */
public abstract class URLRewrite {

    private static final String UNSET = "";

    protected String uri;
    protected String target;
    protected String prefix = null;
    protected String method = null;
    protected Map<String, String> attributes = null;
    protected Map<String, List<String>> parameters = null;
    protected Map<String, String> headers = null;
    protected boolean absolute = false;

    protected URLRewrite(final Element config, final String uri) {
        this.uri = uri;
        if (config == null) {
            return;
        }
        if (config.hasAttribute("absolute")) {
            absolute = "yes".equals(config.getAttribute("absolute"));
        }
        if (config.hasAttribute("method")) {
            method = config.getAttribute("method").toUpperCase();
        }
        // Check for add-parameter elements etc.
        if (!config.hasChildNodes()) {
            return;
        }
        Node node = config.getFirstChild();
        while (node != null) {
            final String ns = node.getNamespaceURI();
            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(ns)) {
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

    protected URLRewrite(final URLRewrite other) {
        this.uri = other.uri;
        this.target = other.target;
        this.prefix = other.prefix;
        this.method = other.method;
    }

    protected void updateRequest(final XQueryURLRewrite.RequestWrapper request) {
        if (prefix != null) {
            request.removePathPrefix(prefix);
        }
    }

    protected void rewriteRequest(final XQueryURLRewrite.RequestWrapper request) {
        // do nothing by default
    }

    protected void setAbsolutePath(final XQueryURLRewrite.RequestWrapper request) {
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
     * @param request the request wrapper
     *
     * @return the new target path excluding context path
     *
     * @throws ServletException if an error occurs
     */
    protected String resolve(final XQueryURLRewrite.RequestWrapper request) throws ServletException {
        final String path = request.getInContextPath();
        if (target == null) {
            return path;
        }
        final String fixedTarget;
        if (request.getBasePath() != null && target.startsWith("/")) {
            fixedTarget = request.getBasePath() + target;
        } else {
            fixedTarget = target;
        }
        try {
            final URI reqURI = new URI(path);
            return reqURI.resolve(fixedTarget).toASCIIString();
        } catch (final URISyntaxException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void copyFrom(final URLRewrite other) {
        if (other.headers != null) {
            this.headers = new HashMap<>(other.headers);
        }
        if (other.attributes != null) {
            this.attributes = new HashMap<>(other.attributes);
        }
        if (other.parameters != null) {
            this.parameters = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : other.parameters.entrySet()) {
                this.parameters.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    protected abstract URLRewrite copy();

    private void setHeader(final String key, final String value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(key, value);
    }

    private void addNameValue(final String name, final String value, final Map<String, List<String>> map) {
        List<String> values = map.get(name);
        if (values == null) {
            values = new ArrayList<>();
        }
        values.add(value);
        map.put(name, values);
    }

    private void addParameter(final String name, final String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        addNameValue(name, value, parameters);
    }

    private void setAttribute(final String name, final String value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(name, value);
    }

    private void unsetAttribute(final String name) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(name, UNSET);
    }

    public void setTarget(final String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public void setURI(final String uri) {
        this.uri = uri;
    }

    public String getURI() {
        return uri;
    }

    public void setPrefix(String prefix) {
        if (prefix.endsWith("/")) {
            prefix = prefix.replaceFirst("/+$", "");
        }
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public abstract void doRewrite(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException;

    public void prepareRequest(final XQueryURLRewrite.RequestWrapper request) {
        if (parameters != null) {
            for (final Map.Entry<String, List<String>> param : parameters.entrySet()) {
                for (final String paramValue : param.getValue()) {
                    request.addParameter(param.getKey(), paramValue);
                }
            }
        }
        if (attributes != null) {
            for (final Map.Entry<String, String> entry : attributes.entrySet()) {
                final String value = entry.getValue();
                if (value.equals(UNSET)) {
                    request.removeAttribute(entry.getKey());
                } else {
                    request.setAttribute(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    protected void setHeaders(final HttpResponseWrapper response) {
        if (headers != null) {
            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                response.setHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    public boolean isControllerForward() {
        return false;
    }

    protected static String normalizePath(final String path) {
        final StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '/') {
                if (i == 0 || path.charAt(i - 1) != '/') {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}