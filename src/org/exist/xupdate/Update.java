/*
 * Update.java - Apr 29, 2003
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
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Update extends Modification {

	/**
	 * @param pool
	 * @param user
	 * @param selectStmt
	 */
	public Update(DBBroker broker, DocumentSet docs, String selectStmt) {
		super(broker, docs, selectStmt);
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process() throws PermissionDeniedException, EXistException, XPathException {
		NodeImpl[] qr = select(docs);
		NodeList children = content.getChildNodes();
		if (qr == null)
			return 0;
		IndexListener listener = new IndexListener(qr);
		NodeImpl node;
		Node temp;
		TextImpl text;
		AttrImpl attribute;
		ElementImpl parent;
		DocumentImpl doc = null;
		Collection collection = null, prevCollection = null;
		int result = children.getLength();
		for (int i = 0; i < qr.length; i++) {
			node = qr[i];
			if(node == null) {
				LOG.warn("select " + selectStmt + " returned empty node");
				continue;
			}
			doc = (DocumentImpl) node.getOwnerDocument();
			doc.setIndexListener(listener);
			collection = doc.getCollection();
			if (!doc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
				throw new PermissionDeniedException("permission to update document denied");
			if(prevCollection != null && collection != prevCollection)
				doc.getBroker().saveCollection(prevCollection);
			switch (node.getNodeType()) {
				case Node.ELEMENT_NODE :
					if (result == 0) result = 1;
					((ElementImpl) node).update(children);
					break;
				case Node.TEXT_NODE :
					parent = (ElementImpl)node.getParentNode();
					if (children.getLength() != 0) {
						temp = children.item(0);
						text = new TextImpl(temp.getNodeValue());
					} else {
						result = 1;
						text = new TextImpl("");
					}
					text.setOwnerDocument(doc);
					parent.updateChild(node, text);
					break;
				case Node.ATTRIBUTE_NODE :
					parent = (ElementImpl)node.getParentNode();
					AttrImpl attr = (AttrImpl)node;
					if (children.getLength() != 0) {
						temp = children.item(0);
						attribute = new AttrImpl(attr.getQName(), temp.getNodeValue());
					} else {
						result = 1;
						attribute = new AttrImpl(attr.getQName(), "");
					}
					attribute.setOwnerDocument(doc);
					parent.updateChild(node, attribute);
					break;
				default :
					throw new EXistException("unsupported node-type");
			}
			prevCollection = collection;
			doc.setLastModified(System.currentTimeMillis());
		}
		if(doc != null)
			doc.getBroker().saveCollection(collection);
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return "update";
	}

}
