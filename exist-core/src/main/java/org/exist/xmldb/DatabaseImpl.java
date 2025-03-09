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
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;
import org.exist.util.Leasable;
import org.exist.util.SSLHelper;

import org.exist.xmlrpc.ExistRpcTypeFactory;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The XMLDB driver class for eXist. This driver manages two different
 * internal implementations. The first communicates with a remote
 * database using the XMLRPC protocol. The second has direct access
 * to an embedded database instance running in the same virtual machine.
 * The driver chooses an implementation depending on the XML:DB URI passed
 * to getCollection().
 *
 * When running in embedded mode, the driver can create a new database
 * instance if none is available yet. It will do so if the property
 * "create-database" is set to "true" or if there is a system property
 * "exist.initdb" with value "true".
 *
 * You may optionally provide the location of an alternate configuration
 * file through the "configuration" property. The driver is also able to
 * address different database instances - which may have been installed at
 * different places.
 *
 * @author Wolfgang Meier
 */
public class DatabaseImpl implements Database {

    private final static Logger LOG = LogManager.getLogger(DatabaseImpl.class);

    //TODO : discuss about other possible values
    protected final static String LOCAL_HOSTNAME = "";

    protected final static int UNKNOWN_CONNECTION = -1;
    protected final static int LOCAL_CONNECTION = 0;
    protected final static int REMOTE_CONNECTION = 1;

    /**
     * Default config filename to configure an Instance
     */
    public final static String CONF_XML = "conf.xml";

    private boolean autoCreate = false;
    private String configuration = null;
    private String dataDir = null;
    private String journalDir = null;
    private String currentInstanceName = null;

    private final Map<String, Leasable<XmlRpcClient>> rpcClients = new HashMap<>();
    private ShutdownListener shutdown = null;
    private int mode = UNKNOWN_CONNECTION;

    private Boolean ssl_enable = false;
    private Boolean ssl_allow_self_signed = true;
    private Boolean ssl_verify_hostname = false;

    public DatabaseImpl() {
        final String initdb = System.getProperty("exist.initdb");
        if (initdb != null) {
            autoCreate = "true".equalsIgnoreCase(initdb);
        }
    }

