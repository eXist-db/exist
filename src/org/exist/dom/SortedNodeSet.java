package org.exist.dom;

import java.io.StringReader;
import java.util.Iterator;
import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.OrderedLinkedList;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.Value;
import org.exist.xpath.ValueSet;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SortedNodeSet extends NodeSet {

	private static Category LOG =
		Category.getInstance(SortedNodeSet.class.getName());

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
		Item item;
		DocumentSet docs = new DocumentSet();
		for (Iterator i = other.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			docs.add(p.doc);
		}
		try {
			XPathLexer lexer = new XPathLexer(new StringReader(sortExpr));
			XPathParser parser =
				new XPathParser(pool, user, lexer);
			expr = new PathExpr(pool);
			parser.expr(expr);
			if (parser.foundErrors())
				LOG.debug(parser.getErrorMsg());
			ndocs = expr.preselect(docs);
		} catch (antlr.RecognitionException re) {
			LOG.debug(re);
		} catch (antlr.TokenStreamException tse) {
			LOG.debug(tse);
		} catch (PermissionDeniedException e) {
			LOG.debug(e);
		} catch (EXistException e) {
			LOG.debug(e);
		}
		StaticContext context = new StaticContext();
		DBBroker broker = null;
		try {
			broker = pool.get();
			for (Iterator i = other.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				item = new Item(broker, p, expr, ndocs, context);
				list.add(item);
			}
		} catch (EXistException e) {
			LOG.debug("exception during sort", e);
		} finally {
			pool.release(broker);
		}
		LOG.debug(
			"sort-expression found " + list.size() + " in "
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
			p = ((Item) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return true;
		}
		return false;
	}

	public NodeProxy get(int pos) {
		final Item item = (Item)list.get(pos);
		return item == null ? null : item.proxy; 
	}

	public NodeProxy get(DocumentImpl doc, long nodeId) {
		NodeProxy p;
		NodeProxy proxy = new NodeProxy(doc, nodeId);
		for (Iterator i = list.iterator(); i.hasNext();) {
			p = ((Item) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return p;
		}
		return null;
	}

	public NodeProxy get(NodeProxy proxy) {
		NodeProxy p;
		for (Iterator i = list.iterator(); i.hasNext();) {
			p = ((Item) i.next()).proxy;
			if (p.compareTo(proxy) == 0)
				return p;
		}
		return null;
	}

	public int getLength() {
		return list.size();
	}

	public Node item(int pos) {
		NodeProxy p = ((Item) list.get(pos)).proxy;
		return p == null ? null : p.doc.getNode(p);
	}

	public Iterator iterator() {
		return new SortedNodeSetIterator(list.iterator());
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
			return ((Item) pi.next()).proxy;
		}

		public void remove() {
		}
	}

	private static final class Item implements Comparable {
		NodeProxy proxy;
		String value = null;
		
		public Item(DBBroker broker, NodeProxy proxy, PathExpr expr, 
			DocumentSet ndocs, StaticContext context) {
			this.proxy = proxy;
			NodeSet contextSet = new SingleNodeSet(proxy);
			Value v = expr.eval(context, ndocs, contextSet, null);
			StringBuffer buf = new StringBuffer();
			OrderedLinkedList strings = new OrderedLinkedList();
			switch (v.getType()) {
				case Value.isNodeList :
					NodeSet resultSet = (NodeSet) v.getNodeList();
					if (resultSet.getLength() == 0)
						return;
					NodeProxy p;
					for (Iterator i = resultSet.iterator(); i.hasNext();) {
						p = (NodeProxy) i.next();
						strings.add(broker.getNodeValue(p).toUpperCase());
					}

					for (Iterator j = strings.iterator(); j.hasNext();)
						buf.append((String) j.next());
					value = buf.toString();
					break;
				default :
					ValueSet valueSet = v.getValueSet();
					if (valueSet.getLength() == 0)
						return;
					for (int k = 0; k < valueSet.getLength(); k++) {
						v = valueSet.get(k);
						strings.add(v.getStringValue().toUpperCase());
					}
					for (Iterator j = strings.iterator(); j.hasNext();)
						buf.append((String) j.next());
					value = buf.toString();
					break;
			}
		}

		public int compareTo(Object other) {
			Item o = (Item) other;
			if (value == null)
				return o.value == null ? 0 : 1;
			if (o.value == null)
				return value == null ? 0 : -1;
			return value.compareTo(o.value);
		}
	}
}
