package org.exist.dom;

import java.io.StringReader;
import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.OrderedLinkedList;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import antlr.collections.AST;

public class SortedNodeSet extends NodeSet {

	private static Category LOG = Category.getInstance(SortedNodeSet.class.getName());

	private PathExpr expr;
	private OrderedLinkedList list = new OrderedLinkedList();
	private DocumentSet ndocs;
	private String sortExpr;
	private BrokerPool pool;
	private User user = null;

	public SortedNodeSet(BrokerPool pool, User user, String sortExpr) {
		this.sortExpr = sortExpr;
		this.pool = pool;
		this.user = user;
	}

	public void addAll(NodeSet other) {
		long start = System.currentTimeMillis();
		NodeProxy p;
		IteratorItem item;
		DocumentSet docs = new DocumentSet();
		for (Iterator i = other.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			docs.add(p.doc);
		}
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			StaticContext context = new StaticContext(broker);
			XPathLexer2 lexer = new XPathLexer2(new StringReader(sortExpr));
			XPathParser2 parser = new XPathParser2(lexer);
			XPathTreeParser2 treeParser = new XPathTreeParser2(context);
			parser.xpath();
			if (parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());

			expr = new PathExpr();
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				LOG.debug(treeParser.getErrorMessage());
			}
			ndocs = expr.preselect(docs, context);
			for (Iterator i = other.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				item = new IteratorItem(broker, p, expr, ndocs, context);
				list.add(item);
			}
		} catch (antlr.RecognitionException re) {
			LOG.debug(re);
		} catch (antlr.TokenStreamException tse) {
			LOG.debug(tse);
		} catch (XPathException e) {
			LOG.debug(e.getMessage(), e);
		} catch (EXistException e) {
			LOG.debug("exception during sort", e);
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

	public boolean contains(DocumentImpl doc, long nodeId) {
		return contains(new NodeProxy(doc, nodeId));
	}

	public boolean contains(NodeProxy proxy) {
		NodeProxy p;
		for (Iterator i = list.iterator(); i.hasNext();) {
			p = ((IteratorItem) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return true;
		}
		return false;
	}

	public NodeProxy get(int pos) {
		final IteratorItem item = (IteratorItem) list.get(pos);
		return item == null ? null : item.proxy;
	}

	public NodeProxy get(DocumentImpl doc, long nodeId) {
		NodeProxy p;
		NodeProxy proxy = new NodeProxy(doc, nodeId);
		for (Iterator i = list.iterator(); i.hasNext();) {
			p = ((IteratorItem) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return p;
		}
		return null;
	}

	public NodeProxy get(NodeProxy proxy) {
		NodeProxy p;
		for (Iterator i = list.iterator(); i.hasNext();) {
			p = ((IteratorItem) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return p;
		}
		return null;
	}

	public int getLength() {
		return list.size();
	}

	public Node item(int pos) {
		NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
		return p == null ? null : p.doc.getNode(p);
	}

	public Item itemAt(int pos) {
		NodeProxy p = ((IteratorItem) list.get(pos)).proxy;
		return p == null ? null : p;
	}
	
	public Iterator iterator() {
		return new SortedNodeSetIterator(list.iterator());
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		return new SortedNodeSequenceIterator(list.iterator());
	}
	
	private final static class SortedNodeSetIterator implements Iterator {

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

		public void remove() {
		}
	}

	private final static class SortedNodeSequenceIterator implements SequenceIterator {
		
		Iterator iter;
		
		public SortedNodeSequenceIterator(Iterator iterator) {
			iter = iterator;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if(!iter.hasNext())
				return null;
			return ((IteratorItem) iter.next()).proxy;
		}
	}
	
	private static final class IteratorItem implements Comparable {
		NodeProxy proxy;
		String value = null;

		public IteratorItem(
			DBBroker broker,
			NodeProxy proxy,
			PathExpr expr,
			DocumentSet ndocs,
			StaticContext context) {
			this.proxy = proxy;
			try {
				Sequence seq = expr.eval(context, ndocs, proxy, null);
				StringBuffer buf = new StringBuffer();
				OrderedLinkedList strings = new OrderedLinkedList();
				Item item;
				for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
					item = i.nextItem();
					strings.add(item.getStringValue().toUpperCase());
				}
				for (Iterator j = strings.iterator(); j.hasNext();) 
					buf.append((String) j.next());
				value = buf.toString();
			} catch (XPathException e) {
				LOG.warn(e.getMessage(), e);
			}
		}

		public int compareTo(Object other) {
			IteratorItem o = (IteratorItem) other;
			if (value == null)
				return o.value == null ? 0 : 1;
			if (o.value == null)
				return value == null ? 0 : -1;
			return value.compareTo(o.value);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
	 */
	public void add(NodeProxy proxy) {
	}
}
