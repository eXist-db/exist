package org.exist.xmldb;

import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * A service to manage the database instance. The service defines
 * a single method shutdown() to shut down the database instance
 * used by the current driver.
 * 
 */
public interface DatabaseInstanceManager extends Service {
	
	/**
	 * Immediately shutdown the current database instance.
	 * This current user should be a member of the "dba" group
	 * or an exception will be thrown.
	 * 
	 * @throws XMLDBException
	 */
	public void shutdown() throws XMLDBException;
	
	/**
	 * Shutdown the current database instance after the specified
	 * delay (in milliseconds).
	 * This current user should be a member of the "dba" group
	 * or an exception will be thrown.
	 * 
	 * @throws XMLDBException
	 */
	public void shutdown(long delay) throws XMLDBException;

    public boolean enterServiceMode() throws XMLDBException;

    public void exitServiceMode() throws XMLDBException;
    
    public DatabaseStatus getStatus() throws XMLDBException;
	
	/**
	 * Returns true if the database instance is running local, i.e. in
	 * the same thread as this service.
	 *  
	 * @return true if the database instance is running local
	 */
	public boolean isLocalInstance();
	
	/**
	 * Returns true if XACML is enabled for the database instance. 
	 *
	 * @return True if XACML is enabled
	 */
	public boolean isXACMLEnabled() throws XMLDBException;
}