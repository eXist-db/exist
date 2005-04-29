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
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.store.StorageAddress;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.xquery.AbstractExpression;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
public abstract class Modification extends AbstractExpression {

	protected final static Logger LOG =
		Logger.getLogger(Modification.class);
	
	protected Expression select;
	protected Expression value;
	
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
		select.resetState();
		if (value != null)
			value.resetState();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression, int)
	 */
	public void analyze(Expression parent, int flags) throws XPathException {
		select.analyze(this, flags);
		if (value != null)
			value.analyze(this, flags);
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
	protected NodeImpl[] selectAndLock(NodeSet nodes) throws LockException, PermissionDeniedException, 
		XPathException {
	    Lock globalLock = context.getBroker().getBrokerPool().getGlobalUpdateLock();
	    try {
	        globalLock.acquire(Lock.READ_LOCK);
	       
	        lockedDocuments = nodes.getDocumentSet();
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
	        lockedDocuments.lock(true);
	        
		    NodeImpl ql[] = new NodeImpl[nodes.getLength()];
		    DocumentImpl doc;
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (NodeImpl)nodes.item(i);
				doc = (DocumentImpl)ql[i].getOwnerDocument();
				doc.setBroker(context.getBroker());
			}
			return ql;
	    } finally {
	        globalLock.release();
	    }
	}
	
	/**
	 * Release all acquired document locks.
	 */
	protected void unlockDocuments() {
	    if(lockedDocuments == null)
	        return;
	    lockedDocuments.unlock(true);
	}
	
	/**
	 * Check if any of the modified documents needs defragmentation.
	 * 
	 * Defragmentation will take place if the number of split pages in the
	 * document exceeds the limit defined in the configuration file.
	 *  
	 * @param docs
	 */
	protected void checkFragmentation(DocumentSet docs) throws EXistException {
		DBBroker broker = context.getBroker();
	    for(Iterator i = docs.iterator(); i.hasNext(); ) {
	        DocumentImpl next = (DocumentImpl) i.next();
	        if(next.getSplitCount() > broker.getFragmentationLimit())
	            broker.defrag(next);
	        broker.consistencyCheck(next);
	    }
	}
	
	final static class IndexListener implements NodeIndexListener {

		NodeImpl[] nodes;

		public IndexListener(NodeImpl[] nodes) {
			this.nodes = nodes;
		}

		/* (non-Javadoc)
		 * @see org.exist.dom.NodeIndexListener#nodeChanged(org.exist.dom.NodeImpl)
		 */
		public void nodeChanged(NodeImpl node) {
			final long address = node.getInternalAddress();
			for (int i = 0; i < nodes.length; i++) {
				if (StorageAddress.equals(nodes[i].getInternalAddress(), address)) {
					nodes[i] = node;
				}
			}
		}

		/* (non-Javadoc)
		 * @see org.exist.dom.NodeIndexListener#nodeChanged(long, long)
		 */
		public void nodeChanged(long oldAddress, long newAddress) {
			// Ignore the address change
			// TODO: is this really save?
			
//			for (int i = 0; i < nodes.length; i++) {
//				if (StorageAddress.equals(nodes[i].getInternalAddress(), oldAddress)) {
//					nodes[i].setInternalAddress(newAddress);
//				}
//			}

		}

	}
}
