/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-04 Wolfgang M. Meier wolfgang@exist-db.org
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
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
     * @see org.exist.xmldb.IndexQueryService#reindexCollection()
     */
    public void reindexCollection() throws XMLDBException {
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            broker.reindex(parent.getCollection().getName());
            broker.sync();
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xmldb.IndexQueryService#reindexCollection(java.lang.String)
     */
    public void reindexCollection(String collectionPath) throws XMLDBException {
        String path = (collectionPath.startsWith("/db") ? collectionPath : 
    		parent.getPath() + '/' + collectionPath);
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            broker.reindex(path);
            broker.sync();
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#getIndexedElements(boolean)
	 */
	public Occurrences[] getIndexedElements(boolean inclusive)
		throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			return broker.getElementIndex().scanIndexedElements(parent.getCollection(), inclusive);
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
			return broker.getTextEngine().scanIndexTerms(user, parent.getCollection(), 
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
