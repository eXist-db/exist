package org.exist.xupdate;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.XMLUtil;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.store.StorageAddress;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.NodeList;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * Modification.java
 * 
 * @author Wolfgang Meier
 */
public abstract class Modification {

	protected final static Logger LOG = Logger.getLogger(Modification.class);

	protected String selectStmt = null;
	protected DocumentFragment content = null;
	protected DBBroker broker;
	protected DocumentSet docs;

	/**
	 * Constructor for Modification.
	 */
	public Modification(DBBroker broker, DocumentSet docs, String selectStmt) {
		this.selectStmt = selectStmt;
		this.broker = broker;
		this.docs = docs;
	}

	public abstract long process() throws PermissionDeniedException, EXistException, XPathException;

	public abstract String getName();

	public void setContent(DocumentFragment node) {
		content = node;
	}

	protected NodeImpl[] select(DocumentSet docs)
		throws PermissionDeniedException, EXistException, XPathException {
		try {
			XQueryContext context = new XQueryContext(broker);
			context.setStaticallyKnownDocuments(docs);
			XQueryLexer lexer = new XQueryLexer(new StringReader(selectStmt));
			XQueryParser parser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new RuntimeException(parser.getErrorMessage());
			}

			AST ast = parser.getAST();

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new RuntimeException(treeParser.getErrorMessage());
			}
			long start = System.currentTimeMillis();

			Sequence resultSeq = expr.eval(null, null);
			if (resultSeq.getItemType() != Type.NODE)
				throw new EXistException("select expression should evaluate to a" + "node-set");
			NodeList set = (NodeList)resultSeq;
			LOG.info("found " + set.getLength() + " for select: " + selectStmt + "; retrieving nodes...");
			ArrayList out = new ArrayList(set.getLength());
			for (int i = 0; i < set.getLength(); i++) {
				out.add(set.item(i));
			}
			NodeImpl result[] = new NodeImpl[out.size()];
			out.toArray(result);
			return result;
		} catch (RecognitionException e) {
			LOG.warn("error while parsing select expression", e);
			throw new EXistException(e);
		} catch (TokenStreamException e) {
			LOG.warn("error while parsing select expression", e);
			throw new EXistException(e);
		}
	}

	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("<xu:");
		buf.append(getName());
		buf.append(" select=\"");
		buf.append(selectStmt);
		buf.append("\">");
		buf.append(XMLUtil.dump(content));
		buf.append("</xu:");
		buf.append(getName());
		buf.append(">");
		return buf.toString();
	}

	final static class IndexListener implements NodeIndexListener {

		NodeImpl[] nodes;

		public IndexListener(NodeImpl[] nodes) {
			this.nodes = nodes;
		}

		/* (non-Javadoc)
		 * @see org.exist.dom.NodeIndexListener#nodeChanged(org.exist.dom.NodeImpl)
		 */
		public void nodeChanged(NodeImpl node) {
			final long address = node.getInternalAddress();
			for (int i = 0; i < nodes.length; i++) {
				if (StorageAddress.equals(nodes[i].getInternalAddress(), address)) {
					nodes[i] = node;
				}
			}
		}

		/* (non-Javadoc)
		 * @see org.exist.dom.NodeIndexListener#nodeChanged(long, long)
		 */
		public void nodeChanged(long oldAddress, long newAddress) {
			for (int i = 0; i < nodes.length; i++) {
				if (StorageAddress.equals(nodes[i].getInternalAddress(), oldAddress)) {
					nodes[i].setInternalAddress(newAddress);
				}
			}

		}

	}

	final static class NodeComparator implements Comparator {

		/* (non-Javadoc)
		* @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		*/
		public int compare(Object o1, Object o2) {
			NodeImpl n1 = (NodeImpl) o1;
			NodeImpl n2 = (NodeImpl) o2;
			if (n1.getInternalAddress() == n2.getInternalAddress())
				return 0;
			else if (n1.getInternalAddress() < n2.getInternalAddress())
				return -1;
			else
				return 1;
		}
	}
}
