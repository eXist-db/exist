package org.exist.xupdate;

import java.util.ArrayList;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
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
	public long process()
		throws PermissionDeniedException, EXistException {
		ArrayList qr = select(docs);
		NodeList children = content.getChildNodes();
		if (qr == null || children.getLength() == 0)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		DocumentImpl doc, prevDoc = null;
		int len = children.getLength();
		for (Iterator i = qr.iterator(); i.hasNext();) {
			node = (NodeImpl) i.next();
			doc = (DocumentImpl)node.getOwnerDocument();
			doc.setIndexListener(listener);
			if (!doc.getCollection().getPermissions()
				.validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"write access to collection denied; user="
						+ user.getName());
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to remove document denied");
			node.appendChildren(children);
			doc.clearIndexListener();
			prevDoc = doc;
		}
		return qr.size();
	}

	public String getName() {
		return "append";
	}
}
