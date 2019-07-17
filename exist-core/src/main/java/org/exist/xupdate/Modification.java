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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.DocumentTriggers;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.StoredNode;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;

/**
 * Base class for all XUpdate modifications.
 * 
 * @author Wolfgang Meier
 */
public abstract class Modification {

	protected final static Logger LOG = LogManager.getLogger(Modification.class);

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
	protected ManagedLocks<ManagedDocumentLock> lockedDocumentsLocks = null;
	protected MutableDocumentSet modifiedDocuments = new DefaultDocumentSet();
    protected Int2ObjectMap<DocumentTrigger> triggers;

	@SuppressWarnings("unused")
	private Modification() {}

	/**
	 * Constructor for Modification.
	 *
	 * @param broker the database broker
	 * @param docs the document set
	 * @param selectStmt the select statement
	 * @param namespaces the namespace bindings
	 * @param variables the variable bindings
	 */
	public Modification(DBBroker broker, DocumentSet docs, String selectStmt,
	        Map<String, String> namespaces, Map<String, Object> variables) {
		this.selectStmt = selectStmt;
		this.broker = broker;
		this.docs = docs;
		this.namespaces = new HashMap<String, String>(namespaces);
		this.variables = new TreeMap<String, Object>(variables);
        this.triggers = new Int2ObjectOpenHashMap<>();
        // DESIGN_QUESTION : wouldn't that be nice to apply selectStmt right here ?
	}

	/**
     * Process the modification. This is the main method that has to be implemented 
     * by all subclasses.
     * 
     * @param transaction the database transaction
	 * @return long the number of updates processed
	 *
     * @throws PermissionDeniedException if the caller has insufficient priviledges
     * @throws LockException if a lock error occurs
     * @throws EXistException if the database raises an error
     * @throws XPathException if the XPath raises an error
	 * @throws TriggerException if a trigger raises an error
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
	 * @param docs the documents to evaludate the expression over
	 *
	 * @return The selected nodes.
	 *
	 * @throws PermissionDeniedException if the caller has insufficient priviledges
	 * @throws EXistException if the database raises an error
	 * @throws XPathException if the XPath raises an error
	 */
	protected NodeList select(DocumentSet docs)
		throws PermissionDeniedException, EXistException, XPathException {
		final XQuery xquery = broker.getBrokerPool().getXQueryService();
		final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
		final Source source = new StringSource(selectStmt);
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
		XQueryContext context;
		if(compiled == null) {
			context = new XQueryContext(broker.getBrokerPool());
		} else {
			context = compiled.getContext();
			context.prepareForReuse();
		}
		context.setStaticallyKnownDocuments(docs);
		declareNamespaces(context);
		declareVariables(context);
		if(compiled == null)
			try {
				compiled = xquery.compile(broker, context, source);
			} catch (final IOException e) {
				throw new EXistException("An exception occurred while compiling the query: " + e.getMessage());
			}
		
		Sequence resultSeq = null;
		try {
			resultSeq = xquery.execute(broker, compiled, null);
		} finally {
			context.runCleanupTasks();
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
	 * @param context the xquery context
	 * @throws XPathException if an error occurs whilst declaring the variables
	 */
	protected void declareVariables(XQueryContext context) throws XPathException {
		for (final Iterator<Map.Entry<String, Object>> i = variables.entrySet().iterator(); i.hasNext(); ) {
			final Map.Entry<String, Object> entry = (Map.Entry<String, Object>) i.next();
			context.declareVariable(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * @param context the xquery context
	 * @throws XPathException if an error occurs whilst declaring the namespaces
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
	 * @param transaction the database transaction.
	 * 
	 * @return The selected document nodes.
	 *
	 * @throws LockException if a lock error occurs
	 * @throws PermissionDeniedException if the caller has insufficient priviledges
	 * @throws EXistException if the database raises an error
	 * @throws XPathException if the XPath raises an error
	 * @throws TriggerException if a trigger raises an error
	 */
	protected final StoredNode[] selectAndLock(Txn transaction)
			throws LockException, PermissionDeniedException, EXistException,
			XPathException, TriggerException {
		final java.util.concurrent.locks.Lock globalLock = broker.getBrokerPool().getGlobalUpdateLock();
		globalLock.lock();
	    try {
	        final NodeList nl = select(docs);
	        final DocumentSet lockedDocuments = ((NodeSet)nl).getDocumentSet();
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
	        lockedDocumentsLocks = lockedDocuments.lock(broker, true);
	        
		    final StoredNode ql[] = new StoredNode[nl.getLength()];		    
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (StoredNode)nl.item(i);
				final DocumentImpl doc = ql[i].getOwnerDocument();
				
				// call the eventual triggers
				// TODO -jmv separate loop on docs and not on nodes
			
				//prepare Trigger
				prepareTrigger(transaction, doc);
			}
			return ql;
	    } finally {
	        globalLock.unlock();
	    }
	}

	/**
	 * Release all acquired document locks;
	 * feature trigger_update :
	 * at the same time we leverage on the fact that it's called after 
	 * database modification to call the eventual triggers
	 *
	 * @param transaction the database transaction.
	 *
	 * @throws TriggerException if a trigger raises an error
	 */
	protected final void unlockDocuments(final Txn transaction) throws TriggerException
	{
		if(lockedDocumentsLocks == null) {
			return;
		}

		try {
			//finish Trigger
			final Iterator<DocumentImpl> iterator = modifiedDocuments.getDocumentIterator();
			while (iterator.hasNext()) {
				finishTrigger(transaction, iterator.next());
			}
		} finally {
			triggers.clear();
			modifiedDocuments.clear();

			//unlock documents
	        lockedDocumentsLocks.close();
	        lockedDocumentsLocks = null;
		}
	}
	
	/**
	 * Check if any of the modified documents needs defragmentation.
	 * 
	 * Defragmentation will take place if the number of split pages in the
	 * document exceeds the limit defined in the configuration file.
	 *
	 * @param transaction the database transaction.
	 * @param docs the documents
	 *
	 * @throws EXistException if an error occurs
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
	 * @param transaction The database transaction
	 * @param doc The document to trigger for
	 *
	 * @throws TriggerException if a trigger raises an error
	 */
	private void prepareTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {
            
	    final Collection col = doc.getCollection();
	        
            final DocumentTrigger trigger = new DocumentTriggers(broker, transaction, col);
            
            trigger.beforeUpdateDocument(broker, transaction, doc);
            triggers.put(doc.getDocId(), trigger);
	}
	
	/** 
	 * Fires the finish function for UPDATE_DOCUMENT_EVENT for the documents trigger
	 * 
	 * @param transaction The transaction
	 * @param doc The document to trigger for
	 *
	 * @throws TriggerException if a trigger raises an error
	 */
	private void finishTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {
        final DocumentTrigger trigger = triggers.get(doc.getDocId());
        if(trigger != null)
            {trigger.afterUpdateDocument(broker, transaction, doc);}
	}
	
	public String toString() {
		//		buf.append(XMLUtil.dump(content));
		return "<xu:" + getName() + " select=\"" + selectStmt + "\">" + "</xu:" +	getName() +	">";
	}

	/**
	 * Get's the parent of the node.
	 *
	 * @param node The node of which to retrieve the parent.
	 *
	 * @return the parent node, or null if not available
	 */
	protected @Nullable Node getParent(@Nullable final Node node) {
		if (node == null) {
			return null;
		} else if (node instanceof Attr) {
			return ((Attr) node).getOwnerElement();
		} else {
			return node.getParentNode();
		}
	}
}
