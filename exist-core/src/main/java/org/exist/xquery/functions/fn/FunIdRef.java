/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
 * http://exist-db.org
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
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.util.XMLNames;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;

import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author perig
 * @author piotr kaminski
 *
 */
public class FunIdRef extends Function {
	protected static final Logger logger = LogManager.getLogger(FunIdRef.class);
	public final static FunctionSignature signature[] = {
		new FunctionSignature(
			new QName("idref", Function.BUILTIN_FUNCTION_NS),
			"Returns the sequence of element or attributes nodes with an IDREF value matching the " +
			"value of one or more of the ID values supplied in $ids. " +
			"If none is matching or $ids is the empty sequence, returns the empty sequence.",
			new SequenceType[] {
                new FunctionParameterSequenceType("ids", Type.STRING, Cardinality.ZERO_OR_MORE, "The ID sequence"),
            },
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the elements with matching IDREF values from IDs in $ids")),

        new FunctionSignature(
            new QName("idref", Function.BUILTIN_FUNCTION_NS),
			"Returns the sequence of element or attributes nodes with an IDREF value matching the " +
			"value of one or more of the ID values supplied in $ids. " +
            "If none is matching or $ids is the empty sequence, returns the empty sequence.",
            new SequenceType[] {
                new FunctionParameterSequenceType("ids", Type.STRING, Cardinality.ZERO_OR_MORE, "The ID sequence"),
                new FunctionParameterSequenceType("node-in-document", Type.NODE, Cardinality.EXACTLY_ONE, "The node in document")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the elements with matching IDREF values from IDs in $ids in the same document as $node-in-document"))
    };

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
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        if (getArgumentCount() < 1)
			{throw new XPathException(this, ErrorCodes.XPST0017, "function id requires one argument");}
		
        if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
		
        Sequence result;
        boolean processInMem = false;
        final Expression arg = getArgument(0);        
		final Sequence idrefval = arg.eval(contextSequence);
		if(idrefval.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
    		String nextId;
    		DocumentSet docs = null;
            if (getArgumentCount() == 2) {
                // second argument should be a node, whose owner document will be
                // searched for the id
                final Sequence nodes = getArgument(1).eval(contextSequence);
                if (nodes.isEmpty())
                    {throw new XPathException(this, ErrorCodes.XPDY0002, 
                    		"no node or context item for fn:idref");}
                
                if (!Type.subTypeOf(nodes.itemAt(0).getType(), Type.NODE)) 
                	{throw new XPathException(this, ErrorCodes.XPTY0004, 
                			"fn:idref() argument is not a node");}
                
                NodeValue node = (NodeValue)nodes.itemAt(0);
                if (node.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                	//TODO : how to enforce this ?
                	//If $node, or the context item if the second argument is omitted, 
                	//is a node in a tree whose root is not a document node [err:FODC0001] is raised                    processInMem = true;
                    {processInMem = true;}
                else {
                    MutableDocumentSet ndocs = new DefaultDocumentSet();
                    ndocs.add(((NodeProxy)node).getOwnerDocument());
                    docs = ndocs;
                }
                contextSequence = node;
            } else if (contextSequence == null)
                {throw new XPathException(this, ErrorCodes.XPDY0002, "no context item specified");}
            else if(!Type.subTypeOf(contextSequence.getItemType(), Type.NODE))
    			{throw new XPathException(this, ErrorCodes.XPTY0004, "context item is not a node");}
    		else {
    			if (contextSequence.isPersistentSet())
                    {docs = contextSequence.toNodeSet().getDocumentSet();}
                else
                    {processInMem = true;}
            }

            if (processInMem)
                {result = new ValueSequence();}
            else
                {result = new ExtArrayNodeSet();}

            for(final SequenceIterator i = idrefval.iterate(); i.hasNext(); ) {
    			nextId = i.nextItem().getStringValue();
                if (nextId.length() == 0) {continue;}
                if(XMLNames.isNCName(nextId)) {
                    if (processInMem)
                        {getIdRef(result, contextSequence, nextId);}
                    else
                        {getIdRef((NodeSet)result, docs, nextId);}
                }
    		}
        }
		
		result.removeDuplicates();

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;   
        
	}

	private void getIdRef(NodeSet result, DocumentSet docs, String id) throws XPathException {
		final NodeSet attribs = context.getBroker().getValueIndex().find(context.getWatchDog(), Comparison.EQ, docs, null, -1, null, new StringValue(id, Type.IDREF));

		for (final NodeProxy n : attribs) {
            n.setNodeType(Node.ATTRIBUTE_NODE);
            result.add(n);
		}
	}

    private void getIdRef(Sequence result, Sequence seq, String id) throws XPathException {
        final Set<org.exist.dom.memtree.DocumentImpl> visitedDocs = new TreeSet<org.exist.dom.memtree.DocumentImpl>();
        for (final SequenceIterator i = seq.iterate(); i.hasNext();) {
            final org.exist.dom.memtree.NodeImpl v = (org.exist.dom.memtree.NodeImpl) i.nextItem();
            final org.exist.dom.memtree.DocumentImpl doc = v.getOwnerDocument();
            if (!visitedDocs.contains(doc)) {
                final org.exist.dom.memtree.NodeImpl node = doc.selectByIdref(id);
                if (node != null)
                    {result.add(node);}
                visitedDocs.add(doc);
            }
        }
    }
}