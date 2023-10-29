/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.modules.xmldiff;

import io.lacuna.bifurcan.IMap;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.functions.map.MapType;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import javax.xml.transform.Source;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.xmldiff.XmlDiffModule.functionSignature;

/**
 * Module for comparing XML documents and nodes.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Compare extends BasicFunction {

    private static final StringValue EQUIVALENT_MAP_KEY = new StringValue("equivalent");
    private static final StringValue POSITION_MAP_KEY = new StringValue("position");
    private static final StringValue MESSAGE_MAP_KEY = new StringValue("message");

    private static final FunctionParameterSequenceType FS_PARAM_NODE_SET_1 = optManyParam("node-set-1", Type.NODE, "The first node set.");
    private static final FunctionParameterSequenceType FS_PARAM_NODE_SET_2 = optManyParam("node-set-2", Type.NODE, "The second node set.");

    private static final String FNS_COMPARE = "compare";
    private static final String FNS_DIFF = "diff";

    public static final FunctionSignature FS_COMPARE = functionSignature(
            FNS_COMPARE,
            "Compares two nodes sets to determine their equivalence." +
                    "Equivalence is determined in 3 stages, first by sequence length, then equivalent Node types, and finally by XMLUnit Diff.",
            returns(Type.BOOLEAN, "Returns true if the node sets $node-set-1 and $node-set-2 are equal, false otherwise. " +
                    "This function is a simplified version of: " + XmlDiffModule.PREFIX + ":" + FNS_DIFF + "#2 that only returns true or false."),
            FS_PARAM_NODE_SET_1,
            FS_PARAM_NODE_SET_2
    );

    public static final FunctionSignature FS_DIFF = functionSignature(
            FNS_DIFF,
            "Reports on the differences between two nodes sets to determine their equality." +
                    "Equality is determined in 3 stages, first by sequence length, then equivalent Node types, and finally by XMLUnit Diff for Document and Element nodes, or fn:deep-equals for all other node types.",
            returns(Type.MAP_ITEM, "Returns a map(xs:string, xs:anyAtomicType). When the node sets are equivalent the map is: map {'equivalent': fn:true() }. When the nodesets are not equivalent, the map is structured like: map {'equivalent': fn:false(), 'position': xs:integer, 'message': xs:string}."),
            FS_PARAM_NODE_SET_1,
            FS_PARAM_NODE_SET_2
    );

    public Compare(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence nodeSet1 = args[0];
        final Sequence nodeSet2 = args[1];

        final int itemCount1 = nodeSet1.getItemCount();
        final int itemCount2 = nodeSet2.getItemCount();

        // first determination - are the sequences of the same length?
        if (itemCount1 != itemCount2) {
            if (isCalledAs(FNS_COMPARE)) {
                return BooleanValue.FALSE;
            } else {
                return falseMapResult(Math.min(itemCount1, itemCount2), "Sequences are of different lengths: fn:length($node-set-1) eq " + itemCount1 + ", fn:length($node-set-2) eq " + itemCount2 + ".");
            }
        }

        // second determination - do the sequences contain the same types?
        for (int i = 0; i < itemCount1; i++) {
            final Item item1 = nodeSet1.itemAt(i);
            final Item item2 = nodeSet2.itemAt(i);

            if (item1.getType() != item2.getType()) {
                if (isCalledAs(FNS_COMPARE)) {
                    return BooleanValue.FALSE;
                } else {
                    return falseMapResult(i + 1, "Items are of different types: $node-set-1[" + i + "] as " + Type.getTypeName(item1.getType()) + ", $node-set-2[" + i + "] as " + Type.getTypeName(item2.getType()) + ".");
                }
            }
        }

        // third determination - does XMLUnit consider each node in the sequences to be equal
        for (int i = 0; i < itemCount1; i++) {
            final Node node1 = toNode(nodeSet1.itemAt(i));
            final Node node2 = toNode(nodeSet2.itemAt(i));

            if (node1 == null || node2 == null) {
                throw new XPathException(this, XmlDiffModule.UNSUPPORTED_DOM_IMPLEMENTATION, "Unable to determine DOM implementation of node set item");
            }

            final Source expected = Input.fromNode(node1).build();
            final Source actual = Input.fromNode(node2).build();

            final Diff diff = DiffBuilder.compare(expected).withTest(actual)
                    .checkForIdentical()
                    .build();

            if (diff.hasDifferences()) {
                if (isCalledAs(FNS_COMPARE)) {
                    return BooleanValue.FALSE;
                } else {
                    return falseMapResult(i + 1, diff.toString());
                }
            }
        }

        if (isCalledAs(FNS_COMPARE)) {
            return BooleanValue.TRUE;
        } else {
            return trueMapResult();
        }
    }

    private MapType trueMapResult() {
        return new MapType(getContext(), getContext().getDefaultCollator(), EQUIVALENT_MAP_KEY, BooleanValue.TRUE);
    }

    private MapType falseMapResult(final int sequencePosition, final String message) {
        final IMap<AtomicValue, Sequence> linearMap = MapType.newLinearMap(getContext().getDefaultCollator());
        linearMap.put(EQUIVALENT_MAP_KEY, BooleanValue.FALSE);
        linearMap.put(POSITION_MAP_KEY, new IntegerValue(sequencePosition));
        linearMap.put(MESSAGE_MAP_KEY, new StringValue(message.trim()));
        return new MapType(getContext(), linearMap.forked(), Type.STRING);
    }

    private static @Nullable Node toNode(final Item item) {
        if (item instanceof Node) {
            return (Node) item;
        }

        if (item instanceof NodeProxy) {
            return ((NodeProxy) item).getNode();
        }

        return null;
    }
}
