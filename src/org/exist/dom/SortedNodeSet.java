package org.exist.dom;

import java.io.StringReader;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.NullAccessContextException;
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

import antlr.collections.AST;

public class SortedNodeSet extends AbstractNodeSet {

    private PathExpr expr;
    private OrderedLinkedList list = new OrderedLinkedList();
    private String sortExpr;
    private BrokerPool pool;
    private Subject user = null;
    private AccessContext accessCtx;

    public SortedNodeSet(BrokerPool pool, Subject user, String sortExpr, AccessContext accessCtx) {
        this.sortExpr = sortExpr;
        this.pool = pool;
        this.user = user;
        if(accessCtx == null)
            {throw new NullAccessContextException();}
        this.accessCtx = accessCtx;
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
    public void addAll(Sequence other) throws XPathException {
        addAll(other.toNodeSet());
    }

    @Override
    public void addAll(NodeSet other) {
        final long start = System.currentTimeMillis();
        final MutableDocumentSet docs = new DefaultDocumentSet();
        for (final Iterator<NodeProxy> i = other.iterator(); i.hasNext();) {
            final NodeProxy p = i.next();
            docs.add(p.getDocument());
        }
        // TODO(pkaminsk2): why replicate XQuery.compile here?
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            final XQueryContext context = new XQueryContext(pool, accessCtx);
            final XQueryLexer lexer = new XQueryLexer(context, new StringReader(sortExpr));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser treeParser = new XQueryTreeParser(context);
            parser.xpath();
            if (parser.foundErrors()) {
                //TODO : error ?
                LOG.debug(parser.getErrorMessage());
            }
            final AST ast = parser.getAST();
            LOG.debug("generated AST: " + ast.toStringTree());
            expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
                LOG.debug(treeParser.getErrorMessage());
            }
            expr.analyze(new AnalyzeContextInfo());
            for (final SequenceIterator i = other.iterate(); i.hasNext();) {
                final NodeProxy p = (NodeProxy) i.nextItem();
                final IteratorItem item = new IteratorItem(broker, p, expr, docs, context);
                list.add(item);
            }
        } catch (final antlr.RecognitionException re) {
            LOG.debug(re); //TODO : throw exception ! -pb
        } catch (final antlr.TokenStreamException tse) {
            LOG.debug(tse); //TODO : throw exception ! -pb
        } catch (final EXistException e) {
            LOG.debug("Exception during sort", e); //TODO : throw exception ! -pb
        } catch (final XPathException e) {
            LOG.debug("Exception during sort", e); //TODO : throw exception ! -pb
        } finally {
            pool.release(broker);
        }
        LOG.debug("sort-expression found " + list.size() + " in "
                + (System.currentTimeMillis() - start) + "ms.");
    }

    public void addAll(NodeList other) {
        if (!(other instanceof NodeSet))
            {throw new RuntimeException("not implemented!");}
        addAll((NodeSet) other);
    }

    @Override
    public boolean contains(NodeProxy proxy) {
        for (final Iterator<IteratorItem> i = list.iterator(); i.hasNext();) {
            final NodeProxy p = (i.next()).proxy;
            if (p.compareTo(proxy) == 0)
                {return true;}
        }
        return false;
    }

    @Override
    public NodeProxy get(int pos) {
        final IteratorItem item = (IteratorItem) list.get(pos);
        return item == null ? null : item.proxy;
    }

    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        final NodeProxy proxy = new NodeProxy(doc, nodeId);
        for (final Iterator<IteratorItem> i = list.iterator(); i.hasNext();) {
            final NodeProxy p = (i.next()).proxy;
            if (p.compareTo(proxy) == 0)
                {return p;}
        }
        return null;
    }

    @Override
    public NodeProxy get(NodeProxy proxy) {
        for (final Iterator<IteratorItem> i = list.iterator(); i.hasNext();) {
            final NodeProxy p = (i.next()).proxy;
            if (p.compareTo(proxy) == 0)
                {return p;}
        }
        return null;
    }

    @Override
    public int getLength() {
        return list.size();
    }

    //TODO : evaluate both semantics (length/item count)
    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public Node item(int pos) {
        final NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
        return p == null ? null : p.getDocument().getNode(p);
    }

    //TODO : evaluate both semantics (item/itemAt)
    @Override
    public Item itemAt(int pos) {
        final NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
        return p == null ? null : p;
    }

    @Override
    public NodeSetIterator iterator() {
        return new SortedNodeSetIterator(list.iterator());
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#iterate()
     */
    @Override
    public SequenceIterator iterate() throws XPathException {
        return new SortedNodeSetIterator(list.iterator());
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
     */
    @Override
    public SequenceIterator unorderedIterator() throws XPathException {
        return new SortedNodeSetIterator(list.iterator());
    }

    private final static class SortedNodeSetIterator implements NodeSetIterator, SequenceIterator {

        Iterator<IteratorItem> pi;

        public SortedNodeSetIterator(Iterator<IteratorItem> i) {
            pi = i;
        }

        public boolean hasNext() {
            return pi.hasNext();
        }

        public NodeProxy next() {
            if (!pi.hasNext())
                {return null;}
            return (pi.next()).proxy;
        }

        public NodeProxy peekNode() {
            return null;
        }

        /* (non-Javadoc)
         * @see org.exist.xquery.value.SequenceIterator#nextItem()
         */
        public Item nextItem() {
            if (!pi.hasNext())
                {return null;}
            return ((IteratorItem) pi.next()).proxy;
        }

        public void remove() {
            //Nothing to do
        }

        public void setPosition(NodeProxy proxy) {
            throw new RuntimeException("NodeSetIterator.setPosition() is not supported by SortedNodeSetIterator");
        }
    }

    private static final class IteratorItem extends OrderedLinkedList.Node {

        NodeProxy proxy;
        String value = null;

        public IteratorItem(DBBroker broker, NodeProxy proxy, PathExpr expr, DocumentSet ndocs, 
                XQueryContext context) {
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
                for (final Iterator<OrderedLinkedList.SimpleNode> j = strings.iterator(); j.hasNext();) 
                    buf.append((j.next()).getData());
                value = buf.toString();
            } catch (final XPathException e) {
                LOG.warn(e.getMessage(), e); //TODO : throw exception ! -pb
            }
        }

        @Override
        public int compareTo(OrderedLinkedList.Node other) {
            final IteratorItem o = (IteratorItem) other;
            if (value == null)
                {return o.value == null ? Constants.EQUAL : Constants.SUPERIOR;}
            if (o.value == null)
                {return value == null ? Constants.EQUAL : Constants.INFERIOR;}
            return value.compareTo(o.value);
        }

        @Override
        public boolean equals(OrderedLinkedList.Node other) {
            final IteratorItem o = (IteratorItem) other;
            return value.equals(o.value);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
     */
    @Override
    public void add(NodeProxy proxy) {
        LOG.info("Called SortedNodeSet.add()");
    }

}
