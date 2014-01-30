/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
import org.exist.collections.Collection;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.DocumentTriggers;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.NullAccessContextException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
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

	/** select Statement in the current XUpdate definition;
	 * defines the set of nodes to which this XUpdate might apply. */
	protected String selectStmt = null;
	
    /**
     * NodeList to keep track of created document fragments within
     * the currently processed XUpdate modification.
     * see {@link XUpdateProcessor#contents}
     */
	protected NodeList content = null;
	protected DBBroker broker;
	/** Documents concerned by this XUpdate modification,
	 * i.e. the set of documents to which this XUpdate might apply. */
	protected DocumentSet docs;
	protected Map<String, String> namespaces;
	protected Map<String, Object> variables;
	protected DocumentSet lockedDocuments = null;
	protected MutableDocumentSet modifiedDocuments = new DefaultDocumentSet();
    protected Int2ObjectHashMap<DocumentTrigger> triggers;

    private AccessContext accessCtx;

	@SuppressWarnings("unused")
	private Modification() {}
	/**
	 * Constructor for Modification.
	 */
	public Modification(DBBroker broker, DocumentSet docs, String selectStmt,
	        Map<String, String> namespaces, Map<String, Object> variables) {
		this.selectStmt = selectStmt;
		this.broker = broker;
		this.docs = docs;
		this.namespaces = new HashMap<String, String>(namespaces);
		this.variables = new TreeMap<String, Object>(variables);
        this.triggers = new Int2ObjectHashMap<DocumentTrigger>(97);
        // DESIGN_QUESTION : wouldn't that be nice to apply selectStmt right here ?
	}

	public final void setAccessContext(AccessContext accessCtx) {
		if(accessCtx == null)
			{throw new NullAccessContextException();}
		if(this.accessCtx != null)
			{throw new IllegalStateException("Access context can only be set once.");}
		this.accessCtx = accessCtx;
		
	}
	public final AccessContext getAccessContext() {
		if(accessCtx == null)
			{throw new IllegalStateException("Access context has not been set.");}
		return accessCtx;
	}
	
	/**
     * Process the modification. This is the main method that has to be implemented 
     * by all subclasses.
     * 
     * @param transaction 
     * @throws PermissionDeniedException 
     * @throws LockException 
     * @throws EXistException 
     * @throws XPathException 
     */
	public abstract long process(Txn transaction) throws PermissionDeniedException, LockException, 
		EXistException, XPathException, TriggerException;

	public abstract String getName();

	public void setContent(NodeList nodes) {
		content = nodes;
	}

	/**
	 * Evaluate the select expression.
	 * 
	 * @param docs
	 * @return The selected nodes.
	 * @throws PermissionDeniedException
	 * @throws EXistException
	 * @throws XPathException
	 */
	protected NodeList select(DocumentSet docs)
		throws PermissionDeniedException, EXistException, XPathException {
		final XQuery xquery = broker.getXQueryService();
		final XQueryPool pool = xquery.getXQueryPool();
		final Source source = new StringSource(selectStmt);
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
		XQueryContext context;
		if(compiled == null)
		    {context = xquery.newContext(getAccessContext());}
		else
		    {context = compiled.getContext();}

		context.setStaticallyKnownDocuments(docs);
		declareNamespaces(context);
		declareVariables(context);
		if(compiled == null)
			try {
				compiled = xquery.compile(context, source);
			} catch (final IOException e) {
				throw new EXistException("An exception occurred while compiling the query: " + e.getMessage());
			}
		
		Sequence resultSeq = null;
		try {
			resultSeq = xquery.execute(compiled, null);
		} finally {
			pool.returnCompiledXQuery(source, compiled);
		}

		if (!(resultSeq.isEmpty() || Type.subTypeOf(resultSeq.getItemType(), Type.NODE)))
			{throw new EXistException("select expression should evaluate to a node-set; got " +
			        Type.getTypeName(resultSeq.getItemType()));}
		if (LOG.isDebugEnabled())
			{LOG.debug("found " + resultSeq.getItemCount() + " for select: " + selectStmt);}
		return (NodeList)resultSeq.toNodeSet();
	}

	/**
	 * @param context
	 * @throws XPathException
	 */
	protected void declareVariables(XQueryContext context) throws XPathException {
		for (final Iterator<Map.Entry<String, Object>> i = variables.entrySet().iterator(); i.hasNext(); ) {
			final Map.Entry<String, Object> entry = (Map.Entry<String, Object>) i.next();
			context.declareVariable(entry.getKey().toString(), entry.getValue());
		}
	}

	/**
	 * @param context
	 */
	protected void declareNamespaces(XQueryContext context) throws XPathException {
		Map.Entry<String, String> entry;
		for (final Iterator<Map.Entry<String, String>> i = namespaces.entrySet().iterator(); i.hasNext();) {
			entry = (Map.Entry<String, String>) i.next();
			context.declareNamespace(
				entry.getKey(),
				entry.getValue());
		}
	}

	/**
	 * Acquire a lock on all documents processed by this modification. We have
	 * to avoid that node positions change during the operation.
	 * feature trigger_update :
	 * At the same time we leverage on the fact that it's called before 
	 * database modification to call the eventual triggers.
	 * 
	 * @return The selected document nodes.
	 * 
	 * @throws LockException
	 * @throws PermissionDeniedException
	 * @throws EXistException
	 * @throws XPathException 
	 * @throws TriggerException 
	 */
	protected final StoredNode[] selectAndLock(Txn transaction)
			throws LockException, PermissionDeniedException, EXistException,
			XPathException, TriggerException {
	    final Lock globalLock = broker.getBrokerPool().getGlobalUpdateLock();
	    try {
	        globalLock.acquire(Lock.READ_LOCK);
	        
	        final NodeList nl = select(docs);
	        lockedDocuments = ((NodeSet)nl).getDocumentSet();
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
	        lockedDocuments.lock(broker, true, false);
	        
		    final StoredNode ql[] = new StoredNode[nl.getLength()];		    
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (StoredNode)nl.item(i);
				final DocumentImpl doc = (DocumentImpl)ql[i].getOwnerDocument();
				
				// call the eventual triggers
				// TODO -jmv separate loop on docs and not on nodes
			
				//prepare Trigger
				prepareTrigger(transaction, doc);
			}
			return ql;
	    } finally {
	        globalLock.release(Lock.READ_LOCK);
	    }
	}
	
	/**
	 * Release all acquired document locks;
	 * feature trigger_update :
	 * at the same time we leverage on the fact that it's called after 
	 * database modification to call the eventual triggers
	 * @throws TriggerException 
	 */
	protected final void unlockDocuments(Txn transaction) throws TriggerException
	{
		if(lockedDocuments == null)
			{return;}
		
		//finish Trigger
		final Iterator<DocumentImpl> iterator = modifiedDocuments.getDocumentIterator();
		while (iterator.hasNext()) {
			finishTrigger(transaction, iterator.next());
		}
        triggers.clear();
        modifiedDocuments.clear();
		
		//unlock documents
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
        int fragmentationLimit = -1;
        final Object property = broker.getBrokerPool().getConfiguration().getProperty(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
        if (property != null)
	        {fragmentationLimit = ((Integer)property).intValue();}		
	    for(final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
	        final DocumentImpl next = i.next();
	        if(next.getMetadata().getSplitCount() > fragmentationLimit)
	            {broker.defragXMLResource(transaction, next);}
	        broker.checkXMLResourceConsistency(next);
	    }
	}
	
	/**
	 * Fires the prepare function for the UPDATE_DOCUMENT_EVENT trigger for the Document doc
	 *  
	 * @param transaction	The transaction
	 * @param doc	The document to trigger for
	 * 
	 * @throws TriggerException 
	 */
	private void prepareTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {
            
	    final Collection col = doc.getCollection();
	        
            final DocumentTrigger trigger = new DocumentTriggers(broker, col);
            
            trigger.beforeUpdateDocument(broker, transaction, doc);
            triggers.put(doc.getDocId(), trigger);
	}
	
	/** 
	 * Fires the finish function for UPDATE_DOCUMENT_EVENT for the documents trigger
	 * 
	 * @param transaction	The transaction
	 * @param doc	The document to trigger for
	 * 
	 * @throws TriggerException 
	 */
	private void finishTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {
        final DocumentTrigger trigger = triggers.get(doc.getDocId());
        if(trigger != null)
            {trigger.afterUpdateDocument(broker, transaction, doc);}
	}
	
	public String toString() {
		final StringBuilder buf = new StringBuilder();
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

	final static class NodeComparator implements Comparator<StoredNode> {

		/* (non-Javadoc)
		* @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		*/
		public int compare(StoredNode n1, StoredNode n2) {
			if (n1.getInternalAddress() == n2.getInternalAddress())
				{return Constants.EQUAL;}
			if (n1.getInternalAddress() < n2.getInternalAddress())
				{return Constants.INFERIOR;}
			else
				{return Constants.SUPERIOR;}
		}
	}
}
