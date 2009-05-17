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

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.storage.UpdateListener;
import org.exist.util.LockException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.Iterator;

/**
 * Reads a set of document root nodes from the context. Used for
 * absolute path expression that do not start with fn:doc() or fn:collection().
 * 
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class RootNode extends Step {

    private NodeSet cached = null;

    private DocumentSet cachedDocs = null;

    private UpdateListener listener = null;
    
    /** Constructor for the RootNode object */
    public RootNode(XQueryContext context) {
        super(context, Constants.SELF_AXIS);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        // get statically known documents from the context
        DocumentSet ds = context.getStaticallyKnownDocuments();
        if (ds == null || ds.getDocumentCount() == 0) return Sequence.EMPTY_SEQUENCE;
        
//        // if the expression occurs in a nested context, we might have cached the
//        // document set
//        // TODO: disabled cache for now as it may cause concurrency issues
//        // better use compile-time inspection and maybe a pragma to mark those
//        // sections in the query that can be safely cached
//        if (cachedDocs != null && cachedDocs.equalDocs(ds)) return cached;
        
        // check if the loaded documents should remain locked
        NodeSet result = new NewArrayNodeSet(2);
        try {
            // wait for pending updates
            if (!context.inProtectedMode())
                ds.lock(context.getBroker(), false, true);
	        DocumentImpl doc;
	        for (Iterator i = ds.getDocumentIterator(); i.hasNext();) {
	            doc = (DocumentImpl) i.next();
                if (context.inProtectedMode() && !context.getProtectedDocs().containsKey(doc.getDocId()))
                    continue;
                if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
	            	result.add(new NodeProxy(doc));
	            }
            }
	        cached = result;
	        cachedDocs = ds;
        } catch (LockException e) {
            throw new XPathException(this, "Failed to acquire lock on the context document set");
        } finally {
            // release all locks
            if (!context.inProtectedMode())
                ds.unlock(false);
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
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
                public void documentUpdated(DocumentImpl document, int event) {
                    // clear all
                    cachedDocs = null;
                    cached = null;
                }

                public void unsubscribe() {
                    RootNode.this.listener = null;
                }

                public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
                    // not relevant
                }

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
        cached = null;
        cachedDocs = null;
    }
}
