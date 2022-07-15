/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.*;
import org.exist.util.XMLNames;
import org.exist.xquery.*;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

public class FunElementWithId extends Function {
    private static final String FN_NAME = "element-with-id";
    private static final String FN_DESCRIPTION =
            "Returns the sequence of element nodes that have an ID value " +
                    "matching the value of one or more of the IDREF values supplied in $idrefs. " +
                    "If none is matching or $idrefs is the empty sequence, returns the empty sequence.";
    private static final FunctionReturnSequenceType FN_RETURN = returnsOptMany(Type.STRING, "the elements with IDs matching IDREFs from $idref-sequence");
    private static final FunctionParameterSequenceType PARAM_ID_REFS_STRING = optManyParam("idrefs", Type.STRING, "The IDREF sequence");
    public static final FunctionSignature[] FS_ELEMENT_WITH_ID_SIGNATURES = functionSignatures(
            FN_NAME,
            FN_DESCRIPTION,
            FN_RETURN,
            arities(
                    arity(),
                    arity(PARAM_ID_REFS_STRING)
            )
    );

	public FunElementWithId(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

    @Override
	public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }

        if (getArgumentCount() < 1) {
			throw new XPathException(this, ErrorCodes.XPST0017, "function element-with-id requires one argument");
        }
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final Sequence result;
        boolean processInMem = false;
        final Expression arg = getArgument(0);
		final Sequence idval = arg.eval(contextSequence);

		if (idval.isEmpty() || (getArgumentCount() == 1 && contextSequence != null && contextSequence.isEmpty())) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
    		String nextId;
    		DocumentSet docs = null;
            if (getArgumentCount() == 2) {
                final Sequence nodes = getArgument(1).eval(contextSequence);
                if (nodes.isEmpty()) {
                    throw new XPathException(this, ErrorCodes.XPDY0002, "XPDY0002: no node or context item for fn:id", nodes);
                } else if (!Type.subTypeOf(nodes.itemAt(0).getType(), Type.NODE)) {
                	throw new XPathException(this, ErrorCodes.XPTY0004, "XPTY0004: fn:id() argument is not a node", nodes);
                }
                NodeValue node = (NodeValue)nodes.itemAt(0);
                if (node.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                    processInMem = true;
                } else {
                    MutableDocumentSet ndocs = new DefaultDocumentSet();
                    ndocs.add(((NodeProxy)node).getOwnerDocument());
                    docs = ndocs;
                }
                contextSequence = node;
            } else if (contextSequence == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "No context item specified");
           } else if(!Type.subTypeOf(contextSequence.getItemType(), Type.NODE)) {
    			throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node", contextSequence);
    		} else {
    			if (contextSequence.isPersistentSet()) {
                    docs = contextSequence.toNodeSet().getDocumentSet();
                } else {
                    processInMem = true;
                }
            }

            if (processInMem) {
                result = new ValueSequence();
            } else {
                result = new ExtArrayNodeSet();
            }

            for(final SequenceIterator i = idval.iterate(); i.hasNext(); ) {
    			nextId = i.nextItem().getStringValue();
                if (!nextId.isEmpty()) {
                    if (nextId.indexOf(' ') != Constants.STRING_NOT_FOUND) {
                        final StringTokenizer tok = new StringTokenizer(nextId, " ");
                        while (tok.hasMoreTokens()) {
                            nextId = tok.nextToken();
                            if (XMLNames.isNCName(nextId)) {
                                if (processInMem) {
                                    getId(result, contextSequence, nextId);
                                } else {
                                    getId((NodeSet) result, docs, nextId);
                                }
                            }
                        }
                    } else {
                        if (XMLNames.isNCName(nextId)) {
                            if (processInMem) {
                                getId(result, contextSequence, nextId);
                            } else {
                                getId((NodeSet) result, docs, nextId);
                            }
                        }
                    }
                }
    		}
        }
		result.removeDuplicates();
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
	}

	private void getId(final NodeSet result, final DocumentSet docs, final String id) throws XPathException {
		final NodeSet attribs = context.getBroker().getValueIndex().find(context.getWatchDog(), Comparison.EQ, docs, null, -1, null, new StringValue(id, Type.ID));
		NodeProxy p;
		for (final NodeProxy n : attribs) {
			p = new NodeProxy(n.getOwnerDocument(), n.getNodeId().getParentId(), Node.ELEMENT_NODE);
			result.add(p);
		}
	}

    private void getId(final Sequence result, final Sequence seq, final String id) throws XPathException {
        final Set<DocumentImpl> visitedDocs = new TreeSet<>();
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final NodeImpl v = (NodeImpl) i.nextItem();
            final DocumentImpl doc = v.getNodeType() == Node.DOCUMENT_NODE ? (DocumentImpl)v : v.getOwnerDocument();

            if (doc != null && !visitedDocs.contains(doc)) {
                final NodeImpl elem = doc.selectById(id, true);
                if (elem != null) {
                    result.add(elem);
                }
                visitedDocs.add(doc);
            }
        }
    }

    @Override
    public int getDependencies() {
        return getArgument(0).getDependencies();
    }
}
