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

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.xquery.*;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the built-in fn:doc() function.
 * 
 * This will be replaced by XQuery's fn:doc() function.
 * 
 * @author wolf
 */
public class FunDoc extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("doc", Function.BUILTIN_FUNCTION_NS),
			"Returns the documents specified in the input sequence. " +  
            "The arguments are either document paths like '" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml' or " +
			"XMLDB URIs like 'xmldb:exist://localhost:8081/" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml' or " +  
            "standard URLs starting with http://, file://, etc.",
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));
	
	private Sequence cached = null;
	private String cachedPath = null;
	private UpdateListener listener = null;
	
	/**
	 * @param context
	 */
	public FunDoc(XQueryContext context) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xquery.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}

	/**
	 * @see org.exist.xquery.Expression#eval(Sequence, Item)
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
		
        Sequence result;
		Sequence arg = getArgument(0).eval(contextSequence, contextItem);
		if (arg.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		String path = arg.itemAt(0).getStringValue();
    		// check if we can return a cached sequence
    		if (cached != null && path.equals(cachedPath)) {
    			return cached;
    		}
    		
    		try {
    			result = DocUtils.getDocument(this.context, path);
    			if (result.isEmpty() && context.isRaiseErrorOnFailedRetrieval()) {
    				throw new XPathException("FODC0002: can not access '" + path + "'");
    			}
    //			TODO: we still need a final decision about this. Also check base-uri.
    //			if (result == Sequence.EMPTY_SEQUENCE)
    //				throw new XPathException(getASTNode(), path + " is not an XML document");
    			DocumentSet docs = result.getDocumentSet();
    			if (docs != null && DocumentSet.EMPTY_DOCUMENT_SET != docs) {
    				// only cache node sets (which have a non-empty document set)
    				cachedPath = path;
    				cached = result;
    				registerUpdateListener();
    			}
    		}
    		catch (Exception e) {
    			throw new XPathException(getASTNode(), e.getMessage());			
    		}
        }
            
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;            
		
	}

	protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {
                public void documentUpdated(DocumentImpl document, int event) {
                    if (document == null || event == UpdateListener.ADD || event == UpdateListener.REMOVE) {
                        // clear all
                        cachedPath = null;
                        cached = null;
                    } else {
                        if (cachedPath != null && (document == null || cachedPath.equals(document.getURI()))) {
                            cached = null;
                            cachedPath = null;
                        }
                    }
                }

                public void unsubscribe() {
                    FunDoc.this.listener = null;
                }

                public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
                    // not relevant
                }

                public void debug() {
                	LOG.debug("UpdateListener: Line: " + getASTNode().getLine() + ": " + FunDoc.this.toString());                	
                }
            };
            context.registerUpdateListener(listener);
        }
	}
	
	/**
	 * @see org.exist.xquery.Expression#resetState(boolean)
     * @param postOptimization
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);

        if (!postOptimization) {
            cached = null;
            cachedPath = null;
            listener = null;
        }

        getArgument(0).resetState(postOptimization);
	}
}