    /**
     * In embedded mode: configure the database instance
     *
     * @throws XMLDBException Description of the Exception
     */
    private void configure(final String instanceName) throws XMLDBException {
        try {
            final Configuration config = new Configuration(configuration, Optional.empty());
            if (dataDir != null) {
                config.setProperty(BrokerPool.PROPERTY_DATA_DIR, Paths.get(dataDir));
            }
            if (journalDir != null) {
                config.setProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, Paths.get(journalDir));
            }

            BrokerPool.configure(instanceName, 1, 5, config);
            if (shutdown != null) {
                BrokerPool.getInstance(instanceName).registerShutdownListener(shutdown);
            }
            currentInstanceName = instanceName;
        } catch (final Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "configuration error: " + e.getMessage(), e);
        }
    }

    /**
     * @deprecated {@link org.exist.xmldb.DatabaseImpl#acceptsURI(org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public boolean acceptsURI(final String uri) throws XMLDBException {
        try {
            //Ugly workaround for non-URI compliant collection (resources ?) names (most likely IRIs)
            final String newURIString = XmldbURI.recoverPseudoURIs(uri);
            //Remember that DatabaseManager (provided in xmldb.jar) trims the leading "xmldb:" !!!
            //... prepend it to have a real xmldb URI again...
            final XmldbURI xmldbURI = XmldbURI.xmldbUriFor(XmldbURI.XMLDB_URI_PREFIX + newURIString);
            return acceptsURI(xmldbURI);
        } catch (final URISyntaxException e) {
            //... even in the error message
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, "xmldb URI is not well formed: " + XmldbURI.XMLDB_URI_PREFIX + uri);
        }
    }

    public boolean acceptsURI(final XmldbURI xmldbURI) throws XMLDBException {
        //TODO : smarter processing (resources names, protocols, servers accessibility...) ? -pb
        return true;
    }

    /**
     * @deprecated {@link org.exist.xmldb.DatabaseImpl#getCollection(org.exist.xmldb.XmldbURI, java.lang.String, java.lang.String)}
     */
    @Deprecated
    @Override
    public Collection getCollection(final String uri, final String user, final String password) throws XMLDBException {
        try {
            //Ugly workaround for non-URI compliant collection names (most likely IRIs)
            final String newURIString = XmldbURI.recoverPseudoURIs(uri);
            //Remember that DatabaseManager (provided in xmldb.jar) trims the leading "xmldb:" !!!
            //... prepend it to have a real xmldb URI again...
            final XmldbURI xmldbURI = XmldbURI.xmldbUriFor(XmldbURI.XMLDB_URI_PREFIX + newURIString);
            return getCollection(xmldbURI, user, password);
        } catch (final URISyntaxException e) {
            //... even in the error message
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, "xmldb URI is not well formed: " + XmldbURI.XMLDB_URI_PREFIX + uri);
        }
    }

    public Collection getCollection(final XmldbURI xmldbURI, final String user, final String password) throws XMLDBException {
        if (XmldbURI.API_LOCAL.equals(xmldbURI.getApiName())) {
            return getLocalCollection(xmldbURI, user, password);
        } else if (XmldbURI.API_XMLRPC.equals(xmldbURI.getApiName())) {
            return getRemoteCollection(xmldbURI, user, password);
        } else {
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, "Unknown or unparsable API for: " + xmldbURI);
        }
    }

    private Collection getLocalCollection(final XmldbURI xmldbURI, final String user, final String password) throws XMLDBException {
        mode = LOCAL_CONNECTION;

        // use local database instance
        if (!BrokerPool.isConfigured(xmldbURI.getInstanceName())) {
            if (autoCreate) {
                configure(xmldbURI.getInstanceName());
            } else {
                throw new XMLDBException(ErrorCodes.COLLECTION_CLOSED, "Local database server is not running");
            }
        }

        try {
            final BrokerPool pool = BrokerPool.getInstance(xmldbURI.getInstanceName());
            final Subject u = getUser(user, password, pool);
            return new LocalCollection(u, pool, xmldbURI.toCollectionPathURI());
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Can not access to local database instance", e);
        } catch (final XMLDBException e) {
            return switch (e.errorCode) {
                case ErrorCodes.NO_SUCH_RESOURCE, ErrorCodes.NO_SUCH_COLLECTION, ErrorCodes.INVALID_COLLECTION, ErrorCodes.INVALID_RESOURCE -> {
                    LOG.debug(e.getMessage());
                    yield null;
                }
                default -> {
                    LOG.error(e.getMessage(), e);
                    throw e;
                }
            };
        }
    }

    private Collection getRemoteCollection(final XmldbURI xmldbURI, String user, String password) throws XMLDBException {
        mode = REMOTE_CONNECTION;
        if (user == null) {
            //TODO : read this from configuration
            user = SecurityManager.GUEST_USER;
            password = SecurityManager.GUEST_USER;
        }

        if (password == null) {
            password = "";
        }

        try {
            final String protocol = ssl_enable ? "https" : "http";
            if (ssl_enable) {
                SSLHelper.initialize(ssl_allow_self_signed, ssl_verify_hostname);
            }

            final URL url = new URL(protocol, xmldbURI.getHost(), xmldbURI.getPort(), xmldbURI.getContext());

            final Leasable<XmlRpcClient> rpcClient = getRpcClient(user, password, url);
            return readCollection(xmldbURI.getRawCollectionPath(), rpcClient);

        } catch (final MalformedURLException e) {
            //Should never happen
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, e.getMessage());
        } catch (final XMLDBException e) {
            return switch (e.errorCode) {
                case ErrorCodes.NO_SUCH_RESOURCE, ErrorCodes.NO_SUCH_COLLECTION, ErrorCodes.INVALID_COLLECTION, ErrorCodes.INVALID_RESOURCE -> {
                    LOG.debug(e.getMessage());
                    yield null;
                }
                default -> {
                    LOG.error(e.getMessage(), e);
                    throw e;
                }
            };
        }
    }

    public static Collection readCollection(final String c, final Leasable<XmlRpcClient> rpcClient) throws XMLDBException {
        final XmldbURI path;
        try {
            path = XmldbURI.xmldbUriFor(c);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }

        final XmldbURI[] components = path.getPathSegments();
        if (components.length == 0) {
            throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Could not find collection: " + path);
        }

        XmldbURI rootName = components[0];
        if (XmldbURI.RELATIVE_ROOT_COLLECTION_URI.equals(rootName)) {
            rootName = XmldbURI.ROOT_COLLECTION_URI;
        }

        Collection current = RemoteCollection.instance(rpcClient, rootName);
        for (int i = 1; i < components.length; i++) {
            current = ((RemoteCollection) current).getChildCollection(components[i]);
            if (current == null) {
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Could not find collection: " + c);
            }
        }
        return current;
    }

    /**
     * @param user
     * @param pool
     * @return the User object corresponding to the username in <code>user</code>
     * @throws XMLDBException
     */
    private Subject getUser(String user, String password, final BrokerPool pool) throws XMLDBException {
        try {
            if (user == null) {
                user = SecurityManager.GUEST_USER;
                password = SecurityManager.GUEST_USER;
            }
            final SecurityManager securityManager = pool.getSecurityManager();
            return securityManager.authenticate(user, password);
        } catch (final AuthenticationException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    /**
     * RpcClients are cached by address+user. The password is transparently changed.
     *
     * @param user
     * @param password
     * @param url
     * @throws XMLDBException
     */
    private Leasable<XmlRpcClient> getRpcClient(final String user, final String password, final URL url) {
        return rpcClients.computeIfAbsent(rpcClientKey(user, url), key -> newRpcClient(user, password, url));
    }

    private String rpcClientKey(final String user, final URL url) {
        return user + "@" + url.toString();
    }

    private Leasable<XmlRpcClient> newRpcClient(final String user, String password, final URL url) {
        final XmlRpcClient client = new XmlRpcClient();

        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setContentLengthOptional(true);
        config.setGzipCompressing(true);
        config.setGzipRequesting(true);
        config.setServerURL(url);
        config.setBasicUserName(user);
        config.setBasicPassword(password);

        client.setConfig(config);
        client.setTypeFactory(new ExistRpcTypeFactory(client));

        return new Leasable<>(client, _client -> rpcClients.remove(rpcClientKey(user, url)));
    }

    /**
     * Register a ShutdownListener for the current database instance. The ShutdownListener is called
     * after the database has shut down. You have to register a listener before any calls to getCollection().
     *
     * @param listener the shutdown listener.
     *
     * @throws XMLDBException if an error occurs whilst setting the listener.
     */
    public void setDatabaseShutdownListener(final ShutdownListener listener) throws XMLDBException {
        shutdown = listener;
    }

    @Override
    public String getConformanceLevel() throws XMLDBException {
        //TODO : what is to be returned here ? -pb
        return "0";
    }

    //WARNING : returning such a default value is dangerous IMHO ? -pb
    @Override
    public String getName() throws XMLDBException {
        return currentInstanceName != null ? currentInstanceName : "exist";
    }

    public final static String CREATE_DATABASE = "create-database";
    public final static String DATABASE_ID = "database-id";
    public final static String CONFIGURATION = "configuration";
    public final static String DATA_DIR = "data-dir";
    public final static String JOURNAL_DIR = "journal-dir";
    public final static String SSL_ENABLE = "ssl-enable";
    public final static String SSL_ALLOW_SELF_SIGNED = "ssl-allow-self-signed";
    public final static String SSL_VERIFY_HOSTNAME = "ssl-verify-hostname";

    @Override
    public String getProperty(final String property) throws XMLDBException {
        return getProperty(property, null);
    }

    @Override
    public String getProperty(final String property, final String defaultValue) throws XMLDBException {
        final String value = switch (property) {
            case CREATE_DATABASE -> Boolean.valueOf(autoCreate).toString();
            case DATABASE_ID -> currentInstanceName;
            case CONFIGURATION -> configuration;
            case DATA_DIR -> dataDir;
            case JOURNAL_DIR -> journalDir;
            case SSL_ENABLE -> ssl_enable.toString();
            case SSL_ALLOW_SELF_SIGNED -> ssl_allow_self_signed.toString();
            case SSL_VERIFY_HOSTNAME -> ssl_verify_hostname.toString();
            default -> defaultValue;
        };
        return value;
    }

    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
        switch(property) {
            case CREATE_DATABASE -> this.autoCreate = Boolean.parseBoolean(value);
            case DATABASE_ID ->  this.currentInstanceName = value;
            case CONFIGURATION -> this.configuration = value;
            case DATA_DIR -> this.dataDir = value;
            case JOURNAL_DIR -> this.journalDir = value;
            case SSL_ENABLE -> this.ssl_enable = Boolean.valueOf(value);
            case SSL_ALLOW_SELF_SIGNED -> this.ssl_allow_self_signed = Boolean.valueOf(value);
            case SSL_VERIFY_HOSTNAME -> this.ssl_verify_hostname = Boolean.valueOf(value);
            default -> LOG.warn("Ignoring unknown property: {}, value: {} ", property, value);
        }
    }
}
