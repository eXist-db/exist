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
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.StoredNode;
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
 * Implements an XUpate append statement.
 * 
 * Note: appending an attribute that is already present in
 * an element will overwrite the old attribute value.
 * 
 * @author Wolfgang Meier
 */
public class Append extends Modification {

    private int child;
    
	/**
	 * Constructor for Append.
	 * @param selectStmt
	 */
	public Append(DBBroker broker, DocumentSet docs, String selectStmt, 
	        String childAttr, Map<String, String> namespaces, Map<String, Object> variables) {
		super(broker, docs, selectStmt, namespaces, variables);
		if(childAttr == null || "last()".equals(childAttr))
		    {child = -1;}
		else
		    {child = Integer.parseInt(childAttr);}
	}
	
	/*
	 * @see org.exist.xupdate.Modification#process()
	 */
	public long process(Txn transaction) throws PermissionDeniedException, LockException,
		EXistException, XPathException, TriggerException {
	    final NodeList children = content;
	    if(children.getLength() == 0)
	        {return 0;}
		
	    try {
	        final StoredNode ql[] = selectAndLock(transaction);
			final IndexListener listener = new IndexListener(ql);
			final NotificationService notifier = broker.getBrokerPool().getNotificationService();
			for(int i = 0; i < ql.length; i++) {
				final StoredNode node = ql[i];
				final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
				doc.getMetadata().setIndexListener(listener);
				if (!doc.getPermissions().validate(broker.getSubject(), Permission.WRITE)) {
					throw new PermissionDeniedException("User '" + broker.getSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                                }
                node.appendChildren(transaction, children, child);
                doc.getMetadata().clearIndexListener();
                doc.getMetadata().setLastModified(System.currentTimeMillis());
                modifiedDocuments.add(doc);
                broker.storeXMLResource(transaction, doc);
                notifier.notifyUpdate(doc, UpdateListener.UPDATE);
			}
			checkFragmentation(transaction, modifiedDocuments);
			return ql.length;
	    } finally {
	        // release all acquired locks
	        unlockDocuments(transaction);
	    }
	}

	public String getName() {
		return "append";
	}
}
