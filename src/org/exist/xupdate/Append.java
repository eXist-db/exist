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
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.w3c.dom.NodeList;

/**
 * Implements an XUpate append statement.
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
	        String childAttr, Map namespaces) {
		super(broker, docs, selectStmt, namespaces);
		if(childAttr == null || childAttr.equals("last()"))
		    child = -1;
		else
		    child = Integer.parseInt(childAttr);
	}
	
	/*
	 * @see org.exist.xupdate.Modification#process()
	 */
	public long process() throws PermissionDeniedException, LockException,
		EXistException, XPathException {
	    NodeList children = content.getChildNodes();
	    if(children.getLength() == 0)
	        return 0;
		
	    try {
	        NodeImpl ql[] = selectAndLock();
			IndexListener listener = new IndexListener(ql);
			Collection collection = null, prevCollection = null;
			DocumentImpl doc = null, prevDoc = null;
			NodeImpl node;
			for(int i = 0; i < ql.length; i++) {
				node = ql[i];
				doc = (DocumentImpl) node.getOwnerDocument();
				doc.setIndexListener(listener);
				collection = doc.getCollection();
				if (prevCollection != null && collection != prevCollection)
					doc.getBroker().saveCollection(prevCollection);
				if (!doc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
					throw new PermissionDeniedException("permission to update document denied");
                node.appendChildren(children, child);
                doc.clearIndexListener();
                doc.setLastModified(System.currentTimeMillis());
				prevCollection = collection;
			}
			if (doc != null)
				doc.getBroker().saveCollection(collection);
			return ql.length;
	    } finally {
	        // release all acquired locks
	        unlockDocuments();
	    }
	}

	public String getName() {
		return "append";
	}
}
