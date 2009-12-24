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
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Occurrences;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;

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
            broker.reindexCollection(parent.getCollection().getURI());
            broker.sync(Sync.MAJOR_SYNC);
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    
    /** (non-Javadoc)
     * @deprecated Use XmldbURI version instead
     * @see org.exist.xmldb.IndexQueryService#reindexCollection(java.lang.String)
     */
    public void reindexCollection(String collectionPath) throws XMLDBException {
    	try{
    		reindexCollection(XmldbURI.xmldbUriFor(collectionPath));
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
        /* (non-Javadoc)
         * @see org.exist.xmldb.IndexQueryService#reindexCollection(java.lang.String)
         */
   public void reindexCollection(XmldbURI collectionPath) throws XMLDBException {
       if (parent != null)
    	   collectionPath = parent.getPathURI().resolveCollectionPath(collectionPath);        
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            broker.reindexCollection(collectionPath);
            broker.sync(Sync.MAJOR_SYNC);
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#configure(java.lang.String)
	 */
	public void configureCollection(String configData) throws XMLDBException {
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(user);
            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, parent.getCollection(), configData);
            transact.commit(txn);
            System.out.println("Configured '" + parent.getCollection().getURI() + "'");
        } catch (CollectionConfigurationException e) {
            transact.abort(txn);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
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
			MutableDocumentSet docs = new DefaultDocumentSet();
			parent.getCollection().allDocs(broker, docs, inclusive, true);
			return broker.getTextEngine().scanIndexTerms(docs, docs.docsToNodeSet(),  start, end);
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
	
	public Occurrences[] scanIndexTerms(
			String xpath,
			String start,
			String end)
			throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			XQuery xquery = broker.getXQueryService();
			Sequence nodes = xquery.execute(xpath, null, parent.getAccessContext());
			return broker.getTextEngine().scanIndexTerms(nodes.getDocumentSet(), 
					nodes.toNodeSet(),  start, end);
		} catch (EXistException e) {
			throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					"database access error",
					e);
		} catch (XPathException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
					e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}
}
