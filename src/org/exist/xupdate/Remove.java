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
import org.w3c.dom.Node;

/**
 * Remove.java
 * 
 * @author Wolfgang Meier
 */
public class Remove extends Modification {

	private final static Logger LOG = Logger.getLogger(Remove.class);

	/**
	 * Constructor for Remove.
	 * @param pool
	 * @param user
	 * @param selectStmt
	 */
	public Remove(BrokerPool pool, User user, DocumentSet docs, String selectStmt) {
		super(pool, user, docs, selectStmt);
	}

	/**
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process()
		throws PermissionDeniedException, EXistException {
		ArrayList qr = select(docs);
		if(qr == null)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		Node parent;
		DocumentImpl doc = null;
		Collection collection = null, prevCollection = null;
		for (Iterator i = qr.iterator(); i.hasNext();) {
			node = (NodeImpl) i.next();
			doc = (DocumentImpl) node.getOwnerDocument();
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to remove document denied");
			collection = doc.getCollection();
			if(prevCollection != null && collection != prevCollection)
				doc.getBroker().saveCollection(prevCollection);
			doc.setIndexListener(listener);
			parent = node.getParentNode();
			if (parent.getNodeType() != Node.ELEMENT_NODE) {
				LOG.warn("cannot remove the root node");
			} else
				parent.removeChild(node);
			doc.clearIndexListener();
			prevCollection = collection;
		}
		if(doc != null)
			doc.getBroker().saveCollection(collection);
		return qr.size();
	}

	/**
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return "remove";
	}

}
