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

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;


public class FnOuterMost extends BasicFunction {

    public final static FunctionSignature FNS_OUTERMOST = new FunctionSignature(
            new QName("outermost", Function.BUILTIN_FUNCTION_NS),
            "Returns every node within the input sequence that has no ancestor that is itself a member of the input sequence; the nodes are returned in document order with duplicates eliminated.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes to test")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes that have no ancestor which is itself in the input sequence")
    );

    public FnOuterMost(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        return filterByDocumentAncestry(
                args[0],
                (currentNodeId, docNodeIds) -> docNodeIds.parallelStream().anyMatch(currentNodeId::isDescendantOf)
        );
    }

    static Sequence filterByDocumentAncestry(
            final Sequence nodes,
            final BiPredicate<NodeId, List<NodeId>> hasRelatedNode
    ) throws XPathException {
        if (nodes.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        } else if (nodes.hasOne()) {
            return nodes;
        }

        final Sequence results = new ValueSequence();
        // Group NodeIds by document so equal NodeIds in distinct documents (e.g. root DLN "1") do not collide.
        final Map<DocKey, List<NodeId>> nodeIdsByDoc = groupNodeIdsByDoc(nodes);
        final Map<DocKey, Set<NodeId>> foundByDoc = new HashMap<>();

        final SequenceIterator it = nodes.iterate();
        while (it.hasNext()) {
            final Item item = it.nextItem();
            final NodeValue node = (NodeValue) item;
            final DocKey docKey = docKey(node);
            final NodeId currentNodeId = node.getNodeId();
            final Set<NodeId> foundInDoc = foundByDoc.computeIfAbsent(docKey, ignored -> new HashSet<>());
            final List<NodeId> docNodeIds = nodeIdsByDoc.get(docKey);

            if (!foundInDoc.contains(currentNodeId)
                    && !hasRelatedNode.test(currentNodeId, docNodeIds)) {
                results.add(node);
                foundInDoc.add(currentNodeId);
            }
        }

        // the result is in document order, whatever the order of the input
        results.removeDuplicates();
        return results;
    }

    private static Map<DocKey, List<NodeId>> groupNodeIdsByDoc(final Sequence nodes) throws XPathException {
        final Map<DocKey, List<NodeId>> nodeIdsByDoc = new HashMap<>();
        final SequenceIterator it = nodes.iterate();
        while (it.hasNext()) {
            final Item item = it.nextItem();
            final NodeValue node = (NodeValue) item;
            nodeIdsByDoc.computeIfAbsent(docKey(node), ignored -> new ArrayList<>())
                    .add(node.getNodeId());
        }
        return nodeIdsByDoc;
    }

    private static DocKey docKey(final NodeValue node) {
        final int implType = node.getImplementationType();
        final Document document;
        if (node instanceof org.exist.dom.memtree.DocumentImpl memDoc) {
            document = memDoc;
        } else if (Type.DOCUMENT == node.getType() && node instanceof NodeProxy proxy) {
            document = (Document) proxy.getNode();
        } else {
            document = node.getOwnerDocument();
        }
        if (document instanceof org.exist.dom.memtree.DocumentImpl memDoc) {
            return new DocKey(implType, memDoc.getDocId(), null);
        } else if (document instanceof org.exist.dom.persistent.DocumentImpl persistentDoc) {
            return new DocKey(implType, persistentDoc.getDocId(), null);
        }
        return new DocKey(implType, 0L, document != null ? document : node);
    }

    private record DocKey(int implementationType, long docId, Object fallbackDocument) {
    }
}
