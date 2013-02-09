/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; er version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xmldb;

import org.apache.log4j.Logger;

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

/**
 * A utility class for xmldb URis. Since, java.net.URI is <strong>final</strong> this class acts as a wrapper.
 *
 * @author  Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
/*
 * This base class implementation only provides a path.  FullXmldbURI provides
 * full uri support.  The create method creates a minimal object to keep memory
 * usage low.
 */
public class XmldbURI implements Comparable<Object>, Serializable {

    protected final static Logger LOG = Logger.getLogger(XmldbURI.class);
    public static final int NO_PORT = -1;
    //Should be provided by org.xmldb.api package !!!
    public static final String XMLDB_SCHEME = "xmldb";
    public static final String XMLDB_URI_PREFIX = "xmldb:";
    public static final String DEFAULT_INSTANCE_NAME = "exist";
    public static final String EMBEDDED_SERVER_AUTHORITY = "embedded-eXist-server";
    public static final String EMBEDDED_SERVER_URI_PREFIX = XMLDB_URI_PREFIX + DEFAULT_INSTANCE_NAME + "://";
    /** 'db' collection name */
    public final static String ROOT_COLLECTION_NAME = "db";
    /** '/db' collection name */
    public final static String ROOT_COLLECTION = "/" + ROOT_COLLECTION_NAME;
    /** 'system' collection name */
    public final static String SYSTEM_COLLECTION_NAME = "system";
    /** '/db/system' collection name */
    public final static String SYSTEM_COLLECTION = ROOT_COLLECTION + "/" + SYSTEM_COLLECTION_NAME;
    /** 'temp' collection name */
    public final static String TEMP_COLLECTION_NAME = "temp";
    /** '/db/system/temp' collection name */
    public final static String TEMP_COLLECTION = SYSTEM_COLLECTION + "/" + TEMP_COLLECTION_NAME;

    @Deprecated
    /** '/db/system/config' collection name */
    public final static String CONFIG_COLLECTION = SYSTEM_COLLECTION + "/config";
    
    /** '/db' collection **/
    public final static XmldbURI DB = create(ROOT_COLLECTION);
    public final static XmldbURI ROOT_COLLECTION_URI = create(ROOT_COLLECTION);
    public final static XmldbURI RELATIVE_ROOT_COLLECTION_URI = create(ROOT_COLLECTION_NAME);

    /** '/db/system' **/
    public final static XmldbURI SYSTEM = create(SYSTEM_COLLECTION);
    /** '/db/system' **/
    @Deprecated
    public final static XmldbURI SYSTEM_COLLECTION_URI = create(SYSTEM_COLLECTION);
    
    @Deprecated
    public final static XmldbURI CONFIG_COLLECTION_URI = create(CONFIG_COLLECTION);
    
    public final static XmldbURI TEMP_COLLECTION_URI = create(TEMP_COLLECTION);
    public final static XmldbURI EMPTY_URI = createInternal("");
    public static final XmldbURI EMBEDDED_SERVER_URI = XmldbURI.create(EMBEDDED_SERVER_URI_PREFIX + EMBEDDED_SERVER_AUTHORITY);
    /** 'xmldb:exist///db' */
    public static final String LOCAL_DB = EMBEDDED_SERVER_URI_PREFIX + ROOT_COLLECTION;
    /** 'xmldb:exist///db' XmldbURI */
    public static final XmldbURI LOCAL_DB_URI = XmldbURI.create(EMBEDDED_SERVER_URI_PREFIX + ROOT_COLLECTION);
    //TODO : deprecate when we split at root collection
    public final static String API_XMLRPC = "xmlrpc";
    public final static String API_WEBDAV = "webdav";
    public final static String API_REST = "rest-style";
    public final static String API_LOCAL = "local";
    
    private String encodedCollectionPath;
    protected boolean hadXmldbPrefix = false;

    protected XmldbURI(URI xmldbURI) throws URISyntaxException {
    	this(xmldbURI, true);
    }

