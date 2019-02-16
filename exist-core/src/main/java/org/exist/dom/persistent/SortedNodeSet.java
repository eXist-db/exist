/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.persistent;

import antlr.collections.AST;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.OrderedLinkedList;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Constants;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringReader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;


public class SortedNodeSet extends AbstractNodeSet {

    private final OrderedLinkedList list = new OrderedLinkedList();

    private final String sortExpr;
    private final BrokerPool pool;
    private final Subject user;

    public SortedNodeSet(final BrokerPool pool, final Subject user, final String sortExpr) {
        this.sortExpr = sortExpr;
        this.pool = pool;
        this.user = user;
    }

    @Override
    public boolean isEmpty() {
        return list.size() == 0;
    }

    @Override
    public boolean hasOne() {
        return list.size() == 1;
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        addAll(other.toNodeSet());
    }

    @Override
    public void addAll(final NodeSet other) {
        final long start = System.currentTimeMillis();
        final MutableDocumentSet docs = new DefaultDocumentSet();
        for(final Iterator<NodeProxy> i = other.iterator(); i.hasNext(); ) {
            final NodeProxy p = i.next();
            docs.add(p.getOwnerDocument());
        }
        // TODO(pkaminsk2): why replicate XQuery.compile here?
        try(final DBBroker broker = pool.get(Optional.ofNullable(user))) {

            final XQueryContext context = new XQueryContext(pool);
            final XQueryLexer lexer = new XQueryLexer(context, new StringReader(sortExpr));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser treeParser = new XQueryTreeParser(context);
            parser.xpath();
            if(parser.foundErrors()) {
                //TODO : error ?
                LOG.debug(parser.getErrorMessage());
            }
            final AST ast = parser.getAST();
            LOG.debug("generated AST: " + ast.toStringTree());
            final PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if(treeParser.foundErrors()) {
                LOG.debug(treeParser.getErrorMessage());
            }
            expr.analyze(new AnalyzeContextInfo());
            for(final SequenceIterator i = other.iterate(); i.hasNext(); ) {
                final NodeProxy p = (NodeProxy) i.nextItem();
                final IteratorItem item = new IteratorItem(p, expr);
                list.add(item);
            }
        } catch(final antlr.RecognitionException re) {
            LOG.debug(re); //TODO : throw exception ! -pb
        } catch(final antlr.TokenStreamException tse) {
            LOG.debug(tse); //TODO : throw exception ! -pb
        } catch(final EXistException e) {
            LOG.debug("Exception during sort", e); //TODO : throw exception ! -pb
        } catch(final XPathException e) {
            LOG.debug("Exception during sort", e); //TODO : throw exception ! -pb
        }
        LOG.debug("sort-expression found " + list.size() + " in "
            + (System.currentTimeMillis() - start) + "ms.");
    }

    public void addAll(final NodeList other) {
        if(!(other instanceof NodeSet)) {
            throw new RuntimeException("not implemented!");
        }
        addAll((NodeSet) other);
    }

    @Override
    public boolean contains(final NodeProxy proxy) {
        for(final Iterator<IteratorItem> i = list.iterator(); i.hasNext(); ) {
            final NodeProxy p = (i.next()).proxy;
            if(p.compareTo(proxy) == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NodeProxy get(final int pos) {
        final IteratorItem item = (IteratorItem) list.get(pos);
        return item == null ? null : item.proxy;
    }

    public NodeProxy get(final DocumentImpl doc, final NodeId nodeId) {
        final NodeProxy proxy = new NodeProxy(doc, nodeId);
        for(final Iterator<IteratorItem> i = list.iterator(); i.hasNext(); ) {
            final NodeProxy p = (i.next()).proxy;
            if(p.compareTo(proxy) == 0) {
                return p;
            }
        }
        return null;
    }

    @Override
    public NodeProxy get(final NodeProxy proxy) {
        for(final Iterator<IteratorItem> i = list.iterator(); i.hasNext(); ) {
            final NodeProxy p = (i.next()).proxy;
            if(p.compareTo(proxy) == 0) {
                return p;
            }
        }
        return null;
    }

    @Override
    public int getLength() {
        return list.size();
    }

    @Override
    public long getItemCountLong() {
        return list.size();
    }

    @Override
    public Node item(final int pos) {
        final NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
        return p == null ? null : p.getOwnerDocument().getNode(p);
    }

    //TODO : evaluate both semantics (item/itemAt)
    @Override
    public Item itemAt(final int pos) {
        final NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
        return p == null ? null : p;
    }

    @Override
    public NodeSetIterator iterator() {
        return new SortedNodeSetIterator(list.iterator());
    }

    @Override
    public SequenceIterator iterate() {
        return new SortedNodeSetIterator(list.iterator());
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new SortedNodeSetIterator(list.iterator());
    }

    private static final class SortedNodeSetIterator implements NodeSetIterator, SequenceIterator {

        private final Iterator<IteratorItem> ii;

        public SortedNodeSetIterator(final Iterator<IteratorItem> i) {
            ii = i;
        }

        public final boolean hasNext() {
            return ii.hasNext();
        }

        @Override
        public final NodeProxy next() {
            if(!ii.hasNext()) {
                throw new NoSuchElementException();
            } else {
                return ii.next().proxy;
            }
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public final NodeProxy peekNode() {
            return null;
        }

        @Override
        public final Item nextItem() {
            if(!ii.hasNext()) {
                return null;
            } else {
                return ii.next().proxy;
            }
        }

        @Override
        public final void setPosition(final NodeProxy proxy) {
            throw new UnsupportedOperationException("NodeSetIterator.setPosition() is not supported by SortedNodeSetIterator");
        }
    }

    private static final class IteratorItem extends OrderedLinkedList.Node {
        private final NodeProxy proxy;
        private String value = null;

        public IteratorItem(final NodeProxy proxy, final PathExpr expr) {
            this.proxy = proxy;
            try {
                final Sequence seq = expr.eval(proxy);
                final StringBuilder buf = new StringBuilder();
                final OrderedLinkedList strings = new OrderedLinkedList();
                Item item;
                for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                    item = i.nextItem();
                    strings.add(new OrderedLinkedList.SimpleNode(item.getStringValue().toUpperCase()));
                }
                for(final Iterator<OrderedLinkedList.SimpleNode> j = strings.iterator(); j.hasNext(); ) {
                    buf.append((j.next()).getData());
                }
                value = buf.toString();
            } catch(final XPathException e) {
                LOG.warn(e.getMessage(), e); //TODO : throw exception ! -pb
            }
        }

        @Override
        public int compareTo(final OrderedLinkedList.Node other) {
            final IteratorItem o = (IteratorItem) other;
            if(value == null) {
                return o.value == null ? Constants.EQUAL : Constants.SUPERIOR;
            } else if(o.value == null) {
                return Constants.INFERIOR;
            } else {
                return value.compareTo(o.value);
            }
        }

        @Override
        public boolean equals(final OrderedLinkedList.Node other) {
            final IteratorItem o = (IteratorItem) other;
            return value.equals(o.value);
        }
    }

    @Override
    public void add(final NodeProxy proxy) {
        LOG.info("Called SortedNodeSet.add()");
    }

}
