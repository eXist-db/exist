/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2012 The eXist Project
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
package org.exist.collections.triggers;

import java.util.Map.Entry;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.exist.security.PermissionDeniedException;

/**
 * @author wolf
 */
public class Dumper extends FilteringTrigger implements DocumentTrigger {

    /* (non-Javadoc)
     * @see org.exist.collections.FilteringTrigger#configure(java.util.Map)
     */
    @Override
    public void configure(DBBroker broker, Txn transaction, Collection parent, Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, transaction, parent, parameters);
        System.out.println("parameters:");

        for(final Entry<String, List<?>> entry : parameters.entrySet()) {
            System.out.print(entry.getKey() + " = " + entry.getValue());
        }
    }
	
    public void prepare(int event, DBBroker broker, Txn txn, XmldbURI documentName, DocumentImpl existingDocument) throws TriggerException {

        System.out.println("\nstoring document " + documentName + " into collection " + getCollection().getURI());
        if(existingDocument != null) {
            System.out.println("replacing document " + ((DocumentImpl)existingDocument).getFileURI());
        }
        System.out.println("collection contents:");
        final DefaultDocumentSet docs = new DefaultDocumentSet();
        
        try {
            getCollection().getDocuments(broker, docs);
        } catch (final PermissionDeniedException | LockException e) {
            throw new TriggerException(e.getMessage(), e);
        }
        
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            System.out.println("\t" + i.next().getFileURI());
        }
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        prepare(-1, broker, txn, uri, null);
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        prepare(-1, broker, txn, document.getURI(), document);
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        prepare(-1, broker, txn, newUri, document);
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        prepare(-1, broker, txn, newUri, document);
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        prepare(-1, broker, txn, document.getURI(), document);
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
    }

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}
}