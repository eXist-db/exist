package org.exist.xupdate;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.XMLUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Insert.java
 * 
 * @author Wolfgang Meier
 */
public class Insert extends Modification {

	public final static int INSERT_BEFORE = 0;
	public final static int INSERT_AFTER = 1;

    private final static Logger LOG = Logger.getLogger(Insert.class);
     
	private int mode = INSERT_BEFORE;

	/**
	 * Constructor for Insert.
	 * @param pool
	 * @param user
	 * @param selectStmt
	 */
	public Insert(BrokerPool pool, User user, String selectStmt) {
		super(pool, user, selectStmt);
	}

	public Insert(BrokerPool pool, User user, String selectStmt, int mode) {
		this(pool, user, selectStmt);
		this.mode = mode;
	}

	/**
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process(DocumentSet docs)
		throws PermissionDeniedException, EXistException {
		System.out.println(XMLUtil.dump(content));
		NodeSet qr = select(docs);
		LOG.debug("select found " + qr.getLength() + " nodes for insert");
		NodeProxy proxy;
		Node node, parent;
		NodeList children = content.getChildNodes();
		int len = children.getLength();
		LOG.debug("found " + len + " nodes to append");
		for (Iterator i = qr.iterator(); i.hasNext();) {
			proxy = (NodeProxy) i.next();
			node = proxy.getNode();
            parent = node.getParentNode();
			for (int j = 0; j < len; j++) {
				parent.insertBefore(children.item(j), node);
			}
		}
		return qr.getLength();
	}

	/**
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return null;
	}

}