    /**
     * Contructs an XmldbURI from given URI. The provided URI must have the XMLDB_SCHEME ("xmldb")
     *
     * @param   xmldbURI  A string
     *
     * @throws  URISyntaxException  If the given string is not a valid xmldb URI.
     */
    protected XmldbURI(URI xmldbURI, boolean mustHaveXMLDB) throws URISyntaxException {
        final String uriStr = xmldbURI.toString().trim();

        if (!".".equals(uriStr) && !"..".equals(uriStr) && !uriStr.endsWith("/.") && !uriStr.endsWith("/..")) {
            // Only normalize if uri is not "." or ".." or doesn't end with "/." or "/.." .  If it's a dot uri, then the final segment is assumed to be a document name
            xmldbURI = xmldbURI.normalize();
        }

        if (xmldbURI.getScheme() != null && mustHaveXMLDB) {

            if (!XMLDB_SCHEME.equals(xmldbURI.getScheme())) {
                throw (new URISyntaxException(xmldbURI.toString(), "xmldb URI scheme does not start with " + XMLDB_URI_PREFIX));
            }
            xmldbURI = new URI(xmldbURI.toString().substring(XMLDB_URI_PREFIX.length()));
            hadXmldbPrefix = true;
        }
        
        parseURI(xmldbURI, hadXmldbPrefix);

        if (mustHaveXMLDB) {hadXmldbPrefix = true;}
    }

    protected XmldbURI(String collectionPath) {
        this.encodedCollectionPath = collectionPath;
    }

    public static XmldbURI xmldbUriFor(URI uri) throws URISyntaxException {
        return (getXmldbURI(uri));
    }

    public static XmldbURI xmldbUriFor(String xmldbURI) throws URISyntaxException {
        return (xmldbUriFor(xmldbURI, true));
    }

    public static XmldbURI xmldbUriFor(String xmldbURI, boolean escape) throws URISyntaxException {
        if (xmldbURI == null) {
            return (null);
        }
        final URI uri = new URI(escape ? AnyURIValue.escape(xmldbURI) : xmldbURI);
        return (getXmldbURI(uri));
    }

    public static XmldbURI xmldbUriFor(String xmldbURI, boolean escape, boolean mustHaveXMLDB) throws URISyntaxException {
        if (xmldbURI == null) {
            return (null);
        }
        final URI uri = new URI(escape ? AnyURIValue.escape(xmldbURI) : xmldbURI);
        return (getXmldbURI(uri, mustHaveXMLDB));
    }

    public static XmldbURI xmldbUriFor(String accessURI, String collectionPath) throws URISyntaxException {
        if (collectionPath == null) {
            return (null);
        }
        final URI uri = new URI(accessURI + URIUtils.iriToURI(collectionPath));
        return (getXmldbURI(uri));
    }

    public static XmldbURI create(URI uri) {
        try {
            return (xmldbUriFor(uri));
        } catch (final URISyntaxException e) {
            throw (new IllegalArgumentException("Invalid URI: " + e.getMessage()));
        }
    }

    public static XmldbURI create(String uri) {
        try {
            return (xmldbUriFor(uri));
        } catch (final URISyntaxException e) {
            throw (new IllegalArgumentException("Invalid URI: " + e.getMessage()));
        }
    }

    public static XmldbURI create(String uri, boolean mustHaveXMLDB) {
        try {
            return (xmldbUriFor(uri, true, mustHaveXMLDB));
        } catch (final URISyntaxException e) {
            throw (new IllegalArgumentException("Invalid URI: " + e.getMessage()));
        }
    }

    public static XmldbURI create(String accessURI, String collectionPath) {
        try {
            return (xmldbUriFor(accessURI, collectionPath));
        } catch (final URISyntaxException e) {
            throw (new IllegalArgumentException("Invalid URI: " + e.getMessage()));
        }
    }

    public static XmldbURI createInternal(String collectionPath) {
        return (new XmldbURI(collectionPath));
    }

