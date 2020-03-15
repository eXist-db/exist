/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;

public interface UpdateListener {

	// Notification types:
	
	/** Notification type: a document was added to a collection */
	public final static int ADD = 0;
	
	/** Notification type: a document has been updated */
	public final static int UPDATE = 1;
	
	/** Notification type: a document was removed */
	public final static int REMOVE = 2;
	
	/**
	 * Called whenever a document is updated within the database.
	 * Parameter event specifies the event type, i.e. one of {@link #ADD}, {@link #UPDATE} 
	 * or {@link #REMOVE}.
	 * 
	 * @param document updated document
	 * @param event update event
	 */
	public void documentUpdated(DocumentImpl document, int event);

    /**
     * nodeMoved is called after a defragmentation run occurred for a document during which
     * the address and the nodeId of a node may have changed. Defragmentation
     * may only occur after a node update.
     *
     * @param oldNodeId the oold node id
     * @param newNode the new node the old node was move to
     */
    public void nodeMoved(NodeId oldNodeId, NodeHandle newNode);

    /**
     * Called when the listener is removed from the notification service
     */
    public void unsubscribe();

    public void debug();
}