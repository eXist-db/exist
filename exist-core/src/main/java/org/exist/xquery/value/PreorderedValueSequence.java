/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org)
 *  and others (see http://exist-db.org)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xquery.value;

import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;

import java.util.Arrays;
import java.util.Comparator;

/**
 * A sequence that sorts its items in the order specified by the order specs
 * of an "order by" clause. Used by {@link org.exist.xquery.ForExpr}.
 *
 * For better performance, the whole input sequence is sorted in one single step.
 * However, this only works if every order expression returns a result of type
 * node.
 *
 * @author wolf
 */
public class PreorderedValueSequence extends AbstractSequence {

    private final OrderSpec[] orderSpecs;
    private final OrderedNodeProxy[] nodes;

    public PreorderedValueSequence(final OrderSpec specs[], final Sequence input, final int contextId) throws XPathException {
        this.orderSpecs = specs;
        this.nodes = new OrderedNodeProxy[input.getItemCount()];
        int j = 0;
        for (final SequenceIterator i = input.unorderedIterator(); i.hasNext(); j++) {
            final NodeProxy p = (NodeProxy) i.nextItem();
            nodes[j] = new OrderedNodeProxy(p);
            p.addContextNode(contextId, nodes[j]);
        }
        processAll();
    }

    private void processAll() throws XPathException {
        for (int i = 0; i < orderSpecs.length; i++) {
            final Expression expr = orderSpecs[i].getSortExpression();
            final NodeSet result = expr.eval(null).toNodeSet();
            for (final NodeProxy p : result) {
                ContextItem context = p.getContext();
                //TODO : review to consider transverse context
                while (context != null) {
                    if (context.getNode() instanceof OrderedNodeProxy) {
                        final OrderedNodeProxy cp = (OrderedNodeProxy) context.getNode();
                        cp.values[i] = p.atomize();
                    }
                    context = context.getNextDirect();
                }
            }
        }
    }

    @Override
    public void clearContext(final int contextId) {
        for (final OrderedNodeProxy node : nodes) {
            node.clearContext(contextId);
        }
    }

    @Override
    public int getItemType() {
        return Type.NODE;
    }

    @Override
    public SequenceIterator iterate() {
        sort();
        return new PreorderedValueSequenceIterator();
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new PreorderedValueSequenceIterator();
    }

    @Override
    public long getItemCountLong() {
        return nodes.length;
    }

    @Override
    public boolean isEmpty() {
        return nodes.length == 0;
    }

    @Override
    public boolean hasOne() {
        return nodes.length == 1;
    }

    @Override
    public void add(final Item item) {
    }

    @Override
    public Item itemAt(final int pos) {
        return nodes[pos];
    }

    @Override
    public NodeSet toNodeSet() {
        return null;
    }

    @Override
    public MemoryNodeSet toMemNodeSet() {
        return null;
    }

    @Override
    public void removeDuplicates() {
        // TODO: is this ever relevant?
    }

    private void sort() {
        Arrays.sort(nodes, new OrderedComparator());
    }

    private class OrderedComparator implements Comparator<OrderedNodeProxy> {

        @Override
        public int compare(final OrderedNodeProxy p1, final OrderedNodeProxy p2) {
            int cmp = 0;
            for (int i = 0; i < p1.values.length; i++) {
                try {
                    final AtomicValue a = p1.values[i];
                    final AtomicValue b = p2.values[i];
                    if (a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.INFERIOR;
                        } else {
                            cmp = Constants.SUPERIOR;
                        }
                    } else if (a != AtomicValue.EMPTY_VALUE && b == AtomicValue.EMPTY_VALUE) {
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.SUPERIOR;
                        } else {
                            cmp = Constants.INFERIOR;
                        }
                    } else {
                        cmp = a.compareTo(orderSpecs[i].getCollator(), b);
                    }
                    if ((orderSpecs[i].getModifiers() & OrderSpec.DESCENDING_ORDER) != 0) {
                        cmp = cmp * -1;
                    }
                    if (cmp != Constants.EQUAL) {
                        break;
                    }
                } catch (final XPathException e) {
                }
            }
            return cmp;
        }
    }

    private class OrderedNodeProxy extends NodeProxy {
        final AtomicValue[] values;

        public OrderedNodeProxy(final NodeProxy p) {
            super(p);
            this.values = new AtomicValue[orderSpecs.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = AtomicValue.EMPTY_VALUE;
            }
        }
    }

    private class PreorderedValueSequenceIterator implements SequenceIterator {
        private int pos = 0;

        @Override
        public boolean hasNext() {
            return pos < nodes.length;
        }

        @Override
        public Item nextItem() {
            if (pos < nodes.length) {
                return nodes[pos++];
            }
            return null;
        }

        @Override
        public long skippable() {
            if (pos < nodes.length) {
                return nodes.length - pos;
            }
            return 0;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, pos < nodes.length ? nodes.length - pos : 0);
            pos += skip;
            return skip;
        }
    }
}