    private static XmldbURI getXmldbURI(URI uri) throws URISyntaxException {
        if ((uri.getScheme() != null) || (uri.getFragment() != null) || (uri.getQuery() != null)) {
            return (new FullXmldbURI(uri));
        }
        return (new XmldbURI(uri));
        /*
        //TODO : get rid of this and use a more robust approach (dedicated constructor ?) -pb
        //TODO : use named constants
        index = path.lastIndexOf("/xmlrpc");
        if (index > lastIndex) {
        return false;
        }
        //TODO : use named constants
        index = path.lastIndexOf("/webdav");
        if (index > lastIndex) {
        return false;
        }
         */
    }

    private static XmldbURI getXmldbURI(URI uri, boolean mustHaveXMLDB) throws URISyntaxException {
        if ((uri.getScheme() != null) || (uri.getFragment() != null) || (uri.getQuery() != null)) {
            return (new FullXmldbURI(uri, mustHaveXMLDB));
        }
        return (new XmldbURI(uri, mustHaveXMLDB));
    }

    /**
     * Feeds private members. Receives a URI with the xmldb: scheme already stripped
     *
     * @param   xmldbURI        DOCUMENT ME!
     * @param   hadXmldbPrefix  DOCUMENT ME!
     *
     * @throws  URISyntaxException
     */
    protected void parseURI(URI xmldbURI, boolean hadXmldbPrefix) throws URISyntaxException {
        splitPath(xmldbURI.getRawPath());
    }

    /**
     * Given a java.net.URI.getPath(), <strong>tries</strong> to dispatch the host's context from the collection path as smartly as possible. One
     * would probably prefer a split policy based on the presence of a well-known root collection.
     *
     * @param   path  The java.net.URI.getPath() provided.
     *
     * @throws  URISyntaxException
     */
    protected void splitPath(String path) throws URISyntaxException {
        encodedCollectionPath = path;

        if ((encodedCollectionPath != null) && (encodedCollectionPath.length() > 1) && encodedCollectionPath.endsWith("/")) {
            encodedCollectionPath = encodedCollectionPath.substring(0, encodedCollectionPath.length() - 1);
        }
        //TODO : check that collectionPath starts with DBBroker.ROOT_COLLECTION ?
    }

    /**
     * To be called before a context operation with another XmldbURI.
     *
     * @param   uri
     *
     * @throws  IllegalArgumentException
     */
    protected void checkCompatibilityForContextOperation(XmldbURI uri) throws IllegalArgumentException {
        if ((this.getInstanceName() != null) && (uri.getInstanceName() != null) && !this.getInstanceName().equals(uri.getInstanceName())) {
            throw (new IllegalArgumentException(this.getInstanceName() + " instance differs from " + uri.getInstanceName()));
        }

        //case insensitive comparison
        if ((this.getHost() != null) && (uri.getHost() != null) && !this.getHost().equalsIgnoreCase(uri.getHost())) {
            throw (new IllegalArgumentException(this.getHost() + " host differs from " + uri.getHost()));
        }

        if ((this.getPort() != NO_PORT) && (uri.getPort() != NO_PORT) && (this.getPort() != uri.getPort())) {
            throw (new IllegalArgumentException(this.getPort() + " port differs from " + uri.getPort()));
        }

        if ((this.getCollectionPath() != null) && (uri.getCollectionPath() != null) && !this.getCollectionPath().equals(uri.getCollectionPath())) {
            throw (new IllegalArgumentException(this.getCollectionPath() + " collection differs from " + uri.getCollectionPath()));
        }
    }

    /**
     * To be called before a collection path operation with another XmldbURI.
     *
     * @param   uri
     *
     * @throws  IllegalArgumentException
     */
    protected void checkCompatibilityForCollectionOperation(XmldbURI uri) throws IllegalArgumentException {
        if ((this.getInstanceName() != null) && (uri.getInstanceName() != null) && !this.getInstanceName().equals(uri.getInstanceName())) {
            throw (new IllegalArgumentException(this.getInstanceName() + " instance differs from " + uri.getInstanceName()));
        }

        //case insensitive comparison
        if ((this.getHost() != null) && (uri.getHost() != null) && !this.getHost().equalsIgnoreCase(uri.getHost())) {
            throw (new IllegalArgumentException(this.getHost() + " host differs from " + uri.getHost()));
        }

        if ((this.getPort() != NO_PORT) && (uri.getPort() != NO_PORT) && (this.getPort() != uri.getPort())) {
            throw (new IllegalArgumentException(this.getPort() + " port differs from " + uri.getPort()));
        }

        if ((this.getContext() != null) && (uri.getContext() != null) && !this.getContext().equals(uri.getContext())) {
            throw (new IllegalArgumentException(this.getContext() + " context differs from " + uri.getContext()));
        }
    }


