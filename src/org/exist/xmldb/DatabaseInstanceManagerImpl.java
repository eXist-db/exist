package org.exist.xmldb;

import java.io.IOException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class DatabaseInstanceManagerImpl implements DatabaseInstanceManager {

	protected XmlRpcClient client;
	
	/**
	 * Constructor for DatabaseInstanceManagerImpl.
	 */
	public DatabaseInstanceManagerImpl(XmlRpcClient client) {
		this.client = client;
	}

	/**
	 * @see org.exist.xmldb.DatabaseInstanceManager#shutdown()
	 */
	public void shutdown() throws XMLDBException {
		try {
			client.execute("shutdown", new Vector());
		} catch(XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, 
				"shutdown failed",
				e);
		} catch(IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, 
							"shutdown failed",
							e);
		}
		
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
