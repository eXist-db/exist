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
import org.w3c.dom.Attr;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Implements an XUpdate remove operation.
 * 
 * @author Wolfgang Meier
 */
public class Remove extends Modification {

    /**
     * Constructor for Remove.
     *
	 * @param broker the database broker.
	 * @param docs the document working set.
	 * @param selectStmt the select statement.
	 * @param namespaces the namespaces.
	 * @param variables the variables.
     */
	public Remove(DBBroker broker, DocumentSet docs, String selectStmt,
			Map<String, String> namespaces, Map<String, Object> variables) {
		super(broker, docs, selectStmt, namespaces, variables);
	}

	@Override
	public long process(Txn transaction) throws PermissionDeniedException,
			LockException, EXistException, XPathException, TriggerException {
		try {
			final StoredNode[] ql = selectAndLock(transaction);
			final NotificationService notifier = broker.getBrokerPool()
					.getNotificationService();
			for (int i = 0; i < ql.length; i++) {
				final StoredNode node = ql[i];
                final DocumentImpl doc = node.getOwnerDocument();
				if (!doc.getPermissions().validate(broker.getCurrentSubject(),
						Permission.WRITE)) {
            				throw new PermissionDeniedException("User '" + broker.getCurrentSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                                }

				final NodeImpl parent = (NodeImpl) getParent(node);

                if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
					throw new EXistException(
							"you cannot remove the document element. Use update "
									+ "instead");
				} else {
					parent.removeChild(transaction, node);
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
		return "remove";
	}

}