package org.exist.xupdate;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.XMLUtil;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.store.StorageAddress;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
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
	protected Map namespaces;
	protected List activeLocks;

	/**
	 * Constructor for Modification.
	 */
	public Modification(DBBroker broker, DocumentSet docs, String selectStmt,
	        Map namespaces) {
		this.selectStmt = selectStmt;
		this.broker = broker;
		this.docs = docs;
		this.namespaces = new HashMap(namespaces);
		this.activeLocks = new ArrayList(docs.getLength());
	}

	/**
	 * Process the modification. This is the main method that has to be implemented 
	 * by all subclasses.
	 * 
	 * @return
	 * @throws PermissionDeniedException
	 * @throws LockException
	 * @throws EXistException
	 * @throws XPathException
	 */
	public abstract long process() throws PermissionDeniedException, LockException, 
		EXistException, XPathException;

	public abstract String getName();

	public void setContent(DocumentFragment node) {
		content = node;
	}

	/**
	 * Evaluate the select expression.
	 * 
	 * @param docs
	 * @return
	 * @throws PermissionDeniedException
	 * @throws EXistException
	 * @throws XPathException
	 */
	protected NodeList select(DocumentSet docs)
		throws PermissionDeniedException, EXistException, XPathException {
		try {
			XQueryContext context = new XQueryContext(broker);
			context.setStaticallyKnownDocuments(docs);
			Map.Entry entry;
			for (Iterator i = namespaces.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				context.declareNamespace(
					(String) entry.getKey(),
					(String) entry.getValue());
			}
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
			LOG.debug("found " + resultSeq.getLength() + " for select: " + selectStmt);
			return (NodeList)resultSeq;
		} catch (RecognitionException e) {
			LOG.warn("error while parsing select expression", e);
			throw new EXistException(e);
		} catch (TokenStreamException e) {
			LOG.warn("error while parsing select expression", e);
			throw new EXistException(e);
		}
	}

	/**
	 * Acquire a lock on all documents processed by this modification.
	 * We have to avoid that node positions change during the
	 * operation.
	 * 
	 * @param nl
	 * @return
	 * @throws LockException
	 */
	protected NodeImpl[] selectAndLock() throws LockException, PermissionDeniedException, 
		EXistException, XPathException {
	    Lock globalLock = broker.getBrokerPool().getGlobalUpdateLock();
	    try {
	        globalLock.acquire(Lock.WRITE_LOCK);
	        NodeList nl = select(docs);
	        
		    // acquire a lock on all documents
	        // we have to avoid that node positions change
	        // during the modification
		    NodeImpl ql[] = new NodeImpl[nl.getLength()];
		    DocumentImpl doc, prevDoc = null;
		    Lock lock;
			for (int i = 0; i < ql.length; i++) {
				ql[i] = (NodeImpl)nl.item(i);
				doc = (DocumentImpl)ql[i].getOwnerDocument();
				if(prevDoc == null || doc != prevDoc) {
				    lock = doc.getUpdateLock();
				    lock.acquire(Lock.WRITE_LOCK);
				    activeLocks.add(lock);
				    doc.setBroker(broker);
				}
				prevDoc = doc;
			}
			return ql;
	    } finally {
	        globalLock.release();
	    }
	}
	
	/**
	 * Release all acquired document locks.
	 */
	protected void unlockDocuments() {
	    Lock lock;
        for(int i = 0; i < activeLocks.size(); i++) {
            lock = (Lock)activeLocks.get(i);
            lock.release();
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
