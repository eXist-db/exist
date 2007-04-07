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
 * $Id: FunLast.java 5547 2007-03-28 19:46:33Z brihaye $
 */

package org.exist.xquery.functions;

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
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Built-in function fn:last().
 * 
 * @author wolf
 */
public class FunNilled extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("nilled", Function.BUILTIN_FUNCTION_NS),
			"Returns whether $a is nilled of not.",
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE));

	public FunNilled(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result;
        if (args[0].isEmpty())
        	result = Sequence.EMPTY_SEQUENCE;
        else {
            //TODO : how to improve performance ?
            Node n = ((NodeValue)args[0]).getNode();
            if (!(n.getNodeType() == Node.ELEMENT_NODE))
            	result = Sequence.EMPTY_SEQUENCE;
            else {
            	//TODO : think more...
            	if (n.hasAttributes()) {
            		Node nilled =n.getAttributes().getNamedItemNS(Namespaces.SCHEMA_INSTANCE_NS, "nil");
            		if (nilled != null)
            			result = new BooleanValue(nilled.getNodeValue() == "false");
            		else
            			result = BooleanValue.FALSE;
            	} else
            		result = BooleanValue.FALSE;
            }
        }
        	
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;           
		
	}

}
