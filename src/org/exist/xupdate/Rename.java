/*
 * Rename.java - May 1, 2003
 * 
 * @author wolf
 */
package org.exist.xupdate;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Rename extends Modification {

	/**
	 * @param pool
	 * @param user
	 * @param selectStmt
	 */
	public Rename(DBBroker broker, DocumentSet docs, String selectStmt) {
		super(broker, docs, selectStmt);
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process() throws PermissionDeniedException, EXistException, XPathException {
		NodeImpl qr[] = select(docs);
		NodeList children = content.getChildNodes();
		if (qr == null || children.getLength() == 0)
			return 0;
		DocumentImpl doc = null;
		Collection collection = null, prevCollection = null;
		NodeImpl node;
		NodeImpl parent;
		IndexListener listener = new IndexListener(qr);
		String newName = children.item(0).getNodeValue();
		int modificationCount = 0;
		for (int i = 0; i < qr.length; i++) {
			node = qr[i];
			doc = (DocumentImpl) node.getOwnerDocument();
			collection = doc.getCollection();
			if (prevCollection != null && collection != prevCollection)
				doc.getBroker().saveCollection(prevCollection);
			if (!collection.getPermissions().validate(broker.getUser(), Permission.UPDATE))
				throw new PermissionDeniedException(
					"write access to collection denied; user=" + broker.getUser().getName());
			if (!doc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
				throw new PermissionDeniedException("permission denied to update document");
			doc.setIndexListener(listener);
			parent = (NodeImpl)node.getParentNode();
			switch(node.getNodeType()) {
				case Node.ELEMENT_NODE :
					((ElementImpl)node).setNodeName(new QName(newName, "", null));
					parent.updateChild(node, node);
					modificationCount++;
					break;
				case Node.ATTRIBUTE_NODE :
					((AttrImpl)node).setNodeName(new QName(newName, "", null));
					parent.updateChild(node, node);
					modificationCount++;
					break;
				default :
					throw new EXistException("unsupported node-type");
			}
			prevCollection = collection;
			doc.clearIndexListener();
			doc.setLastModified(System.currentTimeMillis());
		}
		if (doc != null)
			doc.getBroker().saveCollection(collection);
		return modificationCount;
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return "rename";
	}

}
