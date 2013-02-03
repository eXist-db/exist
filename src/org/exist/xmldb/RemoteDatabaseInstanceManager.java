package org.exist.xmldb;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.util.ArrayList;
import java.util.List;

public class RemoteDatabaseInstanceManager implements DatabaseInstanceManager {

	protected XmlRpcClient client;
	
	/**
	 * Constructor for DatabaseInstanceManagerImpl.
	 */
	public RemoteDatabaseInstanceManager(XmlRpcClient client) {
		this.client = client;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.DatabaseInstanceManager#shutdown()
	 */
	public void shutdown() throws XMLDBException {
		shutdown(0);		
	}
	
	/**
	 * @see org.exist.xmldb.DatabaseInstanceManager#shutdown()
	 */
	public void shutdown(long delay) throws XMLDBException {
        List<Object> params = new ArrayList<Object>(1);
		if(delay > 0)
			params.add(new Long(delay).toString());
		try {
			client.execute("shutdown", params);
		} catch(XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, 
				"shutdown failed",
				e);
		}
    }

    public boolean enterServiceMode() throws XMLDBException {
		try {
			client.execute("enterServiceMode", new ArrayList<Object>(1));
		} catch(XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"Failed to switch db to service mode: " + e.getMessage(), e);
		}
        return true;
    }


    public void exitServiceMode() throws XMLDBException {
        try {
			client.execute("exitServiceMode", new ArrayList<Object>(1));
		} catch(XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"Failed to switch db to service mode: " + e.getMessage(), e);
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

	public boolean isLocalInstance() {
		return false;
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

	/* (non-Javadoc)
	 * @see org.exist.xmldb.DatabaseInstanceManager#getConfiguration()
	 */
	public DatabaseStatus getStatus() throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED,
			"this method is not available for remote connections");
	}

	public boolean isXACMLEnabled() throws XMLDBException {

        List<Object> params = new ArrayList<Object>(1);
		try {
			Object result = client.execute("isXACMLEnabled", params);
			if(result instanceof Boolean)
				return ((Boolean)result).booleanValue();
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"Invalid return type for remote invocation of 'isXACMLEnabled'");
		} catch(XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, 
				"Error determining if XACML is enabled: " + e.getMessage(),
				e);
		}
    }

    @Override
    public void restorePkgRepo() throws XMLDBException {
        try {
            client.execute("restorePkgRepo", new ArrayList<Object>(1));
        } catch(XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    "Failed to restore package repository: " + e.getMessage(), e);
        }
    }
}
