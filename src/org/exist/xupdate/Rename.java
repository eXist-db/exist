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
     * @param pool
     * @param user
     * @param selectStmt
     */
    public Rename(DBBroker broker, DocumentSet docs, String selectStmt,
            Map namespaces) {
        super(broker, docs, selectStmt, namespaces);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
     */
    public long process() throws PermissionDeniedException, LockException,
            EXistException, XPathException {
        NodeList children = content.getChildNodes();
        if (children.getLength() == 0) return 0;
        int modificationCount = 0;
        try {
            NodeImpl[] ql = selectAndLock();
            DocumentImpl doc = null;
            Collection collection = null, prevCollection = null;
            NodeImpl node;
            NodeImpl parent;
            IndexListener listener = new IndexListener(ql);
            String newName = children.item(0).getNodeValue();
            for (int i = 0; i < ql.length; i++) {
                node = ql[i];
                doc = (DocumentImpl) node.getOwnerDocument();
                collection = doc.getCollection();
                if (prevCollection != null && collection != prevCollection)
                        doc.getBroker().saveCollection(prevCollection);
                if (!collection.getPermissions().validate(broker.getUser(),
                        Permission.UPDATE))
                        throw new PermissionDeniedException(
                                "write access to collection denied; user="
                                        + broker.getUser().getName());
                if (!doc.getPermissions().validate(broker.getUser(),
                        Permission.UPDATE))
                        throw new PermissionDeniedException(
                                "permission denied to update document");
                doc.setIndexListener(listener);
                parent = (NodeImpl) node.getParentNode();
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        ((ElementImpl) node).setNodeName(new QName(newName, "",
                                null));
                        parent.updateChild(node, node);
                        modificationCount++;
                        break;
                    case Node.ATTRIBUTE_NODE:
                        ((AttrImpl) node).setNodeName(new QName(newName, "",
                                null));
                        parent.updateChild(node, node);
                        modificationCount++;
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }

                doc.clearIndexListener();
                doc.setLastModified(System.currentTimeMillis());
                prevCollection = collection;
            }
            if (doc != null) doc.getBroker().saveCollection(collection);
        } finally {
            unlockDocuments();
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