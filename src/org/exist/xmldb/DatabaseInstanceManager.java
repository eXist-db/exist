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
	
	public void shutdown() throws XMLDBException;

}