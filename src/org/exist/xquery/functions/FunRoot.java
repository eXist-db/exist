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

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunRoot extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("root", Function.BUILTIN_FUNCTION_NS),
			"Returns the root of the tree to which the context node belongs. This will usually, "
				+ "but not necessarily, be a document node.",
			new SequenceType[0],
			new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
			new QName("root", Function.BUILTIN_FUNCTION_NS),
			"Returns the root of the tree to which $arg belongs. This will usually, "
				+ "but not necessarily, be a document node.",
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
		)
	};

	/**
	 * @param context
	 * @param signature
	 */
	public FunRoot(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
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
        
        Sequence result;
		Item item = contextItem;
		if (getSignature().getArgumentCount() > 0) {
			Sequence seq = getArgument(0).eval(contextSequence, contextItem);
			if (seq.getLength() == 0)
                item = null;
            else
                item = seq.itemAt(0);
		}
        
        if (item == null)
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		if (!Type.subTypeOf(item.getType(), Type.NODE))
    			throw new XPathException("Context item is not a node; got " + Type.getTypeName(item.getType()));
    		if (item instanceof NodeProxy) {
                NodeProxy p = ((NodeProxy) item);
                org.exist.dom.DocumentImpl doc = p.getDocument();
    		    //Filter out the temporary nodes wrapper element 
                if (doc.getCollection().isTempCollection()) {
                    //TODO check that we only have one child
                    StoredNode trueRoot = (StoredNode)doc.getDocumentElement().getFirstChild();
                    result = new NodeProxy(doc, trueRoot.getNodeId(), trueRoot.getInternalAddress());
                } else                
                    result = new NodeProxy(((NodeProxy) item).getDocument());
    		} else
    		    result = (DocumentImpl) ((NodeImpl) item).getOwnerDocument();
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result; 
        
	}

}
