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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.Trigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.*;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.util.Iterator;

/**
 * @author wolf
 *
 */
public abstract class Modification extends AbstractExpression
{

	protected final static Logger LOG =
		Logger.getLogger(Modification.class);
	
	protected final Expression select;
	protected final Expression value;
	
	protected DocumentSet lockedDocuments = null;
	protected DocumentSet modifiedDocuments = new DocumentSet();
    protected Int2ObjectHashMap triggers;
    
    /**
	 * @param context
	 */
	public Modification(XQueryContext context, Expression select, Expression value) {
		super(context);
		this.select = select;
		this.value = value;
        this.triggers = new Int2ObjectHashMap(97);
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
	 * @param nodes
	 * @return
	 * @throws LockException
	 */
	protected StoredNode[] selectAndLock(Txn transaction, NodeSet nodes) throws LockException, PermissionDeniedException,
		XPathException {
	    Lock globalLock = context.getBroker().getBrokerPool().getGlobalUpdateLock();
	    try {
	        globalLock.acquire(Lock.READ_LOCK);
	       
	        lockedDocuments = nodes.getDocumentSet();
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
	        lockedDocuments.lock(true, false);
	        
		    StoredNode ql[] = new StoredNode[nodes.getLength()];
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (StoredNode)nodes.item(i);
				DocumentImpl doc = (DocumentImpl)ql[i].getOwnerDocument();
				doc.setBroker(context.getBroker());
				//prepare Trigger
				prepareTrigger(transaction, doc);
			}
			return ql;
	    } finally {
	        globalLock.release(Lock.READ_LOCK);
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
						serializer.toReceiver(p, false, false);
                        if (p.getNodeType() == Node.ATTRIBUTE_NODE)
                            item = builder.getDocument().getLastAttr();
                        else
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

    protected void finishTriggers(Txn transaction) {
        Iterator iterator = modifiedDocuments.iterator();
		DocumentImpl doc;
		while(iterator.hasNext())
		{
			doc = (DocumentImpl) iterator.next();
			finishTrigger(transaction, doc);
		}
        triggers.clear();
    }

    /**
	 * Release all acquired document locks.
	 */
	protected void unlockDocuments()
	{
	    if(lockedDocuments == null)
	        return;
	    
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
		DBBroker broker = context.getBroker();
        int fragmentationLimit = -1;
        if (broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR) != null)
        	fragmentationLimit = ((Integer)broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR)).intValue();	
	    for(Iterator i = docs.iterator(); i.hasNext(); ) {
	        DocumentImpl next = (DocumentImpl) i.next();
	        if(next.getMetadata().getSplitCount() > fragmentationLimit)
	            broker.defragXMLResource(transaction, next);
	        broker.checkXMLResourceConsistency(next);
	    }
	}
	
	/**
	 *  Fires the prepare function for the UPDATE_DOCUMENT_EVENT trigger for the Document doc
	 *  
	 *  @param transaction	The transaction
	 *  @param doc	The document to trigger for
	 */
	private void prepareTrigger(Txn transaction, DocumentImpl doc)
	{
		//if we are doing a batch update then only call prepare for the first update to that document
		if(context.hasBatchTransaction())
		{
			Iterator itTrigDoc = modifiedDocuments.iterator();
	    	while(itTrigDoc.hasNext())
	    	{
	    		DocumentImpl trigDoc = (DocumentImpl)itTrigDoc.next();
	    		if(trigDoc.getURI().equals(doc.getURI()))
	    		{
	    			return;
	    		}
	    	}
		}

		//prepare the trigger
		CollectionConfiguration config = doc.getCollection().getConfiguration(doc.getBroker());
        DocumentTrigger trigger = null;
        if(config != null)
        {
        	//get the UPDATE_DOCUMENT_EVENT trigger
            try {
                trigger = (DocumentTrigger)config.newTrigger(Trigger.UPDATE_DOCUMENT_EVENT, doc.getBroker(), doc.getCollection());
            } catch (CollectionConfigurationException e) {
                LOG.debug("An error occurred while initializing a trigger for collection " + doc.getCollection().getURI() + ": " + e.getMessage(), e);
            }

            if(trigger != null)
        	{
	            try
	            {
	            	//fire trigger prepare
	            	trigger.prepare(Trigger.UPDATE_DOCUMENT_EVENT, doc.getBroker(), transaction, doc.getURI(), doc);
	            }
	            catch(TriggerException te)
	            {
	            	LOG.debug("Unable to prepare trigger for event UPDATE_DOCUMENT_EVENT: " + te.getMessage());
	            }
	            catch(Exception e)
        		{
        			LOG.debug("Trigger event UPDATE_DOCUMENT_EVENT for collection: " + doc.getCollection().getURI() + " with: " + doc.getURI() + " " + e.getMessage());
        		}
                triggers.put(doc.getDocId(), trigger);
            }
        }
	}
	
	/** Fires the finish function for UPDATE_DOCUMENT_EVENT for the documents trigger
	 * 
	 * @param transaction	The transaction
	 * @param doc	The document to trigger for
	 */
	private void finishTrigger(Txn transaction, DocumentImpl doc)
	{
		//if this is part of a batch transaction, defer the trigger
		if(context.hasBatchTransaction())
		{
			context.setBatchTransactionTrigger(doc);
		}
		else
		{
			//finish the trigger
            DocumentTrigger trigger = (DocumentTrigger) triggers.get(doc.getDocId());
            if(trigger != null)
            {
                try
                {
                    trigger.finish(Trigger.UPDATE_DOCUMENT_EVENT, doc.getBroker(), transaction, doc);
                }
                catch(Exception e)
                {
                    LOG.debug("Trigger event UPDATE_DOCUMENT_EVENT for collection: " + doc.getCollection().getURI() + " with: " + doc.getURI() + " " + e.getMessage());
                }
            }
		}
	}
	
	/**
	 * Gets the Transaction to use for the update (can be batch or individual)
	 * 
	 * @return The transaction
	 */
	public Txn getTransaction()
	{
		Txn transaction = context.getBatchTransaction();
		
		//if there is no batch update transaction, start a new individual transaction
		if(transaction == null)
		{
			TransactionManager txnMgr = context.getBroker().getBrokerPool().getTransactionManager();
			transaction = txnMgr.beginTransaction();
		}
		
		return transaction;
	}
	
	/**
	 * Commit's the transaction for the update unless it is a batch update and then the commit is defered
	 * 
	 * @param transaction The Transaction to commit
	 */
	public void commitTransaction(Txn transaction) throws TransactionException
	{
		//only commit the transaction, if its not a batch update transaction
		if(!context.hasBatchTransaction())
		{
			TransactionManager txnMgr = context.getBroker().getBrokerPool().getTransactionManager();
			txnMgr.commit(transaction);
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