    /*
     * It is an error for any of the following private members to throw an exception.
     */
    /*
    private void setInstanceName(String instanceName) {
    String oldInstanceName = this.instanceName;
    try {
    this.instanceName = instanceName;
    recomputeURI();
    } catch (URISyntaxException e) {
    this.instanceName = oldInstanceName;
    throw new IllegalArgumentException("Invalid URI: "+e.getMessage());
    }
    }

    private void setContext(String context) throws URISyntaxException {
    String oldContext = this.context;
    try {
    //trims any trailing slash
    if (context != null && context.endsWith("/")) {
    //include root slash if we have a host
    if (this.getHost() != null)
    context = context.substring(0, context.length() - 1);
    }
    this.context = "".equals(context) ? null : context;
    recomputeURI();
    } catch (URISyntaxException e) {
    this.context = oldContext;
    throw e;
    }
    }

    private void setCollectionPath(String collectionPath) throws URISyntaxException {
    String oldCollectionPath = collectionPath;
    try {
    if (collectionPath == null)
    this.encodedCollectionPath = null;
    else {
    String escaped = URIUtils.escapeHtmlURI(collectionPath);
    this.encodedCollectionPath = escaped;
    }
    recomputeURI();
    } catch (URISyntaxException e) {
    this.encodedCollectionPath = oldCollectionPath;
    throw e;
    } catch (UnsupportedEncodingException e) {
    wrappedURI = null;
    throw new URISyntaxException(this.toString(), e.getMessage());
    }
    }
     */
    /**
     * This returns a proper heirarchical URI - the xmldb scheme is trimmed from the beginning. The scheme will be the instance name, and all other
     * fields will be populated as would be expected from a heirarchical URI
     *
     * @return  DOCUMENT ME!
     *
     * @see     #getXmldbURI
     */
    public URI getURI() {
        return (URI.create(encodedCollectionPath));
    }

    /**
     * This returns an xmldb uri. This is the most generic sort of uri - the only fields set in the uri are scheme and schemeSpecificPart
     *
     * @return  DOCUMENT ME!
     */
    public URI getXmldbURI() {
        return (URI.create(encodedCollectionPath));
    }

    public String getInstanceName() {
        return (null);
    }

    /**
     * Method to return the collection path with reserved characters percent encoded.
     *
     * @return  Returns the encoded collection path
     */
    public String getRawCollectionPath() {
        return (encodedCollectionPath);
    }

    public String getCollectionPath() {
        if (encodedCollectionPath == null) {
            return (null);
        }

        try {

            //TODO: we might want to cache this value
            return (URLDecoder.decode(encodedCollectionPath, "UTF-8"));
        } catch (final UnsupportedEncodingException e) {

            //Should never happen
            throw (new IllegalArgumentException(encodedCollectionPath + " can not be properly escaped"));
        }
    }

    public XmldbURI toCollectionPathURI() {
        return ((this instanceof FullXmldbURI) ? XmldbURI.create(getRawCollectionPath()) : this);
    }

    /**
     * To be called each time a private member that interacts with the wrapped URI is modified.
     *
     * @throws  URISyntaxException
     */
    protected void recomputeURI() throws URISyntaxException {
    }

    /**
     * To be called each time a private member that interacts with the wrapped URI is modified.
     */
    protected void safeRecomputeURI() {
        try {
            recomputeURI();
        } catch (final URISyntaxException e) {
        }
    }


