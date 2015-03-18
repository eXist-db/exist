/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2005-2010 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.NodeId;

import java.util.IdentityHashMap;
import org.exist.dom.persistent.IStoredNode;

/**
 * Global notification service for document updates. Other classes
 * can subscribe to this service to be notified of document modifications,
 * removals or additions.
 * 
 * @author wolf
 *
 */
public class NotificationService extends IdentityHashMap<UpdateListener, Object> {

	private static final long serialVersionUID = -3629584664969740903L;

	private final static Logger LOG = LogManager.getLogger(NotificationService.class);
	
	public NotificationService() {
		super();
	}
	
	/**
	 * Subscribe an {@link UpdateListener} to receive notifications.
	 * 
	 * @param listener
	 */
	public synchronized void subscribe(UpdateListener listener) {
		put(listener, new Object());
	}
	
	/**
	 * Unsubscribe an {@link UpdateListener}.
	 * 
	 * @param listener
	 */
	public synchronized void unsubscribe(UpdateListener listener) {
		final Object i = remove(listener);
		if (i == null)
			{throw new RuntimeException(hashCode() + " listener not found: " + listener.hashCode());}
        listener.unsubscribe();
    }

	/**
	 * Notify all subscribers that a document has been updated/removed or
	 * a new document has been added.
	 * 
	 * @param document
	 * @param event
	 */
	public synchronized void notifyUpdate(DocumentImpl document, int event) {
		for (final UpdateListener listener : keySet()) {
	        listener.documentUpdated(document, event);
		}
	}

    /**
	 * Notify all subscribers that a node has been moved. Nodes may be moved during a
     * defragmentation run.
	 */
	public synchronized void notifyMove(NodeId oldNodeId, IStoredNode newNode) {
		for (final UpdateListener listener : keySet()) {
	        listener.nodeMoved(oldNodeId, newNode);
		}
	}

    public void debug() {
		LOG.debug("Registered UpdateListeners:");
		for (final UpdateListener listener : keySet()) {
	        listener.debug();
		}
	}
}
