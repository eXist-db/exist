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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.ItemComparator;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.jnode.JNode;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class Except extends CombiningExpression {

    public Except(final XQueryContext context, final PathExpr left, final PathExpr right) {
        super(context, left, right);
    }

    @Override
    public Sequence combine(final Sequence ls, final Sequence rs) throws XPathException {
        final Sequence result;
        if (ls.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if (rs.isEmpty()) {
            if (!Type.isNodeType(ls.getItemType())) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "except operand is not a node sequence");
            }
            result = ls;
        } else {
            if (!(Type.isNodeType(ls.getItemType()) && Type.isNodeType(rs.getItemType()))) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "except operand is not a node sequence");
            }
            if (ls.isPersistentSet() && rs.isPersistentSet()) {
                result = ls.toNodeSet().except(rs.toNodeSet());
            } else {
                result = new ValueSequence();
                // ItemComparator (TreeSet) cannot order JNodes; fall back to a
                // HashSet that relies on JNode equals/hashCode for membership.
                final boolean hasJsonNodes = Type.subTypeOf(ls.getItemType(), Type.JSON_NODE)
                        || Type.subTypeOf(rs.getItemType(), Type.JSON_NODE);
                final Set<Item> set = hasJsonNodes
                        ? new java.util.HashSet<>()
                        : new TreeSet<>(new ItemComparator());
                for (final SequenceIterator i = rs.unorderedIterator(); i.hasNext(); ) {
                    set.add(i.nextItem());
                }
                for (final SequenceIterator i = ls.unorderedIterator(); i.hasNext(); ) {
                    final Item next = i.nextItem();
                    if (!set.contains(next)) {
                        result.add(next);
                    }
                }
                result.removeDuplicates();
                // Doc-order sort for JNode-only results (see Union.sortJNodes).
                if (Type.subTypeOf(result.getItemType(), Type.JSON_NODE)) {
                    sortJNodes((ValueSequence) result);
                }
            }
        }

        return result;
    }

    @Override
    protected String getOperatorName() {
        return "except";
    }

    /** In-place document-order sort of a JNode-only ValueSequence. */
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
