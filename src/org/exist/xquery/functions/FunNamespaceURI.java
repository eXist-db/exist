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
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * xpath-library function: namespace-uri()
 *
 */
public class FunNamespaceURI extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("namespace-uri", Function.BUILTIN_FUNCTION_NS),
				"Returns the namespace URI of the xs:QName of the context item. " +
				"If the context item is in no namespace or is neither an element nor attribute node, " +
				"returns the xs:anyURI eqvivalent to the zero-length string." +
				" Raises an error if the context item is undefined or not a node.",
				new SequenceType[0],
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
				false),
		new FunctionSignature(
			new QName("namespace-uri", Function.BUILTIN_FUNCTION_NS),
			"Returns the namespace URI of the xs:QName value of $a" +
			"If $a is in no namespace or is neither an element nor attribute node, " +
				"returns the xs:anyURI eqvivalent to the zero-length string." +
				" Raises an error if the context item is undefined or not a node.",
			new SequenceType[] { 
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE) 
			},
			new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
			false)
	};

	public FunNamespaceURI(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
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
        
        Item item = null;
        // check if the node is passed as an argument or should be taken from
        // the context sequence
        if(getArgumentCount() > 0) {
            Sequence seq = getArgument(0).eval(contextSequence);
            if(!seq.isEmpty())
                item = seq.itemAt(0);
        } else { 
        	if (contextItem == null)
            	throw new XPathException(getASTNode(), "XPDY0002: Undefined context item");
        	item = contextItem;
        	//if (contextSequence == null)
        	//	throw new XPathException(getASTNode(), "XPDY0002: Undefined context item");
        	//Doh !
            //if(!contextSequence.isEmpty())
            //	throw new XPathException(getASTNode(), "XPDY0002: Undefined context item");
            //item = contextSequence.itemAt(0);
        }
        
        Sequence result;
        if(item == null)
            result = AnyURIValue.EMPTY_URI;
        else {        	
            if(!Type.subTypeOf(item.getType(), Type.NODE))
                throw new XPathException(getASTNode(), "XPDY0004: Context item is not a node; got: " +
                        Type.getTypeName(item.getType()));
            //TODO : how to improve performance ?
            Node n = ((NodeValue)item).getNode();
            result = new AnyURIValue(n.getNamespaceURI());
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}
}
