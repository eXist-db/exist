package org.exist.xupdate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

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
import org.exist.util.StorageAddress;
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
	public Insert(BrokerPool pool, User user, DocumentSet docs, String selectStmt) {
		super(pool, user, docs, selectStmt);
	}

	public Insert(BrokerPool pool, User user, DocumentSet docs, 
		String selectStmt, int mode) {
		this(pool, user, docs, selectStmt);
		this.mode = mode;
	}

	/**
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process()
		throws PermissionDeniedException, EXistException {
		NodeImpl[] qr = select(docs);
		NodeList children = content.getChildNodes();
		if(qr == null || children.getLength() == 0)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		NodeImpl parent;
		DocumentImpl doc = null;
		Collection collection = null, prevCollection = null;
		int len = children.getLength();
		LOG.debug("found " + len + " nodes to insert");
		for(int i = 0; i < qr.length; i++) {
			node = qr[i];
			doc = (DocumentImpl) node.getOwnerDocument();
			doc.setIndexListener(listener);
			collection = doc.getCollection();
			if(prevCollection != null && collection != prevCollection)
				doc.getBroker().saveCollection(prevCollection);
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to remove document denied");
			LOG.debug("processing " + node.getGID() + " [" + 
				StorageAddress.toString(node.getInternalAddress()) + "]");
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
			prevCollection = collection;
		}
		if(doc != null)
			doc.getBroker().saveCollection(collection);
		return qr.length;
	}

	/**
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return (mode == INSERT_BEFORE ? "insert-before" : "insert-after");
	}

}
