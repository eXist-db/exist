/*
 * Update.java - Apr 29, 2003
 * 
 * @author wolf
 */
package org.exist.xupdate;

import java.util.ArrayList;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.AttrImpl;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Update extends Modification {

	/**
	 * @param pool
	 * @param user
	 * @param selectStmt
	 */
	public Update(BrokerPool pool, User user, DocumentSet docs, String selectStmt) {
		super(pool, user, docs, selectStmt);
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process() throws PermissionDeniedException, EXistException {
		ArrayList qr = select(docs);
		NodeList children = content.getChildNodes();
		if (qr == null || children.getLength() == 0)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		Node temp;
		TextImpl text;
		AttrImpl attribute;
		ElementImpl parent;
		DocumentImpl doc = null;
		Collection collection = null;
		for (Iterator i = qr.iterator(); i.hasNext();) {
			node = (NodeImpl) i.next();
			doc = (DocumentImpl) node.getOwnerDocument();
			doc.setIndexListener(listener);
			collection = doc.getCollection();
			if (!collection.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"write access to collection denied; user=" + user.getName());
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission denied to update document");
			switch (node.getNodeType()) {
				case Node.ELEMENT_NODE :
					((ElementImpl) node).update(children);
					break;
				case Node.TEXT_NODE :
					parent = (ElementImpl)node.getParentNode();
					temp = children.item(0);
					text = new TextImpl(temp.getNodeValue());
					text.setOwnerDocument(doc);
					parent.updateChild(node, text);
					break;
				case Node.ATTRIBUTE_NODE :
					parent = (ElementImpl)node.getParentNode();
					temp = children.item(0);
					attribute = 
						new AttrImpl(((AttrImpl)node).getName(), temp.getNodeValue());
					attribute.setOwnerDocument(doc);
					parent.updateChild(node, attribute);
					break;
				default :
					throw new EXistException("unsupported node-type");
			}
		}
		return children.getLength();
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return "update";
	}

}
