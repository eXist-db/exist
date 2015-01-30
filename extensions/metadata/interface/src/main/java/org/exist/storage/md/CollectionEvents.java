/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.storage.md;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.util.LockException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CollectionEvents implements CollectionTrigger {

	@Override
	public void configure(DBBroker broker, Txn txn, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException {
	}

	@Override
	public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
//		System.out.println("beforeCreateCollection "+uri);
	}

	@Override
	public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
//		System.out.println("afterCreateCollection "+collection.getURI());
		try {
			MDStorageManager.inst.md.addMetas(collection);
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
			MDStorageManager.inst.md.copyMetas(oldUri, collection);
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
	            MDStorageManager.inst.md.moveMetas(
            		collection.getURI().append(doc.getFileURI()), 
            		newUri.append(doc.getFileURI())
        		);
	        }
		} catch (PermissionDeniedException | LockException e) {
			throw new TriggerException(e);
		}
	}

	@Override
	public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
//		System.out.println("afterMoveCollection "+oldUri+" to "+collection.getURI());
		MDStorageManager.inst.md.moveMetas(oldUri, collection.getURI());
	}
	
	private void deleteCollectionRecursive(DBBroker broker, Collection collection) throws PermissionDeniedException, LockException {
        for(Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
            DocumentImpl doc = i.next();
            MDStorageManager.inst.md.delMetas(doc.getURI());
        }

		final XmldbURI uri = collection.getURI();

		for(Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
			final XmldbURI childName = i.next();
			//TODO : resolve URIs !!! name.resolve(childName)
			try (final Collection child = broker.openCollection(uri.append(childName), LockMode.NO_LOCK)) {
				if (child == null) {
//                LOG.warn("Child collection " + childName + " not found");
				} else {
					deleteCollectionRecursive(broker, child);
				}
			}
        }

	}

	@Override
	public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
//		System.out.println("beforeDeleteCollection "+collection.getURI());
		try {
			deleteCollectionRecursive(broker, collection);
		} catch (PermissionDeniedException | LockException e) {
			throw new TriggerException(e);
		}
	}

	@Override
	public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
//		System.out.println("afterDeleteCollection "+uri);
		try {
			MDStorageManager.inst.md.delMetas(uri);
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}
}
