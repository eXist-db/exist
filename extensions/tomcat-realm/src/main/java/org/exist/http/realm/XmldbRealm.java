package org.exist.http.realm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.realm.GenericPrincipal;
import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.security.Account;
import org.exist.security.XmldbPrincipal;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

public class XmldbRealm extends org.apache.catalina.realm.RealmBase {
	

	private String basedir = ".";

	private String configuration = "conf.xml";
	
	private String uri = XmldbURI.LOCAL_DB;
	
	private String driver = "org.exist.xmldb.DatabaseImpl";
	
	private UserManagementService service = null;

	private Account defaultUser = null;
	
	/**
	 * Descriptive information about this Realm implementation.
	 */
	protected final String info = "XMLDBRealm/1.0";
	/**
	 * Descriptive information about this Realm implementation.
	 */
	protected static final String name = "XMLDBRealm";
	/**
	 * Return descriptive information about this Realm implementation and the
	 * corresponding version number, in the format <code>&lt;description&gt;/&lt;version&gt;</code>.
	 */
	public String getInfo() {
		return info;
	}
	/**
	 * Return the Principal associated with the specified username and
	 * credentials, if there is one; otherwise return <code>null</code>.
	 * 
	 * @param username
	 *            Username of the Principal to look up
	 * @param credentials
	 *            Password or other credentials to use in authenticating this
	 *            username
	 */
	public Principal authenticate(String username, String credentials) {
		GenericPrincipal principal = (GenericPrincipal) getPrincipal(username);
		boolean validated = false;
		if (principal != null) {
			if (hasMessageDigest()) {
				// Hex hashes should be compared case-insensitive
				validated = (digest(credentials).equalsIgnoreCase(principal
						.getPassword()));
			} else {
				validated = (digest(credentials)
						.equals(principal.getPassword()));
			}
		}
		if (validated) {
			if (debug >= 2)
				log(sm.getString("userDatabaseRealm.authenticateSuccess", username));
			return (principal);
		} else {
			if (debug >= 2)
				log(sm.getString("userDatabaseRealm.authenticateFailure", username));
			return (null);
		}
	}
	/**
	 * Return a short name for this Realm implementation.
	 */
	protected String getName() {
		return name;
	}
	/**
	 * Return the password associated with the given principal's user name.
	 */
	protected String getPassword(String username) {
		GenericPrincipal principal = (GenericPrincipal) getPrincipal(username);
		if (principal != null) {
			return (principal.getPassword());
		} else {
			return (null);
		}
	}
	/**
	 * Return the Principal associated with the given user name.
	 */
	protected Principal getPrincipal(String username) {
		Account user = null;
		
		try {
			user = service.getAccount(username);
		} catch (XMLDBException e) {
		}
		
		if (user == null) {
			user = defaultUser;
		}
		
		// Accumulate the list of roles for this user
		ArrayList<String> list = new ArrayList<String>();
		String[] groups = user.getGroups();
		for (int i = 0; i < groups.length; i++) {
			list.add(groups[i]);
		}
		return (Principal) new DefaultXmldbPrinciple(this, username, user.getPassword(), list);
	}
	
	
	protected class DefaultXmldbPrinciple extends GenericPrincipal implements XmldbPrincipal {
		public DefaultXmldbPrinciple(Realm arg0, String arg1, String arg2, List arg3) {
			super(arg0, arg1, arg2, arg3);
		}
	}
	/**
	 * Prepare for active use of the public methods of this Component.
	 * 
	 * @exception LifecycleException
	 *                if this component detects a fatal error that prevents it
	 *                from being started
	 */
	public synchronized void start() throws LifecycleException {
		
		try {
			
			URI existURI = new URI(uri);

			if("".equals(existURI.getHost()) || existURI.getHost() == null){
				this.startExistDb();
			}
			
			String driver = "org.exist.xmldb.DatabaseImpl";
			Class<?> cl = Class.forName(driver);			
			DatabaseManager.registerDatabase((Database) cl.newInstance());
		
			// try to get collection
			Collection collection = DatabaseManager.getCollection(uri);
			
			if (collection == null) {
				throw new LifecycleException("unable to resolve");
			}
			
			service = (UserManagementService) collection
					.getService("UserManagementService", "1.0");

			defaultUser = service.getAccount(SecurityManager.GUEST_USER);
			
			/* initialize security */
			boolean admin = true;
			if(admin){
				Account adminUser = service.getAccount(SecurityManager.DBA_USER);
				if(adminUser.getPassword() == null){
					adminUser.setPassword("admin");
					log("Update Admin User on inital start");
				}
			}
			
		} catch (ClassNotFoundException e) {
			throw new LifecycleException(e.getMessage(),e);
		} catch (XMLDBException e) {
			throw new LifecycleException(e.getMessage(),e);
		} catch (InstantiationException e) {
			throw new LifecycleException(e.getMessage(),e);
		} catch (IllegalAccessException e) {
			throw new LifecycleException(e.getMessage(),e);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Perform normal superclass initialization
		super.start();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.catalina.Lifecycle#stop()
	 */
	public void stop() throws LifecycleException {
		super.stop();
		BrokerPool.stopAll(false);
	}
	
	public synchronized void startExistDb() throws LifecycleException {
		try {
			
			if (!BrokerPool.isConfigured(BrokerPool.DEFAULT_INSTANCE_NAME)) {
				this.log("Starting Database");
				
				this.log("exist.home=" + basedir);
				Path f = Paths.get(basedir, configuration);
				
				this.log("reading configuration from " + f.toAbsolutePath());
				if (!Files.isReadable(f))
					throw new LifecycleException("configuration file "
							+ configuration + " not found or not readable");
				Configuration conf = new Configuration(configuration, Optional.ofNullable(basedir).map(Paths::get));
				BrokerPool.configure(1, 5, conf);
			}
			
		} catch (EXistException e) {
			throw new LifecycleException(e.getMessage(),e);
		} catch (DatabaseConfigurationException e) {
			throw new LifecycleException(e.getMessage(),e);
		}

	}
	
	
	/**
	 * @return Returns the basedir.
	 */
	public String getBasedir() {
		return basedir;
	}
	
	/**
	 * @param basedir The basedir to set.
	 */
	public void setBasedir(String basedir) {
		this.basedir = basedir;
	}
	
	/**
	 * @return Returns the configuration.
	 */
	public String getConfiguration() {
		return configuration;
	}
	/**
	 * @param configuration The configuration to set.
	 */
	public void setConfiguration(String configuration) {
		this.configuration = configuration;
	}
	
	/**
	 * @return Returns the driver.
	 */
	public String getDriver() {
		return driver;
	}
	/**
	 * @param driver The driver to set.
	 */
	public void setDriver(String driver) {
		this.driver = driver;
	}

	/**
	 * @return Returns the uri.
	 */
	public String getUri() {
		return uri;
	}
	/**
	 * @param uri The uri to set.
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}
	
	
}
