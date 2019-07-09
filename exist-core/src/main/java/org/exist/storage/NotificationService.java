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

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.NodeId;

import java.util.IdentityHashMap;
import java.util.Map;

import org.exist.dom.persistent.IStoredNode;

/**
 * Global notification service for document updates. Other classes
 * can subscribe to this service to be notified of document modifications,
 * removals or additions.
 *
 * @author wolf
 */
@ThreadSafe
public class NotificationService implements BrokerPoolService {

    private static final long serialVersionUID = -3629584664969740903L;
    private static final Logger LOG = LogManager.getLogger(NotificationService.class);

    private final Map<UpdateListener, Object> listeners = new IdentityHashMap<>();

    public NotificationService() {
        super();
    }

    /**
     * Subscribe an {@link UpdateListener} to receive notifications.
     *
     * @param listener to receive notifications for
     */
    public synchronized void subscribe(final UpdateListener listener) {
        listeners.put(listener, new Object());
    }

    /**
     * Unsubscribe an {@link UpdateListener}.
     *
     * @param listener to stop receiving updates for
     */
    public synchronized void unsubscribe(final UpdateListener listener) {
        final Object i = listeners.remove(listener);
        if (i == null) {
            throw new RuntimeException(hashCode() + " listener not found: " + listener.hashCode());
        }
        listener.unsubscribe();
    }

    /**
     * Notify all subscribers that a document has been updated/removed or
     * a new document has been added.
     *
     * @param document subscribers are listining to
     * @param event that triggers the notify
     */
    public synchronized void notifyUpdate(final DocumentImpl document, final int event) {
        listeners.keySet().forEach(listener -> listener.documentUpdated(document, event));
    }

    /**
     * Notify all subscribers that a node has been moved. Nodes may be moved during a
     * defragmentation run.
     * @param newNode the new node
     * @param oldNodeId old node that have been moved
     */
    public synchronized void notifyMove(final NodeId oldNodeId, final IStoredNode newNode) {
        listeners.keySet().forEach(listener -> listener.nodeMoved(oldNodeId, newNode));
    }

    public synchronized void debug() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Registered UpdateListeners:");
        }
        listeners.keySet().forEach(UpdateListener::debug);
    }
}
