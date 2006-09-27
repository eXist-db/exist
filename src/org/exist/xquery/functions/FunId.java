/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
 *  
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */

package org.exist.xquery.functions;

import java.util.Iterator;
import java.util.StringTokenizer;

import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetHelper;
import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.util.XMLChar;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

public class FunId extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("id", Function.BUILTIN_FUNCTION_NS),
				"Returns the sequence of element nodes that have an ID value " +
				"matching the value of one or more of the IDREF values supplied in $a. " +
				"If none is matching or $ is the empty sequence, returns the empty sequence.",
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)},
				new SequenceType(Type.ELEMENT, Cardinality.ZERO_OR_MORE));
				
	/**
	 * Constructor for FunId.
	 */
	public FunId(XQueryContext context) {
		super(context, signature);
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
        
        if (getArgumentCount() < 1)
			throw new XPathException("function id requires one argument");
		
        if(contextItem != null)
			contextSequence = contextItem.toSequence();
		
        Sequence result;
        Expression arg = getArgument(0);        
		Sequence idval = arg.eval(contextSequence);
		if(idval.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		result = new ExtArrayNodeSet();
    		String nextId;
    		DocumentSet docs;
    		if(contextSequence == null || !(contextSequence instanceof NodeSet))
    			docs = context.getStaticallyKnownDocuments();
    		else
    			docs = contextSequence.toNodeSet().getDocumentSet(); 
    		for(SequenceIterator i = idval.iterate(); i.hasNext(); ) {
    			nextId = i.nextItem().getStringValue();
    			if(nextId.indexOf(" ") != Constants.STRING_NOT_FOUND) {
    				// parse idrefs
    				StringTokenizer tok = new StringTokenizer(nextId, " ");
    				while(tok.hasMoreTokens()) {
    					nextId = tok.nextToken();
    					if(!XMLChar.isValidNCName(nextId))
    						throw new XPathException(nextId + " is not a valid NCName");
    					QName id = new QName(nextId, "", null);
    					getId((NodeSet)result, docs, id);
    				}
    			} else {
    				if(!XMLChar.isValidNCName(nextId))
    					throw new XPathException(nextId + " is not a valid NCName");
    				QName id = new QName(nextId, "", null);
    				getId((NodeSet)result, docs, id);
    			}
    		}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;   
        
	}

	private void getId(NodeSet result, DocumentSet docs, QName id) {
		NodeSet attribs = (NodeSet) context.getBroker().getElementIndex().findElementsByTagName(
                ElementValue.ATTRIBUTE_ID, docs, id, null);
		NodeProxy n, p;
		for (Iterator i = attribs.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			p = new NodeProxy(n.getDocument(), n.getNodeId().getParentId(), Node.ELEMENT_NODE);
			result.add(p);
		}
	}
}
