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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.xquery.Constants;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.AnyURIValue;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;

import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A utility class for xmldb URis. Since, java.net.URI is <strong>final</strong> this class acts as a wrapper.
 *
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
/*
 * This base class implementation only provides a path.  FullXmldbURI provides
 * full uri support.  The create method creates a minimal object to keep memory
 * usage low.
 */
public class XmldbURI implements Comparable<Object>, Serializable, Cloneable {

    protected final static Logger LOG = LogManager.getLogger(XmldbURI.class);
    public static final int NO_PORT = -1;
    //Should be provided by org.xmldb.api package !!!
    public static final String XMLDB_SCHEME = "xmldb";
    public static final String XMLDB_URI_PREFIX = "xmldb:";
    public static final String DEFAULT_INSTANCE_NAME = "exist";
    public static final String EMBEDDED_SERVER_AUTHORITY = "embedded-eXist-server";
    public static final String EMBEDDED_SERVER_URI_PREFIX = XMLDB_URI_PREFIX + DEFAULT_INSTANCE_NAME + "://";
    public static final String EMBEDDED_SHORT_URI_PREFIX = XMLDB_URI_PREFIX + "/";

    /**
     * 'db' collection name
     */
    public final static String ROOT_COLLECTION_NAME = "db";
    /**
     * '/db' collection name
     */
    public final static String ROOT_COLLECTION = "/" + ROOT_COLLECTION_NAME;
    /**
     * 'system' collection name
     */
    public final static String SYSTEM_COLLECTION_NAME = "system";
    /**
     * '/db/system' collection name
     */
    public final static String SYSTEM_COLLECTION = ROOT_COLLECTION + "/" + SYSTEM_COLLECTION_NAME;
    /**
     * 'temp' collection name
     */
    public final static String TEMP_COLLECTION_NAME = "temp";
    /**
     * '/db/system/temp' collection name
     */
    public final static String TEMP_COLLECTION = SYSTEM_COLLECTION + "/" + TEMP_COLLECTION_NAME;

    /**
     * '/db/system/config' collection name
     */
    @Deprecated
    public final static String CONFIG_COLLECTION = SYSTEM_COLLECTION + "/config";

    /**
     * '/db' collection *
     */
    public final static XmldbURI DB = create(ROOT_COLLECTION);
    public final static XmldbURI ROOT_COLLECTION_URI = create(ROOT_COLLECTION);
    public final static XmldbURI RELATIVE_ROOT_COLLECTION_URI = create(ROOT_COLLECTION_NAME);

    /**
     * '/db/system' *
     */
    public final static XmldbURI SYSTEM = create(SYSTEM_COLLECTION);
    /**
     * '/db/system' *
     */
    @Deprecated
    public final static XmldbURI SYSTEM_COLLECTION_URI = create(SYSTEM_COLLECTION);

    @Deprecated
    public final static XmldbURI CONFIG_COLLECTION_URI = create(CONFIG_COLLECTION);

    public final static XmldbURI TEMP_COLLECTION_URI = create(TEMP_COLLECTION);
    public final static XmldbURI EMPTY_URI = createInternal("");
    public static final XmldbURI EMBEDDED_SERVER_URI = XmldbURI.create(EMBEDDED_SERVER_URI_PREFIX + EMBEDDED_SERVER_AUTHORITY);
    /**
     * 'xmldb:exist///db'
     */
    public static final String LOCAL_DB = EMBEDDED_SERVER_URI_PREFIX + ROOT_COLLECTION;
    /**
     * 'xmldb:exist///db' XmldbURI
     */
    public static final XmldbURI LOCAL_DB_URI = XmldbURI.create(EMBEDDED_SERVER_URI_PREFIX + ROOT_COLLECTION);
    //TODO : deprecate when we split at root collection
    public final static String API_XMLRPC = "xmlrpc";
    public final static String API_WEBDAV = "webdav";
    public final static String API_REST = "rest-style";
    public final static String API_LOCAL = "local";

