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
package org.exist.xquery.functions;

import org.exist.collections.Collection;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author wolf
 */
public class ExtCollection extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection", Function.BUILTIN_FUNCTION_NS),
            "Returns the documents contained in the collections " +
            "specified in the input sequence. " +           
            "The arguments are either collection pathes like '" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays' or " +
			"XMLDB URIs like 'xmldb:exist://localhost:8081/" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays'. " +
			"Documents contained in subcollections are also included.",
			new SequenceType[] {
				//Different from the offical specs
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true);

	private boolean includeSubCollections = false;
	
	private List cachedArgs = null;

	private Sequence cached = null;
	private DocumentSet cachedDocs = null;
	private UpdateListener listener = null;
	
	/**
	 * @param context
	 */
	public ExtCollection(XQueryContext context) {
		this(context, signature, true);
	}

	public ExtCollection(XQueryContext context, FunctionSignature signature, boolean inclusive) {
		super(context, signature);
		includeSubCollections = inclusive;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }       
        
	    List args = getParameterValues(contextSequence, contextItem);
		boolean cacheIsValid = false;
		if(cachedArgs != null)
		    cacheIsValid = compareArguments(cachedArgs, args);
		if(cacheIsValid) {
		    // if the expression occurs in a nested context, we might have cached the
            // document set
            if (context.getProfiler().isEnabled())
                context.getProfiler().end(this, "fn:collection: loading documents", cached);                 
		    return cached;
        }
        
		// build the document set
        DocumentSet docs = null;
//        DocumentSet docs = new DocumentSet(521);
	    try {
   			if (args.size() == 0) {
	    		//TODO : add default collection to the context
    			//If the value of the default collection is undefined an error is raised [err:FODC0002].
    			//throw new XPathException("FODC0002: unknown collection '" + uri + "'");	
    			docs = context.getStaticallyKnownDocuments();
		    } else {
                MutableDocumentSet ndocs = new DefaultDocumentSet();
                for (int i = 0; i < args.size(); i++) {
					String next = (String)args.get(i);
					XmldbURI uri = new AnyURIValue(next).toXmldbURI();
				    Collection coll = context.getBroker().getCollection(uri);            
				    if(coll == null) {
				    	if (context.isRaiseErrorOnFailedRetrieval()) {
		    				throw new XPathException("FODC0002: can not access collection '" + uri + "'");
		    			}					    	
				    } else {
	                    if (context.inProtectedMode())
	                        context.getProtectedDocs().getDocsByCollection(coll, includeSubCollections, ndocs);
	                    else
	                        coll.allDocs(context.getBroker(), ndocs, includeSubCollections, true, context.getProtectedDocs());
	                }
	            }
                docs = ndocs;
            }
        } catch (XPathException e) { //From AnyURIValue constructor
        	e.setASTNode(getASTNode());
            throw new XPathException("FODC0002: " + e.getMessage(), e);
        }
        // iterate through all docs and create the node set
		NodeSet result = new ExtArrayNodeSet(docs.getDocumentCount(), 1);
		Lock dlock;
		DocumentImpl doc;
		for (Iterator i = docs.getDocumentIterator(); i.hasNext();) {
		    doc = (DocumentImpl)i.next();
		    dlock = doc.getUpdateLock();
            boolean lockAcquired = false;
            try {
                if (!context.inProtectedMode() && !dlock.hasLock()) {
                    dlock.acquire(Lock.READ_LOCK);
                    doc.setBroker(context.getBroker());
                    lockAcquired = true;
                }
                result.add(new NodeProxy(doc)); // , -1, Node.DOCUMENT_NODE));
		    } catch (LockException e) {
                LOG.info("Could not acquire lock on document " + doc.getURI());
            } finally {
                if (lockAcquired)
                    dlock.release(Lock.READ_LOCK);
		    }
		}
		cached = result;
		cachedArgs = args;
        cachedDocs = docs;
        registerUpdateListener();
        
        if (context.getProfiler().isEnabled())           
               context.getProfiler().end(this, "", result);
       
		return result;
	}
	
	
    /**
     * @param contextSequence
     * @param contextItem
     * @throws XPathException
     */
    private List getParameterValues(Sequence contextSequence, Item contextItem) throws XPathException {
        List args = new ArrayList(getArgumentCount() + 10);
	    for(int i = 0; i < getArgumentCount(); i++) {
	        Sequence seq =
				getArgument(i).eval(contextSequence, contextItem);
			for (SequenceIterator j = seq.iterate(); j.hasNext();) {
				Item next = j.nextItem();
				args.add(next.getStringValue());
			}
	    }
	    return args;
    }

    private boolean compareArguments(List args1, List args2) {
        if(args1.size() != args2.size())
            return false;
        for(int i = 0; i < args1.size(); i++) {
            String arg1 = (String)args1.get(i);
            String arg2 = (String)args2.get(i);
            if(!arg1.equals(arg2))
                return false;
        }
        return true;
    }
    
    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {
                public void documentUpdated(DocumentImpl document, int event) {
                    if (document == null || event == UpdateListener.ADD || event == UpdateListener.REMOVE) {
                        // clear all
                        cachedDocs = null;
                        cached = null;
                        cachedArgs = null;
                    } else {
                        if (cachedDocs != null
                                && cachedDocs.contains(document.getDocId())) {
                            cachedDocs = null;
                            cached = null;
                            cachedArgs = null;
                        }
                    }
                }


                public void unsubscribe() {
                    ExtCollection.this.listener = null;
                }

                public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
                    // not relevant
                }

                public void debug() {
                	LOG.debug("UpdateListener: Line: " + getASTNode().getLine() + ": " + ExtCollection.this.toString());                	
                }
            };
            context.registerUpdateListener(listener);
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#resetState()
     */
    public void resetState(boolean postOptimization) {
        cached = null;
        cachedDocs = null;
        cachedArgs = null;
    }
}