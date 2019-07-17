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
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeImpl;
import org.exist.dom.persistent.StoredNode;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NotificationService;
import org.exist.storage.UpdateListener;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.w3c.dom.NodeList;

/**
 * Implements an XUpdate insert-after or insert-before modification.
 * 
 * @author Wolfgang Meier
 */
public class Insert extends Modification {

    public final static int INSERT_BEFORE = 0;

    public final static int INSERT_AFTER = 1;

    private int mode = INSERT_BEFORE;

    /**
     * Constructor for Insert.
     *
     * @param broker the database broker.
     * @param docs the document working set.
     * @param selectStmt the select statement.
     * @param namespaces the namespaces.
     * @param variables the variables.
     */
    public Insert(DBBroker broker, DocumentSet docs, String selectStmt,
            Map<String, String> namespaces, Map<String, Object> variables) {
        super(broker, docs, selectStmt, namespaces,  variables);
    }

    /**
     * Constructor for Insert.
     *
     * @param broker the database broker.
     * @param docs the document working set.
     * @param selectStmt the select statement.
     * @param mode the insert mode
     * @param namespaces the namespaces.
     * @param variables the variables.
     */
    public Insert(DBBroker broker, DocumentSet docs, String selectStmt,
            int mode, Map<String, String> namespaces, Map<String, Object> variables) {
        this(broker, docs, selectStmt, namespaces, variables);
        this.mode = mode;
    }

    @Override
    public long process(Txn transaction) throws PermissionDeniedException, LockException,
            EXistException, XPathException, TriggerException {
        final NodeList children = content;
        if (children.getLength() == 0) {return 0;}
        try {
            final StoredNode[] ql = selectAndLock(transaction);
            final NotificationService notifier = broker.getBrokerPool().getNotificationService();
            final int len = children.getLength();
            if (LOG.isDebugEnabled())
                {LOG.debug("found " + len + " nodes to insert");}
            for (int i = 0; i < ql.length; i++) {
                final StoredNode node = ql[i];
                final DocumentImpl doc = node.getOwnerDocument();
                if (!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("permission to update document denied");
                }
                final NodeImpl parent = (NodeImpl) getParent(node);
                switch (mode) {
                    case INSERT_BEFORE:
                        parent.insertBefore(transaction, children, node);
                        break;
                    case INSERT_AFTER:
                        parent.insertAfter(transaction, children, node);
                        break;
                }
                doc.getMetadata().setLastModified(System.currentTimeMillis());
                modifiedDocuments.add(doc);
                broker.storeXMLResource(transaction, doc);
                notifier.notifyUpdate(doc, UpdateListener.UPDATE);
            }
            checkFragmentation(transaction, modifiedDocuments);
            return ql.length;
        } finally {
            unlockDocuments(transaction);
        }
    }

    @Override
    public String getName() {
        return (mode == INSERT_BEFORE ? "insert-before" : "insert-after");
    }

}