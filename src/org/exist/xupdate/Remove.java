package org.exist.xupdate;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.xpath.XPathException;
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
		throws PermissionDeniedException, EXistException, XPathException {
		NodeImpl[] qr = select(docs);
		if(qr == null)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		Node parent;
		DocumentImpl doc = null;
		Collection collection = null, prevCollection = null;
		for(int i = 0; i < qr.length; i++) {
			node = qr[i];
			doc = (DocumentImpl) node.getOwnerDocument();
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to remove document denied");
			collection = doc.getCollection();
			if(prevCollection != null && collection != prevCollection)
				doc.getBroker().saveCollection(prevCollection);
			doc.setIndexListener(listener);
			parent = node.getParentNode();
			if (parent.getNodeType() != Node.ELEMENT_NODE) {
				throw new EXistException("you cannot remove the document element. Use update " +
					"instead");
			} else
				parent.removeChild(node);
			doc.clearIndexListener();
			doc.setLastModified(System.currentTimeMillis());
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
		return "remove";
	}

}
