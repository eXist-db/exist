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
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.NodeImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.StoredNode;
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
            Map<String, String> namespaces, Map<String, Object> variables) {
        super(broker, docs, selectStmt, namespaces, variables);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#process(org.exist.dom.persistent.DocumentSet)
     */
    public long process(Txn transaction) throws PermissionDeniedException, LockException,
            EXistException, XPathException, TriggerException {
        final NodeList children = content;
        if (children.getLength() == 0) {return 0;}
        int modificationCount = 0;
        try {
            final StoredNode[] ql = selectAndLock(transaction);
            NodeImpl parent;
            final NotificationService notifier = broker.getBrokerPool().getNotificationService();
            final String newName = children.item(0).getNodeValue();
            for (int i = 0; i < ql.length; i++) {
                final StoredNode node = ql[i];
                final DocumentImpl doc = node.getOwnerDocument();
                if (!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("User '" + broker.getCurrentSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                }
                parent = (NodeImpl) node.getParentNode();
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        final ElementImpl newElem = new ElementImpl((ElementImpl) node);
                        newElem.setNodeName(new QName(newName, "", null));
                        parent.updateChild(transaction, node, newElem);
                        modificationCount++;
                        break;
                    case Node.ATTRIBUTE_NODE:
                        final AttrImpl newAttr = new AttrImpl((AttrImpl) node);
                        newAttr.setNodeName(new QName(newName, "", null));
                        parent.updateChild(transaction, node, newAttr);
                        modificationCount++;
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }

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