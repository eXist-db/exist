package org.exist.xupdate;

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
import org.w3c.dom.NodeList;

/**
 * Append.java
 * 
 * @author Wolfgang Meier
 */
public class Append extends Modification {

	/**
	 * Constructor for Append.
	 * @param selectStmt
	 */
	public Append(BrokerPool pool, User user, DocumentSet docs, String selectStmt) {
		super(pool, user, docs, selectStmt);
	}
	/**
	 * @see org.exist.xupdate.Modification#process()
	 */
	public long process() throws PermissionDeniedException, EXistException, XPathException {
		NodeImpl[] qr = select(docs);
		NodeList children = content.getChildNodes();
		if (qr == null || children.getLength() == 0)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		DocumentImpl doc = null;
		Collection collection = null, prevCollection = null;
		int len = children.getLength();
		for(int i = 0; i < qr.length; i++) {
			node = qr[i];
			doc = (DocumentImpl) node.getOwnerDocument();
			doc.setIndexListener(listener);
			collection = doc.getCollection();
			if (prevCollection != null && collection != prevCollection)
				doc.getBroker().saveCollection(prevCollection);
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to update document denied");
			node.appendChildren(children);
			doc.clearIndexListener();
			doc.setLastModified(System.currentTimeMillis());
			prevCollection = collection;
		}
		if (doc != null)
			doc.getBroker().saveCollection(collection);
		return qr.length;
	}

	public String getName() {
		return "append";
	}
}
