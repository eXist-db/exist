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
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.StorageAddress;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
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

	private final static Logger LOG = Logger.getLogger(Modification.class);

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
			StaticContext context = new StaticContext(broker);
			XPathLexer2 lexer = new XPathLexer2(new StringReader(selectStmt));
			XPathParser2 parser = new XPathParser2(lexer);
			XPathTreeParser2 treeParser = new XPathTreeParser2(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new RuntimeException(parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new RuntimeException(treeParser.getErrorMessage());
			}
			LOG.info("modification select: " + expr.pprint());
			long start = System.currentTimeMillis();
			docs = expr.preselect(docs);
			if (docs.getLength() == 0)
				return null;

			Sequence resultSeq = expr.eval(docs, null, null);
			if (resultSeq.getItemType() != Type.NODE)
				throw new EXistException("select expression should evaluate to a" + "node-set");
			NodeList set = (NodeList)resultSeq;
			LOG.info("found " + set.getLength() + " for select; retrieving nodes...");
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
				if (StorageAddress.equals(nodes[i].getInternalAddress(), address))
					nodes[i] = node;
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
