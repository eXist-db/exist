/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.indexing.QueryableRangeIndex;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.XMLChar;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.*;

import org.exist.xquery.ErrorCodes;

import javax.xml.XMLConstants;

/**
 *
 * @author wolf
 * @author perig
 *
 */
public class FunId extends Function {
	protected static final Logger logger = LogManager.getLogger(FunId.class);
	public final static FunctionSignature signature[] = {
			new FunctionSignature(
				new QName("id", Function.BUILTIN_FUNCTION_NS),
				"Returns the sequence of element nodes that have an ID value " +
				"matching the value of one or more of the IDREF values supplied in $idrefs. " +
				"If none is matching or $idrefs is the empty sequence, returns the empty sequence.",
				new SequenceType[] {
                    new FunctionParameterSequenceType("idrefs", Type.STRING, Cardinality.ZERO_OR_MORE, "The IDREF sequence")
                },
				new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_MORE, "the elements with IDs  matching IDREFs from $idref-sequence")),
            new FunctionSignature(
                    new QName("id", Function.BUILTIN_FUNCTION_NS),
                    "Returns the sequence of element nodes that have an ID value " +
                    "matching the value of one or more of the IDREF values supplied in $idrefs and is in the same document as $node-in-document. " +
                    "If none is matching or $idrefs is the empty sequence, returns the empty sequence.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("idrefs", Type.STRING, Cardinality.ZERO_OR_MORE, "The IDREF sequence"),
                        new FunctionParameterSequenceType("node-in-document", Type.NODE, Cardinality.EXACTLY_ONE, "The node in document")
                    },
                    new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_MORE, "the elements with IDs matching IDREFs from $idrefs in the same document as $node-in-document"))
    };

	/**
	 * Constructor for FunId.
	 */
	public FunId(XQueryContext context, FunctionSignature signature) {
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

        if (getArgumentCount() < 1) {
            logger.error("function id requires one argument");
			throw new XPathException(this, ErrorCodes.XPST0017, "function id requires one argument");
        }
        if(contextItem != null)
			{contextSequence = contextItem.toSequence();}

        Sequence result;
        boolean processInMem = false;
        final Expression arg = getArgument(0);
		final Sequence idval = arg.eval(contextSequence);
		
		if(idval.isEmpty() || (getArgumentCount() == 1 && contextSequence != null && contextSequence.isEmpty()))
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
    		String nextId;
    		DocumentSet docs = null;
            if (getArgumentCount() == 2) {
                // second argument should be a node, whose owner document will be
                // searched for the id
                final Sequence nodes = getArgument(1).eval(contextSequence);
                if (nodes.isEmpty()) {
                    logger.error(ErrorCodes.XPDY0002 + " No node or context item for fn:id");
                    throw new XPathException(this, ErrorCodes.XPDY0002, "XPDY0002: no node or context item for fn:id", nodes);
                }
                if (!Type.subTypeOf(nodes.itemAt(0).getType(), Type.NODE)) {
                    logger.error(ErrorCodes.XPTY0004 + " fn:id() argument is not a node");
                	throw new XPathException(this, ErrorCodes.XPTY0004, "XPTY0004: fn:id() argument is not a node", nodes);
                }
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
            } else if (contextSequence == null) {
                logger.error(ErrorCodes.XPDY0002 +  " No context item specified");
                throw new XPathException(this, ErrorCodes.XPDY0002, "No context item specified");
           } else if(!Type.subTypeOf(contextSequence.getItemType(), Type.NODE)) {
                logger.error(ErrorCodes.XPTY0004 + " Context item is not a node");
    			throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node", contextSequence);
    		} else {
    			if (contextSequence.isPersistentSet())
                    {docs = contextSequence.toNodeSet().getDocumentSet();}
                else
                    {processInMem = true;}
            }

            if (processInMem) {
                result = new ValueSequence();
            }
            else  {
                result = new ExtArrayNodeSet();
            }

            //TODO(AR) may be able to remove single stepping for persistent nodes
            //as the new Lucene Range Index allows us to test many atomic values
            //in one call to {@link org.exist.indexing.QueryableRangeIndex#query}
            for(final SequenceIterator i = idval.iterate(); i.hasNext(); ) {
    			nextId = i.nextItem().getStringValue();
                if (nextId.length() == 0)
                    {continue;}
    			if(nextId.indexOf(" ") != Constants.STRING_NOT_FOUND) {
    				// parse idrefs
    				final StringTokenizer tok = new StringTokenizer(nextId, " ");
    				while(tok.hasMoreTokens()) {
    					nextId = tok.nextToken();
    					if(XMLChar.isValidNCName(nextId)) {
                            if (processInMem)
                                {getId(result, contextSequence, nextId);}
                            else
                                {getId(contextSequence, (NodeSet)result, docs, nextId);}
                        }
    				}
    			} else {
    				if(XMLChar.isValidNCName(nextId)) {
                        if (processInMem)
                            {getId(result, contextSequence, nextId);}
                        else
                            {getId(contextSequence, (NodeSet)result, docs, nextId);}
                    }
    			}
    		}
        }

		result.removeDuplicates();

        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}

        return result;

	}

    private final static QName XML_ID = new QName("id", XMLConstants.XML_NS_URI, ElementValue.ATTRIBUTE);
    private final static NodePath XML_ID_PATH = new NodePath(XML_ID);

	private void getId(final Sequence contextSequence, final NodeSet result, final DocumentSet docs, final String id) throws XPathException {

        final QueryableRangeIndex rangeIndexWorker = (QueryableRangeIndex)context.getBroker().getIndexController().getWorkerByIndexId("org.exist.indexing.range.RangeIndex");
        if(rangeIndexWorker == null) {
            throw new XPathException(this, "Using fn:id requires Range Indexes to be enabled");
        } else {
            final boolean isConfiguredFor = rangeIndexWorker.isConfiguredFor(context.getBroker(), contextSequence, XML_ID_PATH);
            if (!isConfiguredFor) {
                throw new XPathException(this, "Using fn:id requires a Range Index qname definition on @xml:id");
            } else {
                try {
                    final NodeSet attribs = rangeIndexWorker.query(getExpressionId(), docs, null, Arrays.asList(XML_ID), new AtomicValue[]{new StringValue(id)}, QueryableRangeIndex.OperatorFactory.EQ, NodeSet.ANCESTOR);
                    for (final NodeProxy n : attribs) {
                        final NodeProxy p = new NodeProxy(n.getOwnerDocument(), n.getNodeId().getParentId(), Node.ELEMENT_NODE);
                        result.add(p);
                    }
                } catch(final IOException e) {
                    throw new XPathException(this, e.getMessage(), e);
                }
            }
        }
	}

    private void getId(Sequence result, Sequence seq, String id) throws XPathException {
        final Set<DocumentImpl> visitedDocs = new TreeSet<DocumentImpl>();
        for (final SequenceIterator i = seq.iterate(); i.hasNext();) {
            final NodeImpl v = (NodeImpl) i.nextItem();
            final DocumentImpl doc = v.getOwnerDocument();
            if (!visitedDocs.contains(doc)) {
                final NodeImpl elem = doc.selectById(id);
                if (elem != null)
                    {result.add(elem);}
                visitedDocs.add(doc);
            }
        }
    }

    @Override
    public int getDependencies() {
        // fn:id can operate on the entire context sequence at once - unless the
        // argument depends on the context item
        return getArgument(0).getDependencies();
    }
}
