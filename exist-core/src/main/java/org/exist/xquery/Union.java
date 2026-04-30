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
package org.exist.xquery;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.jnode.JNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class Union extends CombiningExpression {

    public Union(final XQueryContext context, final PathExpr left, final PathExpr right) {
        super(context, left, right);
    }

    @Override
    public Sequence combine(final Sequence ls, final Sequence rs) throws XPathException {
        final Sequence result;
        if (ls.isEmpty() && rs.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if (rs.isEmpty()) {
            if (!Type.isNodeType(ls.getItemType())) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "union operand is not a node sequence");
            }
            result = ls;
        } else if (ls.isEmpty()) {
            if (!Type.isNodeType(rs.getItemType())) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "union operand is not a node sequence");
            }
            result = rs;
        } else {
            if (!(Type.isNodeType(ls.getItemType()) && Type.isNodeType(rs.getItemType()))) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "union operand is not a node sequence");
            }
            if (ls.isPersistentSet() && rs.isPersistentSet()) {
                result = ls.toNodeSet().union(rs.toNodeSet());
            } else {
                final ValueSequence values = new ValueSequence(true);
                values.addAll(ls);
                values.addAll(rs);
                values.sortInDocumentOrder();
                values.removeDuplicates();
                // ValueSequence.sortInDocumentOrder cannot order JNodes (the
                // InMemoryNodeComparator only handles XML memtree nodes), so
                // apply a JNode-aware document-order sort when the result
                // contains JSON nodes. removeDuplicates has already run via
                // JNode equals/hashCode in ValueSequence.removeDuplicateNodes.
                if (Type.subTypeOf(values.getItemType(), Type.JSON_NODE)) {
                    sortJNodes(values);
                }
                result = values;
            }
        }

        return result;
    }

    @Override
    protected String getOperatorName() {
        return "union";
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitUnionExpr(this);
    }

    /**
     * In-place document-order sort of a JNode-only ValueSequence.
     * Falls back to leaving the sequence unchanged if any item turns out
     * not to be a JNode (defensive — Type.JSON_NODE itemType implies JNode).
     */
    private static void sortJNodes(final ValueSequence values) {
        final int count = (int) values.getItemCount();
        final List<JNode> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final Item item = values.itemAt(i);
            if (!(item instanceof JNode)) {
                return;
            }
            list.add((JNode) item);
        }
        list.sort(JNode::compareDocumentOrder);
        values.clear();
        for (final JNode n : list) {
            values.add(n);
        }
    }
}
