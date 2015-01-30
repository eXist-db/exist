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

import java.util.List;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DocumentEvents extends FilteringTrigger {

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
//		System.out.println("afterCreateDocument "+document.getURI());
		try {
			MDStorageManager.inst.md.addMetas(document);
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
//		System.out.println("afterUpdateDocument "+document.getURI());
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
//		System.out.println("afterCopyDocument "+document.getURI());
		MDStorageManager.inst.md.copyMetas(oldUri, document);
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
//		System.out.println("afterMoveDocument "+oldUri+" to "+document.getURI());
		try {
			MDStorageManager.inst.md.moveMetas(oldUri, document.getURI());
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
//		System.out.println("afterDeleteDocument "+uri);
		try {
			MDStorageManager.inst.md.delMetas(uri);
		} catch (Throwable e) {
			MDStorageManager.LOG.fatal(e);
		}
	}

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void configure(DBBroker broker, Txn txn, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException {
	}
}
