package org.exist.dom;

import java.io.StringReader;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.security.User;
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
	private User user = null;
	private AccessContext accessCtx;

	private SortedNodeSet() {}
	public SortedNodeSet(BrokerPool pool, User user, String sortExpr, AccessContext accessCtx) {
		this.sortExpr = sortExpr;
		this.pool = pool;
		this.user = user;
		if(accessCtx == null)
			throw new NullAccessContextException();
		this.accessCtx = accessCtx;
	}
	
    public boolean isEmpty() {
    	return list.size() == 0;
    }

    public boolean hasOne() {
    	return list.size() == 1;
    }    

	public void addAll(Sequence other) throws XPathException {
		addAll(other.toNodeSet());
	}
	
	public void addAll(NodeSet other) {
		long start = System.currentTimeMillis();		
		MutableDocumentSet docs = new DefaultDocumentSet();
		for (Iterator i = other.iterator(); i.hasNext();) {
            NodeProxy p = (NodeProxy)i.next();
			docs.add(p.getDocument());
		}
		// TODO(pkaminsk2): why replicate XQuery.compile here?
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			XQueryContext context = new XQueryContext(broker, accessCtx);
			XQueryLexer lexer = new XQueryLexer(context, new StringReader(sortExpr));
			XQueryParser parser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
                //TODO : error ?
				LOG.debug(parser.getErrorMessage());
			}
			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());
			expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				LOG.debug(treeParser.getErrorMessage());
			}
			expr.analyze(new AnalyzeContextInfo());
			for (SequenceIterator i = other.iterate(); i.hasNext();) {
                NodeProxy p = (NodeProxy) i.nextItem();
                IteratorItem item = new IteratorItem(broker, p, expr, docs, context);
				list.add(item);
			}
		} catch (antlr.RecognitionException re) {
			LOG.debug(re);
		} catch (antlr.TokenStreamException tse) {
			LOG.debug(tse);
		} catch (EXistException e) {
			LOG.debug("Exception during sort", e);
		} catch (XPathException e) {
			LOG.debug("Exception during sort", e);
		} finally {
			pool.release(broker);
		}
		LOG.debug(
			"sort-expression found "
				+ list.size()
				+ " in "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
	}

	public void addAll(NodeList other) {
		if (!(other instanceof NodeSet))
			throw new RuntimeException("not implemented!");
		addAll((NodeSet) other);
	}

	public boolean contains(NodeProxy proxy) {		
		for (Iterator i = list.iterator(); i.hasNext();) {
            NodeProxy p = ((IteratorItem) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return true;
		}
		return false;
	}

	public NodeProxy get(int pos) {
		final IteratorItem item = (IteratorItem) list.get(pos);
		return item == null ? null : item.proxy;
	}

    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        NodeProxy proxy = new NodeProxy(doc, nodeId);
		for (Iterator i = list.iterator(); i.hasNext();) {
            NodeProxy p = ((IteratorItem) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return p;
		}
		return null;
    }

    public NodeProxy get(NodeProxy proxy) {
		for (Iterator i = list.iterator(); i.hasNext();) {
            NodeProxy p = ((IteratorItem) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return p;
		}
		return null;
	}

	public int getLength() {
		return list.size();
	}

    //TODO : evaluate both semantics
	public int getItemCount() {
		return list.size();
	}	

	public Node item(int pos) {
		NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
		return p == null ? null : p.getDocument().getNode(p);
	}

	public Item itemAt(int pos) {
		NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
		return p == null ? null : p;
	}
	
	public NodeSetIterator iterator() {
		return new SortedNodeSetIterator(list.iterator());
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() throws XPathException {
		return new SortedNodeSetIterator(list.iterator());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() throws XPathException {
		return new SortedNodeSetIterator(list.iterator());
	}
	
	private final static class SortedNodeSetIterator implements NodeSetIterator, SequenceIterator {

		Iterator pi;

		public SortedNodeSetIterator(Iterator i) {
			pi = i;
		}

		public boolean hasNext() {
			return pi.hasNext();
		}

		public Object next() {
			if (!pi.hasNext())
				return null;
			return ((IteratorItem) pi.next()).proxy;
		}

        public NodeProxy peekNode() {
            return null;
        }
        
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if (!pi.hasNext())
				return null;
			return ((IteratorItem) pi.next()).proxy;
		}
		
		public void remove() {
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
				Sequence seq = expr.eval(proxy);
				StringBuilder buf = new StringBuilder();
				OrderedLinkedList strings = new OrderedLinkedList();
				Item item;
				for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
					item = i.nextItem();
					strings.add(new OrderedLinkedList.SimpleNode(item.getStringValue().toUpperCase()));
				}
				for (Iterator j = strings.iterator(); j.hasNext();) 
					buf.append(((OrderedLinkedList.SimpleNode) j.next()).getData());
				value = buf.toString();
			} catch (XPathException e) {
				LOG.warn(e.getMessage(), e);
			}
		}

		public int compareTo(OrderedLinkedList.Node other) {
			IteratorItem o = (IteratorItem) other;
			if (value == null)
				return o.value == null ? Constants.EQUAL : Constants.SUPERIOR;
			if (o.value == null)
				return value == null ? Constants.EQUAL : Constants.INFERIOR;
			return value.compareTo(o.value);
		}

		public boolean equals(OrderedLinkedList.Node other) {
			IteratorItem o = (IteratorItem) other;
			return value.equals(o.value);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
	 */
	public void add(NodeProxy proxy) {
        LOG.info("Called SortedNodeSet.add()");
	}

}
