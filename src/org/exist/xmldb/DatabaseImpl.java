/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.SSLHelper;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;

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

  private final static Logger LOG = Logger.getLogger(DatabaseImpl.class);

	//TODO : discuss about other possible values
  protected final static String LOCAL_HOSTNAME = "";

  protected final static int UNKNOWN_CONNECTION = -1;
  protected final static int LOCAL_CONNECTION = 0;
  protected final static int REMOTE_CONNECTION = 1;

  /** Default config filename to configure an Instance */
    public final static String CONF_XML="conf.xml";

  protected boolean autoCreate = false;
  protected String configuration = null;
  protected String currentInstanceName = null;

  private HashMap<String, XmlRpcClient> rpcClients = new HashMap<String, XmlRpcClient>();
  protected ShutdownListener shutdown = null;
  protected int mode = UNKNOWN_CONNECTION;
    
  private Boolean ssl_enable=false;
  private Boolean ssl_allow_self_signed=true;
  private Boolean ssl_verify_hostname=false;

  public DatabaseImpl() {
    	String initdb = System.getProperty( "exist.initdb" );
    	if(initdb != null)
    		autoCreate = initdb.equalsIgnoreCase("true");
  }

  /**
     *  In embedded mode: configure the database instance
   * 
     *@exception  XMLDBException  Description of the Exception
   */
  private void configure(String instanceName) throws XMLDBException {
        // System.out.println("Configuring '" + instanceName + "' using " + Configuration.getPath(configuration, null));
    try {
      Configuration config = new Configuration(configuration, null);
      BrokerPool.configure(instanceName, 1, 5, config);
            if (shutdown != null)
                BrokerPool.getInstance(instanceName).registerShutdownListener(shutdown);
        } catch (Exception e ) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "configuration error: " + e.getMessage(), e );
    }
    currentInstanceName = instanceName;
  }

    /* @deprecated  Although part of the xmldb API, the design is somewhat inconsistent.
   * @see org.xmldb.api.base.Database#acceptsURI(java.lang.String)
   */
  public boolean acceptsURI(String uri) throws XMLDBException {
    XmldbURI xmldbURI = null;
    try {
            //Ugly workaround for non-URI compliant collection (resources ?) names (most likely IRIs)
      String newURIString = XmldbURI.recoverPseudoURIs(uri);
            //Remember that DatabaseManager (provided in xmldb.jar) trims the leading "xmldb:" !!!
            //... prepend it to have a real xmldb URI again...
      xmldbURI = XmldbURI.xmldbUriFor(XmldbURI.XMLDB_URI_PREFIX + newURIString);
      return acceptsURI(xmldbURI);
    } catch (URISyntaxException e) {
            //... even in the error message
      throw new XMLDBException(ErrorCodes.INVALID_DATABASE, "xmldb URI is not well formed: " + XmldbURI.XMLDB_URI_PREFIX + uri);
    }
  }

  public boolean acceptsURI(XmldbURI xmldbURI) throws XMLDBException {
        //TODO : smarter processing (resources names, protocols, servers accessibility...) ? -pb
    return true;
  }

    /* Returns a collection from the given "uri".
     * @deprecated  Although part of the xmldb API, the design is somewhat inconsistent.
     * @see org.exist.xmldb.DatabaseImpl#getCollection(org.exist.xmldb.XmldbURI, java.lang.String, java.lang.String)
     * @see org.xmldb.api.base.Database#getCollection(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public Collection getCollection(final String uri, final String user, final String password) throws XMLDBException {
        try {
            //Ugly workaround for non-URI compliant collection names (most likely IRIs)
            final String newURIString = XmldbURI.recoverPseudoURIs(uri);
            //Remember that DatabaseManager (provided in xmldb.jar) trims the leading "xmldb:" !!!
            //... prepend it to have a real xmldb URI again...
            final XmldbURI xmldbURI = XmldbURI.xmldbUriFor(XmldbURI.XMLDB_URI_PREFIX + newURIString);
            return getCollection(xmldbURI, user, password);
        } catch(final URISyntaxException e) {
            //... even in the error message
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, "xmldb URI is not well formed: " + XmldbURI.XMLDB_URI_PREFIX + uri);
        }
    }

    public Collection getCollection(final XmldbURI xmldbURI, final String user, final String password) throws XMLDBException {
        if(XmldbURI.API_LOCAL.equals(xmldbURI.getApiName())) {
            return getLocalCollection(xmldbURI, user, password);
        } else if (XmldbURI.API_XMLRPC.equals(xmldbURI.getApiName())) {
            return getRemoteCollection(xmldbURI, user, password);
        } else {
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, "Unknown or unparsable API for: " + xmldbURI);
        }
    }

    /**
     * @param xmldbURI
     * @param user
     * @param password
     * @return The collection
     * @throws XMLDBException
     */
    private Collection getLocalCollection(final XmldbURI xmldbURI, final String user, final String password) throws XMLDBException {
        mode = LOCAL_CONNECTION;
        
        // use local database instance
        if(!BrokerPool.isConfigured(xmldbURI.getInstanceName())) {
            if(autoCreate) {
                configure(xmldbURI.getInstanceName());
            } else {
                throw new XMLDBException(ErrorCodes.COLLECTION_CLOSED, "Local database server is not running");
            }
        }
        
        BrokerPool pool;
        try {
            pool = BrokerPool.getInstance(xmldbURI.getInstanceName());
        } catch (final EXistException e) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, "Can not access to local database instance", e);
        }
        final Subject u = getUser(user, password, pool);
        try {
            return  new LocalCollection(u, pool, xmldbURI.toCollectionPathURI(), AccessContext.XMLDB);
        } catch(final XMLDBException e) {
            switch (e.errorCode) {
              case ErrorCodes.NO_SUCH_RESOURCE:
              case ErrorCodes.NO_SUCH_COLLECTION:
              case ErrorCodes.INVALID_COLLECTION:
              case ErrorCodes.INVALID_RESOURCE:
                LOG.info(e.getMessage());
                return null;
              default:
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * @param xmldbURI
     * @param user
     * @param password
     * @return The collection
     * @throws XMLDBException
     */
    private Collection getRemoteCollection(final XmldbURI xmldbURI, String user, String password) throws XMLDBException {
        mode = REMOTE_CONNECTION;
        if(user == null) {
            //TODO : read this from configuration
            user = SecurityManager.GUEST_USER;
            password = SecurityManager.GUEST_USER;
        }
    
        if(password == null) {
            password = "";
        }
        
        try {
            final String protocol = ssl_enable ? "https" : "http";
            if(ssl_enable){
                SSLHelper.initialize(ssl_allow_self_signed, ssl_verify_hostname);
            }

            final URL url = new URL(protocol, xmldbURI.getHost(), xmldbURI.getPort(), xmldbURI.getContext());

            final XmlRpcClient rpcClient = getRpcClient(user, password, url);
            return readCollection(xmldbURI.getRawCollectionPath(), rpcClient);

        } catch(final MalformedURLException e) {
            //Should never happen
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, e.getMessage());
        } catch(final XMLDBException e) {
            switch (e.errorCode) {
              case ErrorCodes.NO_SUCH_RESOURCE:
              case ErrorCodes.NO_SUCH_COLLECTION:
              case ErrorCodes.INVALID_COLLECTION:
              case ErrorCodes.INVALID_RESOURCE:
                LOG.info(e.getMessage());
                return null;
              default:
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    public static Collection readCollection(final String c, final XmlRpcClient rpcClient) throws XMLDBException {
        final XmldbURI path;
        try {
            path = XmldbURI.xmldbUriFor(c);
        } catch (URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
        final XmldbURI[] components = path.getPathSegments();
        if(components.length == 0) {
            throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Could not find collection: " + path.toString());
        }
        
        XmldbURI rootName = components[0];
        if(XmldbURI.RELATIVE_ROOT_COLLECTION_URI.equals(rootName)) {
            rootName = XmldbURI.ROOT_COLLECTION_URI;
        }
        
        Collection current = RemoteCollection.instance(rpcClient, rootName);
        for(int i = 1 ; i < components.length ; i++) {
            current = ((RemoteCollection)current).getChildCollection(components[i]);
            if (current == null) {
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION , "Could not find collection: " + c);
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
  private Subject getUser(String user, String password, BrokerPool pool) throws XMLDBException {
    if (user == null) {
      user = SecurityManager.GUEST_USER;
      password = SecurityManager.GUEST_USER;
    }
    SecurityManager securityManager = pool.getSecurityManager();
    try {
        return securityManager.authenticate(user, password);
	} catch (AuthenticationException e) {
		throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
	}
  }

  /**
   * RpcClients are cached by address+user. The password is transparently changed.
   * @param user
   * @param password
   * @param url
   * @throws XMLDBException
   */
  private XmlRpcClient getRpcClient(String user, String password, URL url) throws XMLDBException {
      String key = user + "@" + url.toString();
      XmlRpcClient client = rpcClients.get(key);
      XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
      config.setEnabledForExtensions(true);
      config.setServerURL(url);
      config.setBasicUserName(user);
      config.setBasicPassword(password);
      if (client == null) {
          client = new XmlRpcClient();
          rpcClients.put(key, client);
      }
      client.setConfig(config);
      return client;
  }

  /**
   * Register a ShutdownListener for the current database instance. The ShutdownListener is called
     * after the database has shut down. You have to register a listener before any calls to getCollection().
   * 
   * @param listener
   * @throws XMLDBException
   */
  public void setDatabaseShutdownListener(ShutdownListener listener) throws XMLDBException {
    shutdown = listener;
  }

  public String getConformanceLevel() throws XMLDBException {
        //TODO : what is to be returned here ? -pb
    return "0";
  }

  //WARNING : returning such a default value is dangerous IMHO ? -pb
  /** @deprecated */
  public String getName() throws XMLDBException {
    return (currentInstanceName != null) ? currentInstanceName : "exist";
  }

    //WARNING : returning such *a* default value is dangerous IMHO ? -pb
  public String[] getNames() throws XMLDBException {
    return new String[] { (currentInstanceName != null) ? currentInstanceName : "exist" };
  }

    public String getProperty(String property) throws XMLDBException {
        
        if (property.equals("create-database")) {
            //TODO : rename ?
            return Boolean.valueOf(autoCreate).toString();
        }
        
        if (property.equals("database-id")) 
        {
            //TODO : consider multivalued property
            return currentInstanceName;
        }

        if (property.equals("configuration")) {
            return configuration;
        }

        if (property.equals("ssl-enable")) {
            return "" + ssl_enable;
        }
        
        if (property.equals("ssl-allow-self-signed")) {
            return "" + ssl_allow_self_signed;
        }
        
        if (property.equals("ssl-verify-hostname")) {
            return "" + ssl_allow_self_signed;
        }
        
        return null;
    }

    public void setProperty(String property, String value) throws XMLDBException {

        if (property.equals("create-database")) {
            autoCreate = value.equals("true");
        }
        
        //TODO : rename ?
        if (property.equals("database-id")) 
        {
            //TODO : consider multivalued property
            currentInstanceName = value;
        }

        if (property.equals("configuration")) {
            configuration = value;
        }

        if (property.equals("ssl-enable")) {
            ssl_enable = Boolean.valueOf(value);
        }

        if (property.equals("ssl-allow-self-signed")) {
            ssl_allow_self_signed = Boolean.valueOf(value);
        }

        if (property.equals("ssl-verify-hostname")) {
            ssl_verify_hostname = Boolean.valueOf(value);
        }

    }
}