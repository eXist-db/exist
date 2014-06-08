/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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

import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.md.MetaData;
import org.exist.storage.md.Metas;
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

import static org.exist.storage.md.MDStorageManager.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Reindex extends BasicFunction {
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("reindex", NAMESPACE_URI, PREFIX),
			"",
			null,
			new SequenceType(Type.STRING, Cardinality.EMPTY));

	/**
	 * @param context
	 */
	public Reindex(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		
		BrokerPool db = null;
		DBBroker broker = null;
		try {
			db = BrokerPool.getInstance();
			
			broker = db.get(null);
			
			Subject currentSubject = broker.getSubject();
			try {
			    broker.setSubject( db.getSecurityManager().getSystemSubject() );
    			
    			Collection col = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
    			
    			final MetaData md = MetaData.get();
    			
    			checkSub(broker, md, col);
			} finally {
			    broker.setSubject(currentSubject);
			}
			
		} catch (Exception e) {
			throw new XPathException(this, e);
		} finally {
			if (db != null)
				db.release(broker);
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
	
	private void checkSub(DBBroker broker, MetaData md, Collection col) throws PermissionDeniedException, IOException, LockException, TriggerException {
		
        for (Iterator<XmldbURI> i = col.collectionIterator(broker); i.hasNext(); ) {
            XmldbURI childName = i.next();
            Collection childColl = broker.getOrCreateCollection(null, XmldbURI.ROOT_COLLECTION_URI.append(childName));
            
            checkSub(broker, md, childColl);
        }
		
		MutableDocumentSet childDocs = new DefaultDocumentSet();
		LockedDocumentMap lockedDocuments = new LockedDocumentMap();
		col.getDocuments(broker, childDocs, lockedDocuments, Lock.WRITE_LOCK);
		
		for (Iterator<DocumentImpl> itChildDocs = childDocs.getDocumentIterator(); itChildDocs.hasNext();) {
			DocumentImpl childDoc = itChildDocs.next();
			
			Metas metas = md.addMetas(childDoc);
			
			if (metas != null) {
			    //XXX: md.indexMetas(metas);
			    ;
			}
		}
	}
}
