package org.exist.xmldb;

import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Local implementation of the DatabaseInstanceManager.
 */
public class LocalDatabaseInstanceManager implements DatabaseInstanceManager {

	protected BrokerPool pool;
	protected User user;
	
	public LocalDatabaseInstanceManager(User user, BrokerPool pool) {
		this.pool = pool;
		this.user = user;
	}
	
	/**
	 *  Shutdown the Database instance
	 *
	 *@exception  XMLDBException
	 */
	public void shutdown() throws XMLDBException {
		if(!user.hasGroup("dba"))
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, 
				"only users in group dba may " +
				"shut down the database");
		pool.shutdown();
	}
	
	/**
	 * @see org.xmldb.api.base.Service#getName()
	 */
	public String getName() throws XMLDBException {
		return "DatabaseInstanceManager";
	}

	/**
	 * @see org.xmldb.api.base.Service#getVersion()
	 */
	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	/**
	 * @see org.xmldb.api.base.Service#setCollection(org.xmldb.api.base.Collection)
	 */
	public void setCollection(Collection arg0) throws XMLDBException {
	}

	/**
	 * @see org.xmldb.api.base.Configurable#getProperty(java.lang.String)
	 */
	public String getProperty(String arg0) throws XMLDBException {
		return null;
	}

	/**
	 * @see org.xmldb.api.base.Configurable#setProperty(java.lang.String, java.lang.String)
	 */
	public void setProperty(String arg0, String arg1) throws XMLDBException {
	}

}
