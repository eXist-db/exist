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
     * @param pool
     * @param user
     * @param selectStmt
     */
    public Update(DBBroker broker, DocumentSet docs, String selectStmt,
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
        NodeList children = content;
        if (children.getLength() == 0) 
            return 0;
        int modifications = children.getLength();
        try {
            NodeImpl ql[] = selectAndLock();
            IndexListener listener = new IndexListener(ql);
            NodeImpl node;
            Node temp;
            TextImpl text;
            AttrImpl attribute;
            ElementImpl parent;
            DocumentImpl doc = null;
            Collection collection = null, prevCollection = null;
            for (int i = 0; i < ql.length; i++) {
                node = ql[i];
                if (node == null) {
                    LOG.warn("select " + selectStmt + " returned empty node");
                    continue;
                }
                doc = (DocumentImpl) node.getOwnerDocument();
                doc.setIndexListener(listener);
                collection = doc.getCollection();
                if (!doc.getPermissions().validate(broker.getUser(),
                        Permission.UPDATE))
                        throw new PermissionDeniedException(
                                "permission to update document denied");
                if (prevCollection != null && collection != prevCollection)
                        doc.getBroker().saveCollection(prevCollection);
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        if (modifications == 0) modifications = 1;
                        ((ElementImpl) node).update(children);
                        break;
                    case Node.TEXT_NODE:
                        parent = (ElementImpl) node.getParentNode();
                        if (children.getLength() != 0) {
                            temp = children.item(0);
                            text = new TextImpl(temp.getNodeValue());
                        } else {
                            modifications = 1;
                            text = new TextImpl("");
                        }
                        text.setOwnerDocument(doc);
                        parent.updateChild(node, text);
                        break;
                    case Node.ATTRIBUTE_NODE:
                        parent = (ElementImpl) node.getParentNode();
                        if (parent == null) {
                            LOG.warn("parent node not found for "
                                    + node.getGID());
                            break;
                        }
                        AttrImpl attr = (AttrImpl) node;
                        if (children.getLength() != 0) {
                            temp = children.item(0);
                            attribute = new AttrImpl(attr.getQName(), temp
                                    .getNodeValue());
                        } else {
                            modifications = 1;
                            attribute = new AttrImpl(attr.getQName(), "");
                        }
                        attribute.setOwnerDocument(doc);
                        parent.updateChild(node, attribute);
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }
                doc.setLastModified(System.currentTimeMillis());
                prevCollection = collection;
            }
            if (doc != null) doc.getBroker().saveCollection(collection);
        } finally {
            unlockDocuments();
        }
        return modifications;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xupdate.Modification#getName()
     */
    public String getName() {
        return "update";
    }

}