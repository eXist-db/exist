/*
 * LocalIndexQueryService.java - Mar 5, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Occurrences;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class LocalIndexQueryService implements IndexQueryService {

	private LocalCollection parent = null;
	private BrokerPool pool = null;
	private User user;
	
	public LocalIndexQueryService(
		User user,
		BrokerPool pool,
		LocalCollection parent) {
		this.user = user;
		this.pool = pool;
		this.parent = parent;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#getIndexedElements(boolean)
	 */
	public Occurrences[] getIndexedElements(boolean inclusive)
		throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			return broker.scanIndexedElements(parent.collection, inclusive);
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				"database access error",
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, 
				"permission denied", e);
		} finally {
			pool.release(broker);
		}
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#getName()
	 */
	public String getName() throws XMLDBException {
		return "IndexQueryService";
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
		if (!(col instanceof LocalCollection))
			throw new XMLDBException(
				ErrorCodes.INVALID_COLLECTION,
				"incompatible collection type: " + col.getClass().getName());
		parent = (LocalCollection) col;
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

	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#scanIndexTerms(java.lang.String, java.lang.String, boolean)
	 */
	public Occurrences[] scanIndexTerms(
		String start,
		String end,
		boolean inclusive)
		throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			return broker.getTextEngine().scanIndexTerms(user, parent.collection, 
					start, end, inclusive);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
				"permission denied", e);
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				"database access error",
				e);
		} finally {
			pool.release(broker);
		}
	}

}
