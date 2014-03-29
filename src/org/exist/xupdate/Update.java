/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.xupdate;

import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.*;
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
 * Implements the XUpdate update modification.
 * 
 * @author wolf
 */
public class Update extends Modification {

    /**
     * @param broker 
     * @param docs 
     * @param namespaces 
     * @param variables 
     * @param selectStmt 
     */
    public Update(DBBroker broker, DocumentSet docs, String selectStmt,
            Map<String, String> namespaces, Map<String, Object> variables) {
        super(broker, docs, selectStmt, namespaces, variables);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
     */
    public long process(Txn transaction) throws PermissionDeniedException, LockException,
            EXistException, XPathException, TriggerException {
        final NodeList children = content;
        if (children.getLength() == 0) 
            {return 0;}
        int modifications = children.getLength();
        try {
            final StoredNode ql[] = selectAndLock(transaction);
            final IndexListener listener = new IndexListener(ql);
            final NotificationService notifier = broker.getBrokerPool().getNotificationService();
            Node temp;
            TextImpl text;
            AttrImpl attribute;
            ElementImpl parent;
            for (int i = 0; i < ql.length; i++) {
            	final StoredNode node = ql[i];
                if (node == null) {
                    LOG.warn("select " + selectStmt + " returned empty node");
                    continue;
                }
                final DocumentImpl  doc = (DocumentImpl)node.getOwnerDocument();
                doc.getMetadata().setIndexListener(listener);
                if (!doc.getPermissions().validate(broker.getSubject(), Permission.WRITE)) {
                     throw new PermissionDeniedException("User '" + broker.getSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                }
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        if (modifications == 0) {modifications = 1;}
                        ((ElementImpl) node).update(transaction, children);
                        break;
                    case Node.TEXT_NODE:
                        parent = (ElementImpl) node.getParentNode();
                    	temp = children.item(0);
                    	text = new TextImpl(temp.getNodeValue());
                        modifications = 1;
                        text.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, text);
                        break;
                    case Node.ATTRIBUTE_NODE:
                        parent = (ElementImpl) node.getParentNode();
                        if (parent == null) {
                            LOG.warn("parent node not found for "
                                    + node.getNodeId());
                            break;
                        }
                        final AttrImpl attr = (AttrImpl) node;
                        temp = children.item(0);
                        attribute = new AttrImpl(attr.getQName(), temp.getNodeValue(), broker.getBrokerPool().getSymbols());
                        attribute.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, attribute);
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
        return modifications;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#getName()
     */
    public String getName() {
        return XUpdateProcessor.UPDATE;
    }

}