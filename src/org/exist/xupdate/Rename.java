/*
 * eXist Open Source Native XML Database Copyright (C) 2001-04 Wolfgang M. Meier
 * wolfgang@exist-db.org http://exist.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.xupdate;

import java.util.Map;

import org.exist.EXistException;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NotificationService;
import org.exist.storage.UpdateListener;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Implements an XUpdate rename operation.
 * 
 * @author wolf
 */
public class Rename extends Modification {

    /**
     * @param broker 
     * @param docs 
     * @param namespaces 
     * @param variables 
     * @param selectStmt 
     */
    public Rename(DBBroker broker, DocumentSet docs, String selectStmt,
            Map namespaces, Map variables) {
        super(broker, docs, selectStmt, namespaces, variables);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
     */
    public long process(Txn transaction) throws PermissionDeniedException, LockException,
            EXistException, XPathException {
        NodeList children = content;
        if (children.getLength() == 0) return 0;
        int modificationCount = 0;
        try {
            StoredNode[] ql = selectAndLock(transaction);
            NodeImpl parent;
            IndexListener listener = new IndexListener(ql);
            NotificationService notifier = broker.getBrokerPool().getNotificationService();
            String newName = children.item(0).getNodeValue();
            for (int i = 0; i < ql.length; i++) {
                StoredNode node = ql[i];
                DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
                if (!doc.getPermissions().validate(broker.getUser(),
                        Permission.UPDATE))
                        throw new PermissionDeniedException(
                        "permission to update document denied");
                doc.getMetadata().setIndexListener(listener);
                parent = (NodeImpl) node.getParentNode();
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        ElementImpl newElem = new ElementImpl((ElementImpl) node);
                        newElem.setNodeName(new QName(newName, "", null));
                        parent.updateChild(transaction, node, newElem);
                        modificationCount++;
                        break;
                    case Node.ATTRIBUTE_NODE:
                        AttrImpl newAttr = new AttrImpl((AttrImpl) node);
                        newAttr.setNodeName(new QName(newName, "", null));
                        parent.updateChild(transaction, node, newAttr);
                        modificationCount++;
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }

                doc.getMetadata().clearIndexListener();
                doc.getMetadata().setLastModified(System.currentTimeMillis());
                modifiedDocuments.add(doc);
                broker.storeXMLResource(transaction, doc);
                notifier.notifyUpdate(doc, UpdateListener.UPDATE);
            }
            checkFragmentation(transaction, modifiedDocuments);
        } finally {
            unlockDocuments(transaction);
        }
        return modificationCount;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#getName()
     */
    public String getName() {
        return "rename";
    }

}