    private final static XmldbURI[] NO_SEGMENTS = new XmldbURI[0];

    private String encodedCollectionPath;
    protected boolean hadXmldbPrefix = false;

    protected XmldbURI(final URI xmldbURI) throws URISyntaxException {
        this(xmldbURI, true);
    }

    /**
     * Constructs an XmldbURI from given URI. The provided URI must have the XMLDB_SCHEME ("xmldb")
     *
     * @param xmldbURI      A string
     * @param mustHaveXMLDB true if the provided scheme must be xmldb
     * @throws URISyntaxException If the given string is not a valid xmldb URI.
     */
    protected XmldbURI(URI xmldbURI, final boolean mustHaveXMLDB) throws URISyntaxException {
        final String uriStr = xmldbURI.toString().trim();

        if (!".".equals(uriStr) && !"..".equals(uriStr) && !uriStr.endsWith("/.") && !uriStr.endsWith("/..")) {
            // Only normalize if uri is not "." or ".." or doesn't end with "/." or "/.." .
            // If it's a dot uri, then the final segment is assumed to be a document name
            xmldbURI = xmldbURI.normalize();
        }

        if (xmldbURI.getScheme() != null && mustHaveXMLDB) {

            if (!XMLDB_SCHEME.equals(xmldbURI.getScheme())) {
                throw new URISyntaxException(xmldbURI.toString(), "xmldb URI scheme does not start with " + XMLDB_URI_PREFIX);
            }

            String uri = xmldbURI.toString();
            if (!uri.startsWith(EMBEDDED_SHORT_URI_PREFIX)) {
                xmldbURI = new URI(uri.substring(XMLDB_URI_PREFIX.length()));
            }
            hadXmldbPrefix = true;
        }

        parseURI(xmldbURI, hadXmldbPrefix);

        if (mustHaveXMLDB) {
            hadXmldbPrefix = true;
        }
    }

    protected XmldbURI(final String collectionPath) {
        this.encodedCollectionPath = collectionPath;
    }

    /**
     * Copy Constructor.
     *
     * @param other another XmldbURI to copy.
     */
    private XmldbURI(final XmldbURI other) {
        this.encodedCollectionPath = other.encodedCollectionPath;
        this.hadXmldbPrefix = other.hadXmldbPrefix;
    }

    public static XmldbURI xmldbUriFor(final URI uri) throws URISyntaxException {
        return getXmldbURI(uri);
    }

    public static XmldbURI xmldbUriFor(final String xmldbURI) throws URISyntaxException {
        return xmldbUriFor(xmldbURI, true);
    }

    public static XmldbURI xmldbUriFor(final String xmldbURI, final boolean escape) throws URISyntaxException {
        if (xmldbURI == null) {
            return null;
        }
        final URI uri = new URI(escape ? AnyURIValue.escape(xmldbURI) : xmldbURI);
        return getXmldbURI(uri);
    }

    public static XmldbURI xmldbUriFor(final String xmldbURI, final boolean escape, final boolean mustHaveXMLDB) throws URISyntaxException {
        if (xmldbURI == null) {
            return null;
        }
        final URI uri = new URI(escape ? AnyURIValue.escape(xmldbURI) : xmldbURI);
        return getXmldbURI(uri, mustHaveXMLDB);
    }

    public static XmldbURI xmldbUriFor(final String accessURI, final String collectionPath) throws URISyntaxException {
        if (collectionPath == null) {
            return null;
        }
        final URI uri = new URI(accessURI + URIUtils.iriToURI(collectionPath));
        return getXmldbURI(uri);
    }

