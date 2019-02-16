/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2007 The eXist team
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

package org.exist.xquery.functions.fn;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Built-in function fn:nilled().
 * 
 * @author wolf
 */
public class FunNilled extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("nilled", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:boolean indicating whether the argument node is \"nilled\". " +
			"If the argument is not an element node, returns the empty sequence. " +
			"If the argument is the empty sequence, returns the empty sequence.",
			new SequenceType[] { new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The input node") },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true if the argument node is \"nilled\""));

	public FunNilled(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }

		Sequence result;
		if (args[0].isEmpty()) {
			result = Sequence.EMPTY_SEQUENCE;
		} else {
			final Item arg = args[0].itemAt(0);
			if (!Type.subTypeOf(arg.getType(), Type.ELEMENT)) {
				result = Sequence.EMPTY_SEQUENCE;
			} else {
				final Node n = ((NodeValue) arg).getNode();
				if (n.hasAttributes()) {
					final Node nilled = n.getAttributes().getNamedItemNS(Namespaces.SCHEMA_INSTANCE_NS, "nil");
					if (nilled != null) {
						result = new BooleanValue(nilled.getNodeValue().equals("true"));
					} else {
						result = BooleanValue.FALSE;
					}
				} else {
					result = BooleanValue.FALSE;
				}
			}
		}


		if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);} 
        
        return result;           
		
	}

}
