/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class CollectionEvents implements CollectionTrigger {

    @Override
    public void configure(DBBroker broker, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException {
    }

    @Override
    public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        // System.out.println("beforeCreateCollection "+uri);
    }

    @Override
    public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) {
        // System.out.println("afterCreateCollection "+collection.getURI());
        try {
            MDStorageManager.storage().addMetas(collection);
        } catch (Throwable e) {
            MDStorageManager.LOG.fatal(e,e);
        }

//        index(collection);
    }

    @Override
    public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) {
        // System.out.println("afterCopyCollection "+collection.getURI());
        try {
            MDStorageManager.storage().copyMetas(oldUri, collection);
        } catch (Throwable e) {
            MDStorageManager.LOG.fatal(e,e);
        }

//        index(collection);
    }

    @Override
    public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        // System.out.println("beforeMoveCollection "+collection.getURI());
        try {
            for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
                DocumentImpl doc = i.next();
                try {
                    MDStorageManager.storage().moveMetas(collection.getURI().append(doc.getFileURI()), newUri.append(doc.getFileURI()));
                } catch (Throwable e) {
                    MDStorageManager.LOG.fatal(e,e);
                }
            }
        } catch (PermissionDeniedException e) {
            throw new TriggerException(e);
        }
    }

    @Override
    public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) {
        // System.out.println("afterMoveCollection "+oldUri+" to "+collection.getURI());
        try {
            MDStorageManager.storage().moveMetas(oldUri, collection.getURI());
        } catch (Throwable e) {
            MDStorageManager.LOG.fatal(e,e);
        }

//        index(collection);
    }

    private void deleteCollectionRecursive(DBBroker broker, Collection collection) throws PermissionDeniedException {

        MetaData md = MDStorageManager.storage();

        for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
            DocumentImpl doc = i.next();
            try {
                md.delMetas(doc.getURI());
            } catch (Throwable e) {
                MDStorageManager.LOG.fatal(e,e);
            }
        }

        md.delMetas(collection.getURI());
    }

    @Override
    public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
        // System.out.println("beforeDeleteCollection "+collection.getURI());
        try {
            deleteCollectionRecursive(broker, collection);
        } catch (PermissionDeniedException e) {
            throw new TriggerException(e);
        }
    }

    @Override
    public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) {
        // System.out.println("afterDeleteCollection "+uri);
        try {
            MDStorageManager.storage().delMetas(uri);
        } catch (Throwable e) {
            MDStorageManager.LOG.fatal(e,e);
        }
    }

//    private void index(Collection col) {
//        try {
//            if (db != null) {
//                IndexWorker worker = db.getActiveBroker().getIndexController().getWorkerByIndexId(MDStorageManager.LUCENE_ID);
//                if (worker != null) {
//                    worker.indexCollection(col);
//                }
//            }
//        } catch (Throwable e) {
//            MDStorageManager.LOG.fatal(e,e);
//        }
//    }
}
