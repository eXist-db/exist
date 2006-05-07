/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.update;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.AbstractExpression;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.SAXException;

/**
 * @author wolf
 *
 */
public abstract class Modification extends AbstractExpression {

	protected final static Logger LOG =
		Logger.getLogger(Modification.class);
	
	protected final Expression select;
	protected final Expression value;
	
	protected DocumentSet lockedDocuments = null;
	
	/**
	 * @param context
	 */
	public Modification(XQueryContext context, Expression select, Expression value) {
		super(context);
		this.select = select;
		this.value = value;
	}

	public int getCardinality() {
		return Cardinality.EMPTY;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#returnsType()
	 */
	public int returnsType() {
		return Type.EMPTY;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		super.resetState();
		select.resetState();
		if (value != null)
			value.resetState();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression, int)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		contextInfo.setParent(this);
		contextInfo.addFlag(IN_UPDATE);
		select.analyze(contextInfo);
		if (value != null)
			value.analyze(contextInfo);
	}
	
	/**
	 * Acquire a lock on all documents processed by this modification.
	 * We have to avoid that node positions change during the
	 * operation.
	 * 
	 * @param nl
	 * @return
	 * @throws LockException
	 */
	protected StoredNode[] selectAndLock(NodeSet nodes) throws LockException, PermissionDeniedException, 
		XPathException {
	    Lock globalLock = context.getBroker().getBrokerPool().getGlobalUpdateLock();
	    try {
	        globalLock.acquire(Lock.READ_LOCK);
	       
	        lockedDocuments = nodes.getDocumentSet();
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
	        lockedDocuments.lock(true);
	        
		    StoredNode ql[] = new StoredNode[nodes.getLength()];
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (StoredNode)nodes.item(i);
				DocumentImpl doc = (DocumentImpl)ql[i].getOwnerDocument();
				doc.setBroker(context.getBroker());
			}
			return ql;
	    } finally {
	        globalLock.release();
	    }
	}
	
	protected Sequence deepCopy(Sequence inSeq) throws XPathException {
		context.pushDocumentContext();
		MemTreeBuilder builder = context.getDocumentBuilder();
		DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
		Serializer serializer = context.getBroker().getSerializer();
		serializer.setReceiver(receiver);
		
		try {
			Sequence out = new ValueSequence();
			for (SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
				Item item = i.nextItem();
				if (Type.subTypeOf(item.getType(), Type.NODE)) {
					if (((NodeValue)item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
						int last = builder.getDocument().getLastNode();
						NodeProxy p = (NodeProxy) item;
						serializer.toReceiver(p);
						item = builder.getDocument().getNode(last + 1);
					} else {
						((org.exist.memtree.NodeImpl)item).deepCopy();
					}
				}
				out.add(item);
			}
			return out;
		} catch(SAXException e) {
			throw new XPathException(getASTNode(), e.getMessage(), e);
		} finally {
			context.popDocumentContext();
		}
	}
	
	/**
	 * Release all acquired document locks.
	 */
	protected void unlockDocuments() {
	    if(lockedDocuments == null)
	        return;
	    lockedDocuments.unlock(true);
        lockedDocuments = null;
	}
	
	/**
	 * Check if any of the modified documents needs defragmentation.
	 * 
	 * Defragmentation will take place if the number of split pages in the
	 * document exceeds the limit defined in the configuration file.
	 *  
	 * @param docs
	 */
	protected void checkFragmentation(Txn transaction, DocumentSet docs) throws EXistException {
		DBBroker broker = context.getBroker();
	    for(Iterator i = docs.iterator(); i.hasNext(); ) {
	        DocumentImpl next = (DocumentImpl) i.next();
	        if(next.getMetadata().getSplitCount() > broker.getFragmentationLimit())
	            broker.defragXMLResource(transaction, next);
	        broker.checkXMLResourceConsistency(next);
	    }
	}
	
    final static class IndexListener implements NodeIndexListener {

		StoredNode[] nodes;

		public IndexListener(StoredNode[] nodes) {
			this.nodes = nodes;
		}

		/* (non-Javadoc)
		 * @see org.exist.dom.NodeIndexListener#nodeChanged(org.exist.dom.NodeImpl)
		 */
		public void nodeChanged(StoredNode node) {
			long address = node.getInternalAddress();
			for (int i = 0; i < nodes.length; i++) {
				if (StorageAddress.equals(nodes[i].getInternalAddress(), address)) {
					nodes[i] = node;
				}
			}
		}
	}
}
