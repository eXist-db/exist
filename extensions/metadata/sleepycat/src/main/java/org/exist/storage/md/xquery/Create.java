package org.exist.storage.md.xquery;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.md.MetaData;
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

public class Create extends BasicFunction {
	
	private static final Logger logger = Logger.getLogger(Create.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("create", MetadataModule.NAMESPACE_URI, MetadataModule.PREFIX),
			"",
			null,
			new SequenceType(Type.STRING, Cardinality.EMPTY));

	/**
	 * @param context
	 */
	public Create(XQueryContext context) {
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
			
			Collection col = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
			
			checkSub(broker, col);
			
		} catch (Exception e) {
			throw new XPathException(e);
		} finally {
			if (db != null)
				db.release(broker);
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
	
	private void checkSub(DBBroker broker, Collection col) throws PermissionDeniedException, IOException, LockException, TriggerException {
		
        for (Iterator i = col.collectionIterator(broker); i.hasNext(); ) {
            XmldbURI childName = (XmldbURI) i.next();
            Collection childColl = broker.getOrCreateCollection(null, XmldbURI.ROOT_COLLECTION_URI.append(childName));
            
            checkSub(broker, childColl);
        }
		
		MutableDocumentSet childDocs = new DefaultDocumentSet();
		LockedDocumentMap lockedDocuments = new LockedDocumentMap();
		col.getDocuments(broker, childDocs, lockedDocuments, Lock.WRITE_LOCK);
		
		for (Iterator itChildDocs = childDocs.getDocumentIterator(); itChildDocs.hasNext();) {
			DocumentImpl childDoc = (DocumentImpl) itChildDocs.next();
			
			MetaData.get().addMetas(childDoc);
		}
	}
}
