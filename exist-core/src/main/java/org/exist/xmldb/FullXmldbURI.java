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
package org.exist.xmldb;

import java.net.URI;
import java.net.URISyntaxException;

public class FullXmldbURI extends XmldbURI {

    //this will never have xmldb:
    private URI wrappedURI;
    private String context;
    private String apiName;

    /**
     * Constructs an XmldbURI from given URI. The provided URI must have the
     * XMLDB_SCHEME ("xmldb")
     *
     * @param xmldbURI A string
     * @throws URISyntaxException If the given string is not a valid xmldb URI.
     */
    protected FullXmldbURI(final URI xmldbURI) throws URISyntaxException {
        this(xmldbURI, true);
    }

    protected FullXmldbURI(final URI xmldbURI, final boolean mustHaveXMLDB) throws URISyntaxException {
        super(xmldbURI, mustHaveXMLDB);
    }

    /**
     * Feeds private members
     *
     * @param xmldbURI the xmldb URI.
     * @param hadXmldbPrefix if the xmldb URI has an xmldb prefix.
     *
     * @throws URISyntaxException if the URI is invalid.
     */
    @Override
    protected void parseURI(final URI xmldbURI, final boolean hadXmldbPrefix) throws URISyntaxException {
        wrappedURI = xmldbURI;
        if (hadXmldbPrefix) {
            if (wrappedURI.getScheme() == null) {
                throw new URISyntaxException(XMLDB_URI_PREFIX + wrappedURI, "xmldb URI scheme has no instance name");
            }

            String userInfo = wrappedURI.getUserInfo();
            //Very tricky :
            if (wrappedURI.getHost() == null && wrappedURI.getAuthority() != null) {
                userInfo = wrappedURI.getAuthority();
                if (userInfo.endsWith("@")) {
                    userInfo = userInfo.substring(0, userInfo.length() - 1);
                }
            }

            //Eventually rewrite wrappedURI *without* user info
            if (userInfo != null) {
                final StringBuilder recomputed = new StringBuilder();//XMLDB_URI_PREFIX);
                recomputed.append(wrappedURI.getScheme());
                recomputed.append("://");
                recomputed.append(wrappedURI.getHost());
                if (wrappedURI.getPort() != -1) {
                    recomputed.append(":").append(wrappedURI.getPort());
                }
                recomputed.append(wrappedURI.getRawPath());
                wrappedURI = new URI(recomputed.toString());
            }
        }
        super.parseURI(xmldbURI, hadXmldbPrefix);
    }

    @Override
    protected void splitPath(final String path) throws URISyntaxException {
        int index = -1;
        int lastIndex = -1;
        //Reinitialise members
        this.context = null;
        String pathForSuper = null;

        if (path != null) {
            final String host = getHost();
            if (host == null || EMBEDDED_SERVER_AUTHORITY.equals(host)) {
                if (getPort() != NO_PORT) {
                    throw new URISyntaxException(XMLDB_URI_PREFIX + wrappedURI.toString(), "Local xmldb URI should not provide a port");
                }
                apiName = API_LOCAL;
                context = null;
                pathForSuper = path;

            } else {
                //Try to extract the protocol from the provided URI. 
                //TODO : get rid of this and use a more robust approach (dedicated constructor ?) -pb
                //TODO : use named constants  
                index = path.lastIndexOf("/xmlrpc");
                if (index > lastIndex) {
                    apiName = API_XMLRPC;
                    pathForSuper = path.substring(index + "/xmlrpc".length());
                    context = path.substring(0, index) + "/xmlrpc";
                    lastIndex = index;
                }

                //TODO : use named constants  
                index = path.lastIndexOf("/webdav");
                if (index > lastIndex) {
                    apiName = API_WEBDAV;
                    pathForSuper = path.substring(index + "/webdav".length());
                    context = path.substring(0, index) + "/webdav";
                    lastIndex = index;
                }

                //Default : REST-style...
                if (apiName == null) {
                    apiName = API_REST;
                    pathForSuper = path;
                    //TODO : determine the context out of a clean root collection policy.
                    context = null;
                }
            }
        }
        super.splitPath(pathForSuper);
    }

    /**
     * To be called each time a private member that interacts with the wrapped
     * URI is modified.
     *
     * @throws URISyntaxException if the URI is invalid.
     */
    @Override
    protected void recomputeURI() throws URISyntaxException {
        final URI oldWrappedURI = wrappedURI;
        final StringBuilder buf = new StringBuilder();
        if (getInstanceName() != null) {
            buf.append(getInstanceName()).append("://");
        }

        //No userInfo
        if (getHost() != null) {
            buf.append(getHost());
        }

        if (getPort() != NO_PORT) {
            buf.append(":").append(getPort());
        }

        if (context != null) {
            buf.append(context);
        }

        //TODO : eventually use a prepend.root.collection system property 		
        if (getRawCollectionPath() != null) {
            buf.append(getRawCollectionPath());
        }

        try {
            wrappedURI = new URI(buf.toString());
        } catch (final URISyntaxException e) {
            wrappedURI = oldWrappedURI;
            throw e;
        }
    }