    /*
     * Must be encoded!
     */
    private void setCollectionPath(String collectionPath) {
        String oldCollectionPath = encodedCollectionPath;

        try {
            encodedCollectionPath = "".equals(collectionPath) ? null : collectionPath;

            //include root slash if we have a context
            if ((encodedCollectionPath != null) && ((getContext() != null) & (encodedCollectionPath.charAt(0) != '/'))) {
                encodedCollectionPath = "/" + encodedCollectionPath;
            }
            recomputeURI();
        } catch (final URISyntaxException e) {
            encodedCollectionPath = oldCollectionPath;
            throw (new IllegalArgumentException("Invalid URI: " + e.getMessage()));
        }
    }

    public String getApiName() {
        return (null);
    }

    public String getContext() {
        return (null);
    }

    public int compareTo(Object ob) throws ClassCastException {
        if (!(ob instanceof XmldbURI)) {
            throw (new ClassCastException("The provided Object is not an XmldbURI"));
        }
        return (getXmldbURI().compareTo(((XmldbURI) ob).getXmldbURI()));
    }

    /**
     * This function returns a relative XmldbURI with the value after the last / in the collection path of the URI.
     *
     * @return  A relative XmldbURI containing the value after the last / in the collection path
     */
    public XmldbURI lastSegment() {
        String name = getRawCollectionPath();
        int last;

        // No slash - give them the whole thing!
        if ((last = name.lastIndexOf('/')) == Constants.STRING_NOT_FOUND) {
            return (this);
        }

        // Checks against a trailing slash
        // is this appropriate?
        if (last == (name.length() - 1)) {
            name = name.substring(0, last);
            last = name.lastIndexOf('/');
        }
        return (XmldbURI.create(name.substring(last + 1)));
    }

    /**
     * This function returns a relative XmldbURI with the value after the last / in the collection path of the URI.
     *
     * @return  A relative XmldbURI containing the value after the last / in the collection path
     */
    public int numSegments() {
        final String name = getRawCollectionPath();

        if ((name == null) || "".equals(name)) {
            return (0);
        }
        final String[] split = name.split("/");
        return (split.length);
    }

    /**
     * This function returns a relative XmldbURI with the value after the last / in the collection path of the URI.
     *
     * @return  A relative XmldbURI containing the value after the last / in the collection path
     */
    public XmldbURI[] getPathSegments() {
        final String name = getRawCollectionPath();

        if ((name == null) || "".equals(name)) {
            return (new XmldbURI[0]);
        }
        final String[] split = name.split("/");
        final int fix = ("".equals(split[0])) ? 1 : 0;
        final XmldbURI[] segments = new XmldbURI[split.length - fix];

        for (int i = fix; i < split.length; i++) {
            segments[i - fix] = XmldbURI.create(split[i]);
        }
        return (segments);
    }

    /**
     * This function returns a string with everything after the last / removed.
     *
     * @return  A relative XmldbURI containing the value after the last / in the collection path
     */
    public XmldbURI removeLastSegment() {
        String uri = toString();
        int last;

        // No slash - return null!
        if ((last = uri.lastIndexOf('/')) == Constants.STRING_NOT_FOUND) {
            return (XmldbURI.EMPTY_URI);
        }

        // Checks against a trailing slash
        // is this appropriate?
        if (last == (uri.length() - 1)) {
            uri = uri.substring(0, last);
            last = uri.lastIndexOf('/');
        }
        return ((last <= 0) ? XmldbURI.EMPTY_URI : XmldbURI.create(uri.substring(0, last), hadXmldbPrefix));
    }

    public XmldbURI append(String uri) {
        return (append(XmldbURI.create(uri)));
    }

    public XmldbURI append(XmldbURI uri) {
        final String toAppend = uri.getRawCollectionPath();
        final String prepend = toString();

        if ("".equals(toAppend)) {
            return (this);
        }

        if ("".equals(prepend)) {
            return (uri);
        }

        if (!(prepend.charAt(prepend.length() - 1) == '/') && !(toAppend.charAt(0) == '/')) {
            return (XmldbURI.create(prepend + "/" + toAppend, hadXmldbPrefix));
        } else {
            return (XmldbURI.create(prepend + toAppend, hadXmldbPrefix));
        }
    }

