package org.exist.xupdate;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.StorageAddress;
import org.exist.util.XMLUtil;
import org.exist.xpath.PathExpr;
import org.exist.xpath.RootNode;
import org.exist.xpath.Value;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.NodeList;

import antlr.RecognitionException;
import antlr.TokenStreamException;

/**
 * Modification.java
 * 
 * @author Wolfgang Meier
 */
public abstract class Modification {

    private final static Logger LOG = Logger.getLogger(Modification.class);
    
	protected String selectStmt = null;
	protected DocumentFragment content = null;
	protected BrokerPool pool;
    protected User user;
    protected DocumentSet docs;
    
	/**
	 * Constructor for Modification.
	 */
	public Modification(BrokerPool pool, User user, DocumentSet docs, String selectStmt) {
		this.selectStmt = selectStmt;
        this.pool = pool;
        this.user = user;
        this.docs = docs;
	}

	public abstract long process() throws PermissionDeniedException, EXistException;

	public abstract String getName();

	public void setContent(DocumentFragment node) {
		content = node;
	}

	protected NodeImpl[] select(DocumentSet docs) throws PermissionDeniedException, EXistException {
		try {
			XPathLexer lexer = new XPathLexer(new StringReader(selectStmt));
			XPathParser parser = new XPathParser(pool, user, lexer);
			PathExpr expr = new PathExpr(pool);
            RootNode root = new RootNode(pool);
            expr.add(root);
			parser.expr(expr);
			LOG.info("modification select: " + expr.pprint());
			long start = System.currentTimeMillis();
			if (parser.foundErrors())
				throw new RuntimeException(parser.getErrorMsg());
			docs = expr.preselect(docs);
			if (docs.getLength() == 0)
				return null;
			
			Value resultValue = expr.eval(docs, null, null);
            if(!(resultValue.getType() == Value.isNodeList))
                throw new EXistException("select expression should evaluate to a" +
                    "node-set");
            NodeList set = resultValue.getNodeList();
			LOG.info("found " + set.getLength() + " for select; retrieving nodes...");
            ArrayList out = new ArrayList(set.getLength());
            for(int i = 0; i < set.getLength(); i++) {
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
			for(int i = 0; i < nodes.length; i++) {
				if(StorageAddress.equals(nodes[i].getInternalAddress(), address))
					nodes[i] = node;
			}
		}
	
		/* (non-Javadoc)
		 * @see org.exist.dom.NodeIndexListener#nodeChanged(long, long)
		 */
		public void nodeChanged(long oldAddress, long newAddress) {
			for(int i = 0; i < nodes.length; i++) {
				if(StorageAddress.equals(nodes[i].getInternalAddress(), oldAddress)) {
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
			NodeImpl n1 = (NodeImpl)o1;
			NodeImpl n2 = (NodeImpl)o2;
			if(n1.getInternalAddress() == n2.getInternalAddress())
				return 0;
			else if(n1.getInternalAddress() < n2.getInternalAddress())
				return -1;
			else
				return 1;
		}
	}
}
