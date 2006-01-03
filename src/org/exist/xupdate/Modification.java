/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xupdate;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.NodeList;

/**
 * Base class for all XUpdate modifications.
 * 
 * @author Wolfgang Meier
 */
public abstract class Modification {

	protected final static Logger LOG = Logger.getLogger(Modification.class);

	protected String selectStmt = null;
	protected NodeList content = null;
	protected DBBroker broker;
	protected DocumentSet docs;
	protected Map namespaces;
	protected Map variables;
	protected DocumentSet lockedDocuments = null;

	/**
	 * Constructor for Modification.
	 */
	public Modification(DBBroker broker, DocumentSet docs, String selectStmt,
	        Map namespaces, Map variables) {
		this.selectStmt = selectStmt;
		this.broker = broker;
		this.docs = docs;
		this.namespaces = new HashMap(namespaces);
		this.variables = new TreeMap(variables);
	}

	/**
	 * Process the modification. This is the main method that has to be implemented 
	 * by all subclasses.
	 * 
	 * @return
	 * @throws PermissionDeniedException
	 * @throws LockException
	 * @throws EXistException
	 * @throws XPathException
	 */
	public abstract long process(Txn transaction) throws PermissionDeniedException, LockException, 
		EXistException, XPathException;

	public abstract String getName();

	public void setContent(NodeList nodes) {
		content = nodes;
	}

	/**
	 * Evaluate the select expression.
	 * 
	 * @param docs
	 * @return
	 * @throws PermissionDeniedException
	 * @throws EXistException
	 * @throws XPathException
	 */
	protected NodeList select(DocumentSet docs)
		throws PermissionDeniedException, EXistException, XPathException {
		XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		Source source = new StringSource(selectStmt);
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
		XQueryContext context;
		if(compiled == null)
		    context = xquery.newContext();
		else
		    context = compiled.getContext();

		context.setExclusiveMode(true);
		context.setStaticallyKnownDocuments(docs);
		declareNamespaces(context);
		declareVariables(context);
		if(compiled == null)
			try {
				compiled = xquery.compile(context, source);
			} catch (IOException e) {
				throw new EXistException("An exception occurred while compiling the query: " + e.getMessage());
			}
		
		Sequence resultSeq = null;
		try {
			resultSeq = xquery.execute(compiled, null);
		} finally {
			pool.returnCompiledXQuery(source, compiled);
		}

		if (!(resultSeq.getLength() == 0 || Type.subTypeOf(resultSeq.getItemType(), Type.NODE)))
			throw new EXistException("select expression should evaluate to a node-set; got " +
			        Type.getTypeName(resultSeq.getItemType()));
		LOG.debug("found " + resultSeq.getLength() + " for select: " + selectStmt);
		return (NodeList)resultSeq.toNodeSet();
	}

	/**
	 * @param context
	 * @throws XPathException
	 */
	protected void declareVariables(XQueryContext context) throws XPathException {
		for (Iterator i = variables.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry entry = (Map.Entry) i.next();
			context.declareVariable(entry.getKey().toString(), entry.getValue());
		}
	}

	/**
	 * @param context
	 */
	protected void declareNamespaces(XQueryContext context) throws XPathException {
		Map.Entry entry;
		for (Iterator i = namespaces.entrySet().iterator(); i.hasNext();) {
			entry = (Map.Entry) i.next();
			context.declareNamespace(
				(String) entry.getKey(),
				(String) entry.getValue());
		}
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
	protected StoredNode[] selectAndLock() throws LockException, PermissionDeniedException, 
		EXistException, XPathException {
	    Lock globalLock = broker.getBrokerPool().getGlobalUpdateLock();
	    try {
	        globalLock.acquire(Lock.READ_LOCK);
	        
	        NodeList nl = select(docs);
	        lockedDocuments = ((NodeSet)nl).getDocumentSet();
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
	        lockedDocuments.lock(true);
	        
		    StoredNode ql[] = new StoredNode[nl.getLength()];
		    DocumentImpl doc;
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (StoredNode)nl.item(i);
				doc = (DocumentImpl)ql[i].getOwnerDocument();
				doc.setBroker(broker);
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
	protected void checkFragmentation(Txn transaction, DocumentSet docs) throws EXistException {
	    for(Iterator i = docs.iterator(); i.hasNext(); ) {
	        DocumentImpl next = (DocumentImpl) i.next();
	        if(next.getMetadata().getSplitCount() > broker.getFragmentationLimit())
	            broker.defrag(transaction, next);
	        broker.consistencyCheck(next);
	    }
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("<xu:");
		buf.append(getName());
		buf.append(" select=\"");
		buf.append(selectStmt);
		buf.append("\">");
//		buf.append(XMLUtil.dump(content));
		buf.append("</xu:");
		buf.append(getName());
		buf.append(">");
		return buf.toString();
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

	final static class NodeComparator implements Comparator {

		/* (non-Javadoc)
		* @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		*/
		public int compare(Object o1, Object o2) {
			StoredNode n1 = (StoredNode) o1;
			StoredNode n2 = (StoredNode) o2;
			if (n1.getInternalAddress() == n2.getInternalAddress())
				return 0;
			else if (n1.getInternalAddress() < n2.getInternalAddress())
				return -1;
			else
				return 1;
		}
	}
}