    public XmldbURI appendInternal(XmldbURI uri) {
        return (XmldbURI.createInternal(getRawCollectionPath() + '/' + uri.getRawCollectionPath()));
    }

    /**
     * Ugly workaround for non-URI compliant pathes.
     *
     * @param       pseudoURI  What is supposed to be a URI
     *
     * @return      an supposedly correctly escaped URI <strong>string representation</string></strong>
     *
     * @throws      URISyntaxException  DOCUMENT ME!
     *
     * @deprecated  By definition, using this method is strongly discouraged
     */
    public static String recoverPseudoURIs(String pseudoURI) throws URISyntaxException {
        final Pattern p = Pattern.compile("/");
        final String[] parts = p.split(pseudoURI);
        final StringBuilder newURIString = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            newURIString.append("/");

            if (!"".equals(parts[i])) {

                try {

                    //Try to instantiate the parst as a URI
                    new URI(newURIString + parts[i]);
                    newURIString.append(parts[i]);
                } catch (final URISyntaxException e) {
                    LOG.info("Trying to escape : ''" + parts[i] + "' in '" + pseudoURI + "' !");
                    newURIString.append(URIUtils.encodeForURI(parts[i]));
                }
            }
        }
        return (newURIString.toString());
    }

    public boolean equals(Object ob) {
        if (ob instanceof XmldbURI) {
            return (getXmldbURI().equals(((XmldbURI) ob).getXmldbURI()));
        }

        if (ob instanceof URI) {
            return (getXmldbURI().equals(ob));
        }

        if (ob instanceof String) {

            try {
                return (getXmldbURI().equals(new URI((String) ob)));
            } catch (final URISyntaxException e) {
                return (false);
            }
        }
        return (false);
    }

    public boolean equalsInternal(XmldbURI other) {
        if (this == other) {
            return (true);
        }
        return (encodedCollectionPath.equals(other.encodedCollectionPath));
    }

    public boolean isAbsolute() {
        return (isCollectionPathAbsolute());
    }

    public boolean isContextAbsolute() {
        return (false);
    }

    public XmldbURI normalizeContext() {
        return (this);
    }

    public URI relativizeContext(URI uri) {
        return (null);
    }

    public URI resolveContext(String str) throws NullPointerException, IllegalArgumentException {
        return (null);
    }

    public URI resolveContext(URI uri) throws NullPointerException {
        return (null);
    }

    public boolean isCollectionPathAbsolute() {
        return ((encodedCollectionPath != null) && (encodedCollectionPath.length() > 0) && (encodedCollectionPath.charAt(0) == '/'));
    }

    public XmldbURI normalizeCollectionPath() {
        final String collectionPath = this.encodedCollectionPath;

        if (collectionPath == null) {
            return (this);
        }
        final URI collectionPathURI = URI.create(collectionPath).normalize();

        if (collectionPathURI.getPath().equals(collectionPath)) {
            return (this);
        }
        final XmldbURI uri = XmldbURI.create(getXmldbURI());
        uri.setCollectionPath(collectionPathURI.toString());
        return (uri);
    }

    public URI relativizeCollectionPath(URI uri) {
        if (uri == null) {
            throw (new NullPointerException("The provided URI is null"));
        }
        final String collectionPath = this.encodedCollectionPath;

        if (collectionPath == null) {
            throw (new NullPointerException("The current collection path is null"));
        }
        URI collectionPathURI;

        //Adds a final slash if necessary
        if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '" + collectionPath + "'");
            collectionPathURI = URI.create(collectionPath + "/");
        } else {
            collectionPathURI = URI.create(collectionPath);
        }
        return (collectionPathURI.relativize(uri));
    }

    //TODO: unit test!
    public XmldbURI resolveCollectionPath(XmldbURI child) throws NullPointerException, IllegalArgumentException {
        if (child == null) {
            throw (new NullPointerException("The provided child URI is null"));
        }
//        if (child.isAbsolute())
//            return child;
        //Old method:
        /*
        String collectionPath = this.encodedCollectionPath;
        if (collectionPath == null)
        throw new NullPointerException("The current collection path is null");
        URI collectionPathURI;
        //Adds a final slash if necessary
        if (!collectionPath.endsWith("/")) {
        LOG.info("Added a final '/' to '" + collectionPath + "'");
        collectionPathURI = URI.create(collectionPath + "/");
        } else
        collectionPathURI = URI.create(collectionPath);
         */

        final String collectionPath = toCollectionPathURI().toString();
        URI newCollectionURI = null;

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
        return (newURI);
    }

    public URI resolveCollectionPath(URI uri) throws NullPointerException {
        if (uri == null) {
            throw (new NullPointerException("The provided URI is null"));
        }
        final String collectionPath = this.encodedCollectionPath;

        if (collectionPath == null) {
            throw (new NullPointerException("The current collection path is null"));
        }
        URI collectionPathURI;

        //Adds a final slash if necessary
        if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '" + collectionPath + "'");
            collectionPathURI = URI.create(collectionPath + "/");
        } else {
            collectionPathURI = URI.create(collectionPath);
        }
        return (collectionPathURI.resolve(uri));
    }

    public String toASCIIString() {
        //TODO : trim trailing slash if necessary
        return (getXmldbURI().toASCIIString());
    }

    public URL toURL() throws IllegalArgumentException, MalformedURLException {
        return (getXmldbURI().toURL());
    }

    //TODO: add unit test for this
    //TODO : come on ! use a URI method name.
    //resolve() is a must here
    public boolean startsWith(XmldbURI xmldbUri) {
        return ((xmldbUri == null) ? false : toString().startsWith(xmldbUri.toString()));
    }

    //TODO : come on ! use a URI method name.
    //resolve() is a must here
    public boolean startsWith(String string) throws URISyntaxException {
        return (startsWith(XmldbURI.xmldbUriFor(string)));
    }

    //TODO: add unit test for this
    public boolean endsWith(XmldbURI xmldbUri) {
        return ((xmldbUri == null) ? false : toString().endsWith(xmldbUri.toString()));
    }

    public boolean endsWith(String string) throws URISyntaxException {
        return (endsWith(XmldbURI.xmldbUriFor(string)));
    }

    //TODO: add unit test for this
    public XmldbURI prepend(XmldbURI xmldbUri) {
        if (xmldbUri == null) {
            throw (new NullPointerException(toString() + " cannot start with null!"));
        }

        //TODO : resolve URIs !!! xmldbUri.resolve(this)
        return (xmldbUri.append(this));
    }

    //TODO: add unit test for this
    public XmldbURI trimFromBeginning(XmldbURI xmldbUri) {
        if (xmldbUri == null) {
            throw (new NullPointerException(toString() + " cannot start with null!"));
        }

        if (!startsWith(xmldbUri)) {
            throw (new IllegalArgumentException(toString() + " does not start with " + xmldbUri.toString()));
        }
        return (XmldbURI.create(toString().substring(xmldbUri.toString().length())));
    }

    public XmldbURI trimFromBeginning(String string) throws URISyntaxException {
        return (trimFromBeginning(XmldbURI.xmldbUriFor(string)));
    }

    public String toString() {
        return (encodedCollectionPath);
    }

    public static String[] getPathComponents(String collectionPath) {
        final Pattern p = Pattern.compile("/");
        final String[] split = p.split(collectionPath);
        final String[] result = new String[split.length - 1];
        System.arraycopy(split, 1, result, 0, split.length - 1);
        return (result);
    }


    /* @deprecated Legacy method used here and there in the code
     * if the currentPath is null return the parentPath else
     * if the currentPath doesnt not start with "/db/" and is not equal to "/db" then adjust the path to start with the parentPath
     *
     * Fix to Jens collection/resource name problem by deliriumsky
     *
     * @deprecated Use {@link #resolveCollectionPath(String) resolveCollectionPath} instead
     */
    public static String checkPath(String currentPath, String parentPath) {
        if (currentPath == null) {
            return (parentPath);
        }

        //Absolute path
        if (ROOT_COLLECTION.equals(currentPath)) {
            return (currentPath);
        }

        //Absolute path
        if (currentPath.startsWith(ROOT_COLLECTION + "/")) {
            return (currentPath);
        }

        //Kind of relative path : against all conventions ! -pb
        if (currentPath.startsWith("/")) {
            LOG.warn("Initial '/' for relative path '" + currentPath + "'");
        }

        //OK : let's process this so-called relative path
        if (currentPath.startsWith("/")) {

            if (parentPath.endsWith("/")) {
                return (parentPath + currentPath.substring(1));
            }
            return (parentPath + currentPath);
        }

        //True relative pathes
        if (parentPath.endsWith("/")) {
            return (parentPath + currentPath);
        }
        return (parentPath + "/" + currentPath);
    }

    /**
     * DOCUMENT ME!
     *
     * @param       fileName
     * @param       parentPath
     *
     * @return      DOCUMENT ME!
     *
     * @deprecated  Legacy method used here and there in the code
     */
    public static String checkPath2(String fileName, String parentPath) {
        //if (!fileName.startsWith("/"))
        //    fileName = "/" + fileName;
        /*if (!fileName.startsWith(ROOT_COLLECTION))
        fileName = ROOT_COLLECTION + fileName;*/

        return (checkPath(fileName, parentPath));
    }

    /**
     * DOCUMENT ME!
     *
     * @param       name
     *
     * @return      DOCUMENT ME!
     *
     * @deprecated  Legacy method used here and there in the code and copied as such
     */
    //TODO : changes // into /  */
    public String makeAbsolute(String name) {
        final StringBuilder out = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {

            //TODO : use dedicated function in XmldbURI
            if ((name.charAt(i) == '/') && (name.length() > (i + 1)) && (name.charAt(i + 1) == '/')) {
                i++;
            } else {
                out.append(name.charAt(i));
            }
        }

        String name2 = out.toString();

        if ((name2.length() > 0) && (name2.charAt(0) != '/')) {
            name2 = "/" + name2;
        }

        if (!name2.startsWith(XmldbURI.ROOT_COLLECTION)) {
            name2 = XmldbURI.ROOT_COLLECTION + name2;
        }

        if (name2.endsWith("/") && (name2.length() > 1)) {
            name2 = name2.substring(0, name2.length() - 1);
        }

        return (name2);

    }

    /**
     * DOCUMENT ME!
     *
     * @param       name
     *
     * @return      DOCUMENT ME!
     *
     * @deprecated  Legacy method used here and there in the code and copied as such
     */
    //TODO : changes // into /  */
    public final static String normalizeCollectionName(String name) {
        final StringBuilder out = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {

            //TODO : use dedicated function in XmldbURI
            if ((name.charAt(i) == '/') && (name.length() > (i + 1)) && (name.charAt(i + 1) == '/')) {
                i++;
            } else {
                out.append(name.charAt(i));
            }
        }

        String name2 = out.toString();

        if ((name2.length() > 0) && (name2.charAt(0) != '/')) {
            name2 = "/" + name2;
        }

        if (!name2.startsWith(XmldbURI.ROOT_COLLECTION)) {
            name2 = XmldbURI.ROOT_COLLECTION + name2;
        }

        if (name2.endsWith("/") && (name2.length() > 1)) {
            name2 = name2.substring(0, name2.length() - 1);
        }

        return (name2);

    }


    /* (non-Javadoc)
     * @see java.net.URI#getAuthority()
     */
    public String getAuthority() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getFragment()
     */
    public String getFragment() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getPort()
     */
    public int getPort() {
        return (NO_PORT);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getQuery()
     */
    public String getQuery() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getRawAuthority()
     */
    public String getRawAuthority() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getHost()
     */
    public String getHost() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getUserInfo()
     */
    public String getUserInfo() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getRawFragment()
     */
    public String getRawFragment() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getRawQuery()
     */
    public String getRawQuery() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.net.URI#getRawUserInfo()
     */
    public String getRawUserInfo() {
        return (null);
    }


    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return (getXmldbURI().hashCode());
    }
//  TODO : prefefined URIs as static classes...
}
