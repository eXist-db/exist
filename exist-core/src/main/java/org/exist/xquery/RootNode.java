/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04 The eXist Team
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import org.exist.collections.ManagedLocks;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.Iterator;

/**
 * Reads a set of document root nodes from the context. Used for
 * absolute path expression that do not start with fn:doc() or fn:collection().
 * 
 * @author <a href="mailto:meier@ifs.tu-darmstadt.de">Wolfgang Meier</a>
 */
public class RootNode extends Step {

    @SuppressWarnings("unused")
	private NodeSet cached = null;

    @SuppressWarnings("unused")
	private DocumentSet cachedDocs = null;

    private UpdateListener listener = null;

    public RootNode(XQueryContext context) {
        super(context, Constants.SELF_AXIS);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }

        // first check if a context item is declared
        final ContextItemDeclaration decl = context.getContextItemDeclartion();
        if (decl != null) {
            final Sequence seq = decl.eval(null, null);
            if (!seq.isEmpty()) {
                final Item item = seq.itemAt(0);
                // context item must be a node
                if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                    throw new XPathException(this, ErrorCodes.XPTY0020, "Context item is not a node");
                }
                final NodeValue node = (NodeValue)item;
                // return fn:root(self::node()) treat as document-node()
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    return new NodeProxy(((NodeProxy)item).getOwnerDocument());
                } else {
                    if (node.getType() == Type.DOCUMENT) {
                        return node;
                    }
                    return (org.exist.dom.memtree.DocumentImpl) node.getOwnerDocument();
                }
            }
            return Sequence.EMPTY_SEQUENCE;
        }

        // get statically known documents from the context
        DocumentSet ds = context.getStaticallyKnownDocuments();
        if (ds == null || ds.getDocumentCount() == 0) {return Sequence.EMPTY_SEQUENCE;}
        
//        // if the expression occurs in a nested context, we might have cached the
//        // document set
//        // TODO: disabled cache for now as it may cause concurrency issues
//        // better use compile-time inspection and maybe a pragma to mark those
//        // sections in the query that can be safely cached
//        if (cachedDocs != null && cachedDocs.equalDocs(ds)) return cached;
        
        // check if the loaded documents should remain locked
        NewArrayNodeSet result = new NewArrayNodeSet();

        // NOTE(AR) locking the documents here does not actually do anything useful, eXist-db will still exhibit weak isolation with concurrent updates
//        ManagedLocks<ManagedDocumentLock> docLocks = null;
//        try {
//            // wait for pending updates
//            if (!context.inProtectedMode()) {
//                docLocks = ds.lock(context.getBroker(), false);
//            }

	        DocumentImpl doc;
	        for (final Iterator<DocumentImpl> i = ds.getDocumentIterator(); i.hasNext();) {
	            doc = i.next();
                if (context.inProtectedMode() && !context.getProtectedDocs().containsKey(doc.getDocId()))
                    {continue;}
                if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
	            	result.add(new NodeProxy(doc));
	            }
            }
	        cached = result;
	        cachedDocs = ds;

        // NOTE(AR) see comment above regards locking the documents
//        } catch (final LockException e) {
//            throw new XPathException(this, "Failed to acquire lock on the context document set");
//        } finally {
//            // release all locks
//            if (!context.inProtectedMode() && docLocks != null) {
//                docLocks.close();
//            }
//        }

//        result.updateNoSort();
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
        registerUpdateListener();
        
        //actualReturnType = result.getItemType();
        
        return result;        
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Step#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        //TODO : find a better message
        dumper.display("[root-node]");
    }
    
    public String toString() {
        //TODO : find a better message
        return "[root-node]";
    }    
    
    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Step#returnsType()
     */
    public int returnsType() {
        return Type.NODE;
    }

    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {
                @Override
                public void documentUpdated(DocumentImpl document, int event) {
                    // clear all
                    cachedDocs = null;
                    cached = null;
                }

                @Override
                public void unsubscribe() {
                    RootNode.this.listener = null;
                }

                @Override
                public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {
                    // not relevant
                }

                @Override
                public void debug() {
                    LOG.debug("UpdateListener: Line: " + RootNode.this.toString());                	
                }
            };
            context.registerUpdateListener(listener);
        }
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Step#resetState()
     */
    public void resetState(boolean postOptimization) {
    	super.resetState(postOptimization);
        cached = null;
        cachedDocs = null;
    }
}