    private void setContext(String context) {
        final String oldContext = this.context;
        try {
            //trims any trailing slash 
            if (context != null && context.endsWith("/")) {
                //include root slash if we have a host
                if (this.getHost() != null) {
                    context = context.substring(0, context.length() - 1);
                }
            }
            this.context = context != null && context.isEmpty() ? null : context;
            recomputeURI();

        } catch (final URISyntaxException e) {
            this.context = oldContext;
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    @Override
    public URI getURI() {
        return wrappedURI;
    }

    @Override
    public URI getXmldbURI() {
        return URI.create(XMLDB_URI_PREFIX + wrappedURI.toString());
    }

    @Override
    public String getInstanceName() {
        return wrappedURI.getScheme();
    }

    @Override
    public String getApiName() {
        return apiName;
    }

    @Override
    public String getContext() {
        return context;
    }

    @Override
    public boolean isAbsolute() {
        return wrappedURI.isAbsolute();
    }

    @Override
    public boolean isContextAbsolute() {
        final String currentContext = this.getContext();
        if (currentContext == null) {
            return true;
        }
        return currentContext.startsWith("/");
    }

    @Override
    public XmldbURI normalizeContext() {
        final String currentContext = this.getContext();
        if (currentContext == null) {
            return this;
        }

        final URI uri = URI.create(currentContext);
        try {
            final FullXmldbURI xmldbURI = new FullXmldbURI(getXmldbURI());
            xmldbURI.setContext(uri.normalize().getRawPath());
            return xmldbURI;

        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    @Override
    public URI relativizeContext(final URI uri) {
        if (uri == null) {
            throw new NullPointerException("The provided URI is null");
        }

        final String currentContext = this.getContext();
        if (currentContext == null) {
            throw new NullPointerException("The current context is null");
        }

        final URI contextURI;
        //Adds a final slash if necessary
        if (!currentContext.endsWith("/")) {
            LOG.info("Added a final '/' to '{}'", currentContext);
            contextURI = URI.create(currentContext + "/");
        } else {
            contextURI = URI.create(currentContext);
        }

        return contextURI.relativize(uri);
    }

    @Override
    public URI resolveContext(final String str) throws NullPointerException, IllegalArgumentException {
        if (str == null) {
            throw new NullPointerException("The provided URI is null");
        }

        String currentContext = this.getContext();
        if (currentContext == null) {
            throw new NullPointerException("The current context is null");
        }

        // Add a final slash if necessary
        if (!currentContext.endsWith("/")) {
            LOG.info("Added a final '/' to '{}'", currentContext);
            currentContext += "/";
        }

        final URI contextURI = URI.create(currentContext);

        return contextURI.resolve(str);
    }

    @Override
    public URI resolveContext(final URI uri) throws NullPointerException {
        if (uri == null) {
            throw new NullPointerException("The provided URI is null");
        }

        String currentContext = this.getContext();
        if (currentContext == null) {
            throw new NullPointerException("The current context is null");
        }

        // Add a final slash if necessary
        if (!currentContext.endsWith("/")) {
            LOG.info("Added a final '/' to '{}'", currentContext);
            currentContext += "/";
        }

        final URI contextURI = URI.create(currentContext);

        return contextURI.resolve(uri);
    }

    @Override
    public String toString() {
        if (hadXmldbPrefix) {
            return XMLDB_URI_PREFIX + wrappedURI.toString();
        } else {
            return wrappedURI.toString();
        }
    }

    @Override
    public String getAuthority() {
        return wrappedURI.getAuthority();
    }

    @Override
    public String getFragment() {
        return wrappedURI.getFragment();
    }

    @Override
    public int getPort() {
        return wrappedURI.getPort();
    }

    @Override
    public String getQuery() {
        return wrappedURI.getQuery();
    }

    @Override
    public String getRawAuthority() {
        return wrappedURI.getRawAuthority();
    }

    @Override
    public String getHost() {
        return wrappedURI.getHost();
    }

    @Override
    public String getUserInfo() {
        return wrappedURI.getUserInfo();
    }

    @Override
    public String getRawFragment() {
        return wrappedURI.getRawFragment();
    }

    @Override
    public String getRawQuery() {
        return wrappedURI.getRawQuery();
    }

    @Override
    public String getRawUserInfo() {
        return wrappedURI.getRawUserInfo();
    }
}
