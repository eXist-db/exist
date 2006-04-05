/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

/**
 * Built-in function fn:local-name().
 *
 */
public class FunLocalName extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("local-name", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[0],
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
			new QName("local-name", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		)
	};

    public FunLocalName(XQueryContext context, FunctionSignature signature) {
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
        
        if(contextItem != null)
            contextSequence = contextItem.toSequence();
                
        Item item = null;
        // check if the node is passed as an argument or should be taken from
        // the context sequence
        if(getArgumentCount() > 0) {
            Sequence seq = getArgument(0).eval(contextSequence);
            if (!seq.isEmpty())
                item = seq.itemAt(0);
        } else {
            if (!contextSequence.isEmpty())
                item = contextSequence.itemAt(0);
            else
                throw new XPathException(getASTNode(), "undefined context item");
        }
        
        Sequence result;
        if (item == null)
            result = Sequence.EMPTY_SEQUENCE;
        else {
            if(!Type.subTypeOf(item.getType(), Type.NODE))
                throw new XPathException(getASTNode(), "context item is not a node; got: " +
                        Type.getTypeName(item.getType()));
            
            Node n = ((NodeValue)item).getNode();
            switch(n.getNodeType()) {
                case Node.ELEMENT_NODE:
                case Node.ATTRIBUTE_NODE:
                    result = new StringValue(n.getLocalName());
                    break;
                case Node.PROCESSING_INSTRUCTION_NODE:
                    result = new StringValue(((ProcessingInstruction)n).getTarget());
                    break;
                default:
                    result = new StringValue("");
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
    }
}
