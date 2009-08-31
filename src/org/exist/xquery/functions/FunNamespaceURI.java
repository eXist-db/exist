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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
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

	protected static final String FUNCTION_DESCRIPTION_0_PARAM =
		"Returns the namespace URI of the xs:QName of the context item.\n\n";
	protected static final String FUNCTION_DESCRIPTION_1_PARAM =
		"Returns the namespace URI of the xs:QName of $arg.\n\n" +
		"If the argument is omitted, it defaults to the context node (.). ";

	protected static final String FUNCTION_DESCRIPTION_COMMON =
		"The behavior of the function if the argument is omitted is exactly " + 
		"the same as if the context item had been passed as the argument.\n\n" +

		"The following errors may be raised: if the context item is undefined " +
		"[err:XPDY0002]XP; if the context item is not a node [err:XPTY0004]XP.\n\n" +

		"If $arg is neither an element nor an attribute node, or if it is an " +
		"element or attribute node whose expanded-QName (as determined by the " +
		"dm:node-name accessor in the Section 5.11 node-name AccessorDM) is " +
		"in no namespace, then the function returns the xs:anyURI " +
		"corresponding to the zero-length string.";

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("namespace-uri", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_0_PARAM + FUNCTION_DESCRIPTION_COMMON,
			new SequenceType[0],
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the namespace URI"),
			false),
		new FunctionSignature(
			new QName("namespace-uri", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON,
			new SequenceType[] { 
				new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The input node") 
			},
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the namespace URI"),
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
        // check if the node is passed as an argument or should be taken from the context sequence
        if(getArgumentCount() > 0) {
            Sequence seq = getArgument(0).eval(contextSequence, contextItem);
            if(!seq.isEmpty())
                item = seq.itemAt(0);
        } else { 
        	if (contextItem == null)
            	throw new XPathException(this, "XPDY0002: Undefined context item");
        	item = contextItem;
        }
        
        Sequence result;
        if(item == null)
            result = AnyURIValue.EMPTY_URI;
        else {        	
            if(!Type.subTypeOf(item.getType(), Type.NODE))
                throw new XPathException(this, "XPDY0004: Context item is not a node; got: " +
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
