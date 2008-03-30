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
 *  $Id: FunId.java 5378 2007-02-21 14:33:35Z brihaye $
 */

package org.exist.xquery.functions;

import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
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
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class FunIdRef extends Function {

	public final static FunctionSignature signature[] = {
		new FunctionSignature(
			new QName("idref", Function.BUILTIN_FUNCTION_NS),
			"Returns the sequence of element or attributes nodes with an IDREF value matching the " +
			"value of one or more of the ID values supplied in $a. " +
			"If none is matching or $a is the empty sequence, returns the empty sequence.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)),
        new FunctionSignature(
            new QName("idref", Function.BUILTIN_FUNCTION_NS),
			"Returns the sequence of element or attributes nodes with an IDREF value matching the " +
			"value of one or more of the ID values supplied in $a. " +
            "If none is matching or $a is the empty sequence, returns the empty sequence.",
            new SequenceType[] {
                 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
                 new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)},
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE))
    };
				
	/**
	 * Constructor for FunId.
	 */
	public FunIdRef(XQueryContext context, FunctionSignature signature) {
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
        boolean processInMem = false;
        Expression arg = getArgument(0);        
		Sequence idrefval = arg.eval(contextSequence);
		if(idrefval.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		String nextId;
    		DocumentSet docs = null;
            if (getArgumentCount() == 2) {
                // second argument should be a node, whose owner document will be
                // searched for the id
                Sequence nodes = getArgument(1).eval(contextSequence);
                if (nodes.isEmpty())
                    throw new XPathException(getASTNode(), 
                            "XPDY0002: no node or context item for fn:idref");
                if (!Type.subTypeOf(nodes.itemAt(0).getType(), Type.NODE)) 
                	throw new XPathException(getASTNode(), 
                    "XPTY0004: fn:idref() argument is not a node");               	
                NodeValue node = (NodeValue)nodes.itemAt(0);
                if (node.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                    processInMem = true;
                else {
                    MutableDocumentSet ndocs = new DefaultDocumentSet();
                    ndocs.add(((NodeProxy)node).getDocument());
                    docs = ndocs;
                }
                contextSequence = node;
            } else if (contextSequence == null)
                throw new XPathException(getASTNode(), "XPDY0002: no context item specified");
            else if(!Type.subTypeOf(contextSequence.getItemType(), Type.NODE))
    			throw new XPathException(getASTNode(), "XPTY0004: context item is not a node");
    		else {
    			if (contextSequence.isPersistentSet())
                    docs = contextSequence.toNodeSet().getDocumentSet();
                else
                    processInMem = true;
            }

            if (processInMem)
                result = new ValueSequence();
            else
                result = new ExtArrayNodeSet();

            for(SequenceIterator i = idrefval.iterate(); i.hasNext(); ) {
    			nextId = i.nextItem().getStringValue();
                if (nextId.length() == 0) continue;
                if(XMLChar.isValidNCName(nextId)) {
                    if (processInMem)
                        getIdRef(result, contextSequence, nextId);
                    else
                        getIdRef((NodeSet)result, docs, nextId);
                }
    		}
        }

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;   
        
	}

	private void getIdRef(NodeSet result, DocumentSet docs, String id) throws XPathException {
		NodeSet attribs = context.getBroker().getValueIndex().find(Constants.EQ, docs, null, -1, null, new StringValue(id, Type.IDREF));
		NodeProxy n;
		for (Iterator i = attribs.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
            n.setNodeType(Node.ATTRIBUTE_NODE);
            result.add(n);
		}
	}

    private void getIdRef(Sequence result, Sequence seq, String id) throws XPathException {
        Set visitedDocs = new TreeSet();
        for (SequenceIterator i = seq.iterate(); i.hasNext();) {
            org.exist.memtree.NodeImpl v = (org.exist.memtree.NodeImpl) i.nextItem();
            org.exist.memtree.DocumentImpl doc = v.getDocument();
            if (!visitedDocs.contains(doc)) {
                org.exist.memtree.NodeImpl node = doc.selectByIdref(id);
                if (node != null)
                    result.add(node);
                visitedDocs.add(doc);
            }
        }
    }
}