    public static XmldbURI create(final URI uri) {
        try {
            return xmldbUriFor(uri);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    public static XmldbURI create(final String uri) {
        try {
            return xmldbUriFor(uri);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    public static XmldbURI create(final String uri, final boolean mustHaveXMLDB) {
        try {
            return xmldbUriFor(uri, true, mustHaveXMLDB);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    public static XmldbURI create(final String accessURI, final String collectionPath) {
        try {
            return xmldbUriFor(accessURI, collectionPath);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    public static XmldbURI createInternal(final String collectionPath) {
        return new XmldbURI(collectionPath);
    }

    private static XmldbURI getXmldbURI(final URI uri) throws URISyntaxException {
        if ((uri.getScheme() != null) || (uri.getFragment() != null) || (uri.getQuery() != null)) {
            return new FullXmldbURI(uri);
        }
        return new XmldbURI(uri);
    }

    private static XmldbURI getXmldbURI(final URI uri, final boolean mustHaveXMLDB) throws URISyntaxException {
        if ((uri.getScheme() != null) || (uri.getFragment() != null) || (uri.getQuery() != null)) {
            return new FullXmldbURI(uri, mustHaveXMLDB);
        }
        return new XmldbURI(uri, mustHaveXMLDB);
    }

    /**
     * Feeds private members. Receives a URI with the xmldb: scheme already stripped
     *
     * @param xmldbURI       the xmldb URI.
     * @param hadXmldbPrefix if the xmldb URI has an xmldb prefix.
     * @throws URISyntaxException if the URI is invalid.
     */
    protected void parseURI(final URI xmldbURI, final boolean hadXmldbPrefix) throws URISyntaxException {
        splitPath(xmldbURI.getRawPath());
    }

    /**
     * Given a java.net.URI.getPath(), <strong>tries</strong> to dispatch the host's context from the collection path as smartly as possible. One
     * would probably prefer a split policy based on the presence of a well-known root collection.
     *
     * @param path The java.net.URI.getPath() provided.
     * @throws URISyntaxException if the URI is invalid.
     */
    protected void splitPath(final String path) throws URISyntaxException {
        encodedCollectionPath = path;

        if ((encodedCollectionPath != null) && (encodedCollectionPath.length() > 1) && encodedCollectionPath.endsWith("/")) {
            encodedCollectionPath = encodedCollectionPath.substring(0, encodedCollectionPath.length() - 1);
        }
        //TODO : check that collectionPath starts with DBBroker.ROOT_COLLECTION ?
    }

    /**
     * To be called before a context operation with another XmldbURI.
     *
     * @param uri the uri
     * @throws IllegalArgumentException if the URI is invalid
     */
    protected void checkCompatibilityForContextOperation(final XmldbURI uri) throws IllegalArgumentException {
        if ((this.getInstanceName() != null) && (uri.getInstanceName() != null) && !this.getInstanceName().equals(uri.getInstanceName())) {
            throw new IllegalArgumentException(this.getInstanceName() + " instance differs from " + uri.getInstanceName());
        }

        //case insensitive comparison
        if ((this.getHost() != null) && (uri.getHost() != null) && !this.getHost().equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException(this.getHost() + " host differs from " + uri.getHost());
        }

        if ((this.getPort() != NO_PORT) && (uri.getPort() != NO_PORT) && (this.getPort() != uri.getPort())) {
            throw new IllegalArgumentException(this.getPort() + " port differs from " + uri.getPort());
        }

        if ((this.getCollectionPath() != null) && (uri.getCollectionPath() != null) && !this.getCollectionPath().equals(uri.getCollectionPath())) {
            throw new IllegalArgumentException(this.getCollectionPath() + " collection differs from " + uri.getCollectionPath());
        }
    }

    /**
     * To be called before a collection path operation with another XmldbURI.
     *
     * @param uri the uri
     * @throws IllegalArgumentException if the uri is invalid
     */
    protected void checkCompatibilityForCollectionOperation(final XmldbURI uri) throws IllegalArgumentException {
        if ((this.getInstanceName() != null) && (uri.getInstanceName() != null) && !this.getInstanceName().equals(uri.getInstanceName())) {
            throw new IllegalArgumentException(this.getInstanceName() + " instance differs from " + uri.getInstanceName());
        }

        //case insensitive comparison
        if ((this.getHost() != null) && (uri.getHost() != null) && !this.getHost().equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException(this.getHost() + " host differs from " + uri.getHost());
        }

        if ((this.getPort() != NO_PORT) && (uri.getPort() != NO_PORT) && (this.getPort() != uri.getPort())) {
            throw new IllegalArgumentException(this.getPort() + " port differs from " + uri.getPort());
        }

        if ((this.getContext() != null) && (uri.getContext() != null) && !this.getContext().equals(uri.getContext())) {
            throw new IllegalArgumentException(this.getContext() + " context differs from " + uri.getContext());
        }
    }

    /**
     * This returns a proper hierarchical URI - the xmldb scheme is trimmed from the beginning. The scheme will be the
     * instance name, and all other fields will be populated as would be expected from a hierarchical URI.
     *
     * @return DOCUMENT ME!
     * @see #getXmldbURI
     */
    public URI getURI() {
        return URI.create(encodedCollectionPath);
    }

    /**
     * This returns an xmldb uri. This is the most generic sort of uri - the only fields set in the uri are scheme and schemeSpecificPart
     *
     * @return DOCUMENT ME!
     */
    public URI getXmldbURI() {
        return URI.create(encodedCollectionPath);
    }

    public String getInstanceName() {
        return null;
    }

    /**
     * Method to return the collection path with reserved characters percent encoded.
     *
     * @return Returns the encoded collection path
     */
    public String getRawCollectionPath() {
        return encodedCollectionPath;
    }

    public String getCollectionPath() {
        if (encodedCollectionPath == null) {
            return null;
        }

        //TODO: we might want to cache this value
        return URLDecoder.decode(encodedCollectionPath, UTF_8);
    }

    public XmldbURI toCollectionPathURI() {
        return this instanceof FullXmldbURI ? XmldbURI.create(getRawCollectionPath()) : this;
    }

    /**
     * To be called each time a private member that interacts with the wrapped URI is modified.
     *
     * @throws URISyntaxException if the URI is invalid.
     */
    protected void recomputeURI() throws URISyntaxException {
    }

    /**
     * To be called each time a private member that interacts with the wrapped URI is modified.
     */
    protected void safeRecomputeURI() {
        try {
            recomputeURI();
        } catch (final URISyntaxException ignored) {
        }
    }


    /*
     * Must be encoded!
     */
    private void setCollectionPath(final String collectionPath) {
        final String oldCollectionPath = encodedCollectionPath;

        try {
            encodedCollectionPath = collectionPath != null && collectionPath.isEmpty() ? null : collectionPath;

            //include root slash if we have a context
            if ((encodedCollectionPath != null) && ((getContext() != null) && (encodedCollectionPath.charAt(0) != '/'))) {
                encodedCollectionPath = "/" + encodedCollectionPath;
            }
            recomputeURI();
        } catch (final URISyntaxException e) {
            encodedCollectionPath = oldCollectionPath;
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage());
        }
    }

    public String getApiName() {
        return null;
    }

    public String getContext() {
        return null;
    }

    @Override
    public int compareTo(final Object ob) throws ClassCastException {
        if (!(ob instanceof XmldbURI)) {
            throw new ClassCastException("The provided Object is not an XmldbURI");
        }
        return getXmldbURI().compareTo(((XmldbURI) ob).getXmldbURI());
    }

    /**
     * This function returns a relative XmldbURI with the value after the last / in the collection path of the URI.
     *
     * @return A relative XmldbURI containing the value after the last / in the collection path
     */
    public XmldbURI lastSegment() {
        String name = getRawCollectionPath();
        int last;

        // No slash - give them the whole thing!
        if ((last = name.lastIndexOf('/')) == Constants.STRING_NOT_FOUND) {
            return this;
        }

        // Checks against a trailing slash
        // is this appropriate?
        if (last == (name.length() - 1)) {
            name = name.substring(0, last);
            last = name.lastIndexOf('/');
        }
        return new XmldbURI(name.substring(last + 1));
    }

    public String lastSegmentString() {
        String name = getRawCollectionPath();
        int last;

        // No slash - give them the whole thing!
        if ((last = name.lastIndexOf('/')) == Constants.STRING_NOT_FOUND) {
            return name;
        }

        // Checks against a trailing slash
        // is this appropriate?
        if (last == (name.length() - 1)) {
            name = name.substring(0, last);
            last = name.lastIndexOf('/');
        }

        return name.substring(last + 1);
    }

    /**
     * This function returns a relative XmldbURI with the value after the last / in the collection path of the URI.
     *
     * @return A relative XmldbURI containing the value after the last / in the collection path
     */
    public int numSegments() {
        final String name = getRawCollectionPath();

        if ((name == null) || name.isEmpty()) {
            return 0;
        }
        final String[] split = name.split("/");
        return split.length;
    }

    /**
     * This function returns a relative XmldbURI with the value after the last / in the collection path of the URI.
     *
     * @return A relative XmldbURI containing the value after the last / in the collection path
     */
    public XmldbURI[] getPathSegments() {
        final String name = getRawCollectionPath();

        if ((name == null) || name.isEmpty()) {
            return NO_SEGMENTS;
        }

        final String[] split = name.split("/");
        if (split.length == 0) {
            return NO_SEGMENTS;
        }

        final int fix = ("".equals(split[0])) ? 1 : 0;
        final XmldbURI[] segments = new XmldbURI[split.length - fix];

        for (int i = fix; i < split.length; i++) {
            segments[i - fix] = XmldbURI.create(split[i]);
        }
        return segments;
    }

    /**
     * This function returns a string with everything after the last / removed.
     *
     * @return A relative XmldbURI containing the value after the last / in the collection path
     */
    public XmldbURI removeLastSegment() {
        String uri = toString();
        int last;

        // No slash - return null!
        if ((last = uri.lastIndexOf('/')) == Constants.STRING_NOT_FOUND) {
            return XmldbURI.EMPTY_URI;
        }

        // Checks against a trailing slash
        // is this appropriate?
        if (last == (uri.length() - 1)) {
            uri = uri.substring(0, last);
            last = uri.lastIndexOf('/');
        }
        return (last <= 0) ? XmldbURI.EMPTY_URI : XmldbURI.create(uri.substring(0, last), hadXmldbPrefix);
    }

    public XmldbURI append(final String uri) {
        return append(XmldbURI.create(uri));
    }

    public XmldbURI append(final XmldbURI uri) {
        final String toAppend = uri.getRawCollectionPath();
        final String prepend = toString();

        if (toAppend.isEmpty()) {
            return this;
        }

        if (prepend.isEmpty()) {
            return uri;
        }

        if (prepend.charAt(prepend.length() - 1) != '/' && toAppend.charAt(0) != '/') {
            return XmldbURI.create(prepend + "/" + toAppend, hadXmldbPrefix);
        } else {
            return XmldbURI.create(prepend + toAppend, hadXmldbPrefix);
        }
    }

    public XmldbURI appendInternal(final XmldbURI uri) {
        return XmldbURI.createInternal(getRawCollectionPath() + '/' + uri.getRawCollectionPath());
    }

    /**
     * Ugly workaround for non-URI compliant pathes.
     *
     * @param pseudoURI What is supposed to be a URI
     * @return a supposedly correctly escaped URI <strong>string representation</strong>
     * @throws URISyntaxException if the URI is invalid.
     * @deprecated By definition, using this method is strongly discouraged
     */
    @Deprecated
    public static String recoverPseudoURIs(final String pseudoURI) throws URISyntaxException {
        final Pattern p = Pattern.compile("/");
        final String[] parts = p.split(pseudoURI);
        final StringBuilder newURIString = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            newURIString.append("/");

            if (!parts[i].isEmpty()) {
                try {
                    //Try to instantiate the parts as a URI
                    new URI(newURIString + parts[i]);
                    newURIString.append(parts[i]);
                } catch (final URISyntaxException e) {
                    LOG.info("Trying to escape : ''{}' in '{}' !", parts[i], pseudoURI);
                    newURIString.append(URIUtils.encodeForURI(parts[i]));
                }
            }
        }
        return newURIString.toString();
    }

    public boolean equals(final Object ob) {
        if (ob instanceof XmldbURI) {
            return getXmldbURI().equals(((XmldbURI) ob).getXmldbURI());
        }

        if (ob instanceof URI) {
            return getXmldbURI().equals(ob);
        }

        if (ob instanceof String) {
            try {
                return getXmldbURI().equals(new URI((String) ob));
            } catch (final URISyntaxException e) {
                return false;
            }
        }
        return false;
    }

    public boolean equalsInternal(final XmldbURI other) {
        if (this == other) {
            return true;
        }
        return encodedCollectionPath.equals(other.encodedCollectionPath);
    }

    public boolean isAbsolute() {
        return isCollectionPathAbsolute();
    }

    public boolean isContextAbsolute() {
        return false;
    }

    public XmldbURI normalizeContext() {
        return this;
    }

    public URI relativizeContext(final URI uri) {
        return null;
    }

    public URI resolveContext(final String str) throws NullPointerException, IllegalArgumentException {
        return null;
    }

    public URI resolveContext(final URI uri) throws NullPointerException {
        return null;
    }

    public boolean isCollectionPathAbsolute() {
        return (encodedCollectionPath != null) && (encodedCollectionPath.length() > 0) && (encodedCollectionPath.charAt(0) == '/');
    }

    public XmldbURI normalizeCollectionPath() {
        final String collectionPath = this.encodedCollectionPath;

        if (collectionPath == null) {
            return this;
        }
        final URI collectionPathURI = URI.create(collectionPath).normalize();

        if (collectionPathURI.getPath().equals(collectionPath)) {
            return this;
        }
        final XmldbURI uri = XmldbURI.create(getXmldbURI());
        uri.setCollectionPath(collectionPathURI.toString());
        return uri;
    }

    public URI relativizeCollectionPath(final URI uri) {
        if (uri == null) {
            throw new NullPointerException("The provided URI is null");
        }
        final String collectionPath = this.encodedCollectionPath;

        if (collectionPath == null) {
            throw new NullPointerException("The current collection path is null");
        }
        URI collectionPathURI;

        //Adds a final slash if necessary
        if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '{}'", collectionPath);
            collectionPathURI = URI.create(collectionPath + "/");
        } else {
            collectionPathURI = URI.create(collectionPath);
        }
        return collectionPathURI.relativize(uri);
    }

    //TODO: unit test!
    public XmldbURI resolveCollectionPath(final XmldbURI child) throws NullPointerException, IllegalArgumentException {
        if (child == null) {
            throw new NullPointerException("The provided child URI is null");
        }

        final String collectionPath = toCollectionPathURI().toString();
        URI newCollectionURI;

        if (!collectionPath.endsWith("/")) {
            newCollectionURI = URI.create(collectionPath + "/").resolve(child.toCollectionPathURI().getURI());
        } else {
            newCollectionURI = getURI().resolve(child.toCollectionPathURI().getURI());
        }

        final XmldbURI newURI = XmldbURI.create(getXmldbURI());
        String newCollectionPath = newCollectionURI.getRawPath();

        if (newCollectionPath.endsWith("/")) {
            newCollectionPath = newCollectionPath.substring(0, newCollectionPath.length() - 1);
        }
        newURI.encodedCollectionPath = newCollectionPath;
        newURI.safeRecomputeURI();
        return newURI;
    }

    public URI resolveCollectionPath(final URI uri) throws NullPointerException {
        if (uri == null) {
            throw new NullPointerException("The provided URI is null");
        }
        final String collectionPath = this.encodedCollectionPath;

        if (collectionPath == null) {
            throw new NullPointerException("The current collection path is null");
        }

        final URI collectionPathURI;

        //Adds a final slash if necessary
        if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '{}'", collectionPath);
            collectionPathURI = URI.create(collectionPath + "/");
        } else {
            collectionPathURI = URI.create(collectionPath);
        }
        return collectionPathURI.resolve(uri);
    }

    public String toASCIIString() {
        //TODO : trim trailing slash if necessary
        return getXmldbURI().toASCIIString();
    }

    public URL toURL() throws IllegalArgumentException, MalformedURLException {
        return getXmldbURI().toURL();
    }

    public boolean startsWith(final XmldbURI prefix) {
        if (prefix == null) {
            return false;
        }

        final XmldbURI[] segments = getPathSegments();
        final XmldbURI[] prefix_segments = prefix.getPathSegments();

        if (prefix_segments.length > segments.length) {
            return false;
        }

        for (int i = 0; i < prefix_segments.length; i++) {
            if (!prefix_segments[i].equalsInternal(segments[i])) {
                return false;
            }
        }

        return true;
    }

    public boolean startsWith(final String string) throws URISyntaxException {
        return startsWith(XmldbURI.xmldbUriFor(string));
    }

    //TODO: add unit test for this
    public boolean endsWith(final XmldbURI xmldbUri) {
        return xmldbUri != null && toString().endsWith(xmldbUri.toString());
    }

    public boolean endsWith(final String string) throws URISyntaxException {
        return endsWith(XmldbURI.xmldbUriFor(string));
    }

    //TODO: add unit test for this
    public XmldbURI prepend(final XmldbURI xmldbUri) {
        if (xmldbUri == null) {
            throw new NullPointerException(toString() + " cannot start with null!");
        }

        //TODO : resolve URIs !!! xmldbUri.resolve(this)
        return xmldbUri.append(this);
    }

    //TODO: add unit test for this
    public XmldbURI trimFromBeginning(final XmldbURI xmldbUri) {
        if (xmldbUri == null) {
            throw new NullPointerException(toString() + " cannot start with null!");
        }

        if (!startsWith(xmldbUri)) {
            throw new IllegalArgumentException(toString() + " does not start with " + xmldbUri.toString());
        }
        return XmldbURI.create(toString().substring(xmldbUri.toString().length()));
    }

    public XmldbURI trimFromBeginning(final String string) throws URISyntaxException {
        return trimFromBeginning(XmldbURI.xmldbUriFor(string));
    }

    @Override
    public String toString() {
        return encodedCollectionPath;
    }

    public static String[] getPathComponents(final String collectionPath) {
        final Pattern p = Pattern.compile("/");
        final String[] split = p.split(collectionPath);
        final String[] result = new String[split.length - 1];
        System.arraycopy(split, 1, result, 0, split.length - 1);
        return result;
    }

    public String getAuthority() {
        return null;
    }

    public String getFragment() {
        return null;
    }

    public int getPort() {
        return NO_PORT;
    }

    public String getQuery() {
        return null;
    }

    public String getRawAuthority() {
        return null;
    }

    public String getHost() {
        return null;
    }

    public String getUserInfo() {
        return null;
    }

    public String getRawFragment() {
        return null;
    }

    public String getRawQuery() {
        return null;
    }

    public String getRawUserInfo() {
        return null;
    }

    @Override
    public int hashCode() {
        return getXmldbURI().hashCode();
    }

    @Override
    public Object clone() {
        return new XmldbURI(this);
    }
}
