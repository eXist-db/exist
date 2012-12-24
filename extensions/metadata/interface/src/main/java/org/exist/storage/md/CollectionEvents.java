/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.storage.md;

import java.util.Iterator;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CollectionEvents implements CollectionTrigger {

	@Override
	public void configure(DBBroker broker, Collection parent, Map parameters) throws TriggerException {
	}

	@Override
	public void prepare(int event, DBBroker broker, Txn txn,
			Collection collection, Collection newCollection)
			throws TriggerException {
	}

	@Override
	public void finish(int event, DBBroker broker, Txn txn,
			Collection collection, Collection newCollection) {
	}

	@Override
	public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
//		System.out.println("beforeCreateCollection "+uri);
	}

	@Override
	public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
//		System.out.println("afterCreateCollection "+collection.getURI());
		try {
			MDStorageManager._.md.addMetas(collection);
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}

	@Override
	public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
//		System.out.println("afterCopyCollection "+collection.getURI());
		try {
			MDStorageManager._.md.copyMetas(oldUri, collection);
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}

	@Override
	public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
//		System.out.println("beforeMoveCollection "+collection.getURI());
		try {
	        for(Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
	            DocumentImpl doc = i.next();
	            MDStorageManager._.md.moveMetas(
            		collection.getURI().append(doc.getFileURI()), 
            		newUri.append(doc.getFileURI())
        		);
	        }
		} catch (PermissionDeniedException e) {
			throw new TriggerException(e);
		}
	}

	@Override
	public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
//		System.out.println("afterMoveCollection "+oldUri+" to "+collection.getURI());
		MDStorageManager._.md.moveMetas(oldUri, collection.getURI());
	}
	
	private void deleteCollectionRecursive(DBBroker broker, Collection collection) throws PermissionDeniedException {
        for(Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
            DocumentImpl doc = i.next();
            MDStorageManager._.md.delMetas(doc.getURI());
        }

		final XmldbURI uri = collection.getURI();

		for(Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
            final XmldbURI childName = i.next();
            //TODO : resolve URIs !!! name.resolve(childName)
            final Collection child = broker.openCollection(uri.append(childName), Lock.NO_LOCK);
            if(child == null) {
//                LOG.warn("Child collection " + childName + " not found");
            } else {
                try {
                	deleteCollectionRecursive(broker, child);
                } finally {
                    child.release(Lock.NO_LOCK);
                }
            }
        }

	}

	@Override
	public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
//		System.out.println("beforeDeleteCollection "+collection.getURI());
		try {
			deleteCollectionRecursive(broker, collection);
		} catch (PermissionDeniedException e) {
			throw new TriggerException(e);
		}
	}

	@Override
	public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
//		System.out.println("afterDeleteCollection "+uri);
		try {
			MDStorageManager._.md.delMetas(uri);
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}
}
