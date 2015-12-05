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
package org.exist.storage.md.xquery;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.md.MetaData;
import org.exist.storage.md.MDStorageManager;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Check extends BasicFunction {
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("create", MDStorageManager.NAMESPACE_URI, MDStorageManager.PREFIX),
			"",
			null,
			new SequenceType(Type.STRING, Cardinality.EMPTY));

	/**
	 * @param context
	 */
	public Check(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		try {
			final BrokerPool db = BrokerPool.getInstance();

			try(final DBBroker broker = db.get(Optional.of(context.getSubject()))) {

				Collection col = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);

				checkSub(broker, col);
			}
		} catch (Exception e) {
		    //e.printStackTrace();
			throw new XPathException(this, e);
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
	
	private void checkSub(DBBroker broker, Collection col) throws PermissionDeniedException, IOException, LockException, TriggerException {
		
        for (Iterator<XmldbURI> i = col.collectionIterator(broker); i.hasNext(); ) {
            XmldbURI childName = i.next();
            Collection childColl = broker.getCollection(col.getURI().append(childName));
            
            if (childColl != null) {
                checkSub(broker, childColl);
            }
        }
		
		MutableDocumentSet childDocs = new DefaultDocumentSet();
		LockedDocumentMap lockedDocuments = new LockedDocumentMap();
		col.getDocuments(broker, childDocs, lockedDocuments, Lock.WRITE_LOCK);
		
		for (Iterator<DocumentImpl> itChildDocs = childDocs.getDocumentIterator(); itChildDocs.hasNext();) {
			DocumentImpl childDoc = itChildDocs.next();
			
			MetaData.get().addMetas(childDoc);
		} 
	}
}
