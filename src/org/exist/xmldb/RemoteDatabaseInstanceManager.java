/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
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
        final List<Object> params = new ArrayList<Object>(1);
		if(delay > 0)
			params.add(Long.valueOf(delay).toString());
		try {
			client.execute("shutdown", params);
		} catch(final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, 
				"shutdown failed",
				e);
		}
    }

    public boolean enterServiceMode() throws XMLDBException {
		try {
			client.execute("enterServiceMode", new ArrayList<Object>(1));
		} catch(final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"Failed to switch db to service mode: " + e.getMessage(), e);
		}
        return true;
    }


    public void exitServiceMode() throws XMLDBException {
        try {
			client.execute("exitServiceMode", new ArrayList<Object>(1));
		} catch(final XmlRpcException e) {
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

        final List<Object> params = new ArrayList<Object>(1);
		try {
			final Object result = client.execute("isXACMLEnabled", params);
			if(result instanceof Boolean)
				{return ((Boolean)result).booleanValue();}
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"Invalid return type for remote invocation of 'isXACMLEnabled'");
		} catch(final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, 
				"Error determining if XACML is enabled: " + e.getMessage(),
				e);
		}
    }
}
