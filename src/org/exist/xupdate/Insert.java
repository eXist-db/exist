package org.exist.xupdate;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.XMLUtil;
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
		ArrayList qr = select(docs);
		LOG.debug("select found " + qr.size() + " nodes for insert");
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		NodeImpl parent;
		DocumentImpl doc = null;
		Collection collection = null;
		NodeList children = content.getChildNodes();
		int len = children.getLength();
		LOG.debug("found " + len + " nodes to insert");
		for (Iterator i = qr.iterator(); i.hasNext();) {
			node = (NodeImpl) i.next();
			doc = (DocumentImpl) node.getOwnerDocument();
			doc.setIndexListener(listener);
			collection = doc.getCollection();
			if (!collection
				.getPermissions()
				.validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"write access to collection denied; user="
						+ user.getName());
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to remove document denied");
			parent = (NodeImpl) node.getParentNode();
			switch (mode) {
				case INSERT_BEFORE :
					parent.insertBefore(children, node);
					break;
				case INSERT_AFTER :
					 ((NodeImpl) parent).insertAfter(children, node);
					break;
			}
			doc.clearIndexListener();
		}
		return qr.size();
	}

	/**
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return null;
	}

}
