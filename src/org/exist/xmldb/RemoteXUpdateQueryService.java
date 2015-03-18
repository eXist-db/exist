/*
 * RemoteXUpdateQueryService.java - May 2, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteXUpdateQueryService implements XUpdateQueryService {

	private final static Logger LOG = LogManager.getLogger(RemoteXUpdateQueryService.class);

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
		final Vector<Object> params = new Vector<Object>();
		byte[] xupdateData = commands.getBytes(UTF_8);

		params.addElement(parent.getPath());
		params.addElement(xupdateData);
		try {
			final Integer mods = (Integer) parent.getClient().execute("xupdate", params);
			LOG.debug("processed " + mods + " modifications");
			return mods.intValue();
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/* (non-Javadoc)
	 * @see org.xmldb.api.modules.XUpdateQueryService#updateResource(java.lang.String, java.lang.String)
	 */
	public long updateResource(String id, String commands) throws XMLDBException {
		LOG.debug("processing xupdate:\n" + commands);
		final Vector<Object> params = new Vector<Object>();
		byte[] xupdateData = commands.getBytes(UTF_8);
        //TODO : use dedicated function in XmldbURI
		params.addElement(parent.getPath() + "/" + id);
		params.addElement(xupdateData);
		try {
			final Integer mods = (Integer) parent.getClient().execute("xupdateResource", params);
			LOG.debug("processed " + mods + " modifications");
			return mods.intValue();
		} catch (final XmlRpcException e) {
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
