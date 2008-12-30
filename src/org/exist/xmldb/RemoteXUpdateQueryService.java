/*
 * RemoteXUpdateQueryService.java - May 2, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import java.io.UnsupportedEncodingException;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

public class RemoteXUpdateQueryService implements XUpdateQueryService {

	private final static Logger LOG = Logger.getLogger(RemoteXUpdateQueryService.class);

	private RemoteCollection parent;

	/**
	 * 
	 */
	public RemoteXUpdateQueryService(RemoteCollection parent) {
		this.parent = parent;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.modules.XUpdateQueryService#update(java.lang.String)
	 */
	public long update(String commands) throws XMLDBException {
		LOG.debug("processing xupdate:\n" + commands);
		Vector params = new Vector();
		byte[] xupdateData;
		try {
			xupdateData = commands.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			LOG.warn(e);
			xupdateData = commands.getBytes();
		}
		params.addElement(parent.getPath());
		params.addElement(xupdateData);
		try {
			Integer mods = (Integer) parent.getClient().execute("xupdate", params);
			LOG.debug("processed " + mods + " modifications");
			return mods.intValue();
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/* (non-Javadoc)
	 * @see org.xmldb.api.modules.XUpdateQueryService#updateResource(java.lang.String, java.lang.String)
	 */
	public long updateResource(String id, String commands) throws XMLDBException {
		LOG.debug("processing xupdate:\n" + commands);
		Vector params = new Vector();
		byte[] xupdateData;
		try {
			xupdateData = commands.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			LOG.warn(e);
			xupdateData = commands.getBytes();
		}
        //TODO : use dedicated function in XmldbURI
		params.addElement(parent.getPath() + "/" + id);
		params.addElement(xupdateData);
		try {
			Integer mods = (Integer) parent.getClient().execute("xupdateResource", params);
			LOG.debug("processed " + mods + " modifications");
			return mods.intValue();
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#getName()
	 */
	public String getName() throws XMLDBException {
		return "XUpdateQueryService";
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#getVersion()
	 */
	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#setCollection(org.xmldb.api.base.Collection)
	 */
	public void setCollection(Collection col) throws XMLDBException {
		parent = (RemoteCollection)col;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Configurable#getProperty(java.lang.String)
	 */
	public String getProperty(String name) throws XMLDBException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Configurable#setProperty(java.lang.String, java.lang.String)
	 */
	public void setProperty(String name, String value) throws XMLDBException {
	}

}
