/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.indexing;

import org.exist.dom.AttrImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;

/**
 * Callback interface which receives index events. StreamListeners are chained;
 * events should be forwarded to the next listener in the chain (if there is any).
 */
public interface StreamListener {

    /**
     * Undefined mode
     */	
    public final static int UNKNOWN = -1;

    /**
     * Mode for storing nodes of a document
     */	
    public final static int STORE = 0;

    /**
     * Mode for removing all the nodes of a document
     */
    public final static int REMOVE_ALL_NODES = 1;

    /**
     * Mode for removing some nodes of a document
     */
    public final static int REMOVE_SOME_NODES = 2;

    public final static int REMOVE_BINARY = 3;

    /**
     * Returns the IndexWorker that owns this listener.
     * 
     * @return the IndexWorker
     */
    IndexWorker getWorker();

    /**
     * Set the next stream listener in the chain. Events should always be forwarded
     * to the next listener.
     *
     * @param listener the next listener in the chain.
     */
    void setNextInChain(StreamListener listener);

    /**
     * Returns the next stream listener in the chain. This should usually be the one
     * that was passed in from {@link #setNextInChain(StreamListener)}.
     *
     * @return the next listener in the chain.
     */
    StreamListener getNextInChain();

    /**
     * Processed the opening tag of an element.
     *
     * @param transaction the current transaction
     * @param element the element which has been stored to the db
     * @param path the current node path
     */
    void startElement(Txn transaction, ElementImpl element, NodePath path);

    /**
     * An attribute has been stored.
     *
     * @param transaction the current transaction
     * @param attrib the attribute which has been stored to the db
     * @param path the current node path
     */
    void attribute(Txn transaction, AttrImpl attrib, NodePath path);

    /**
     * A text node has been stored.
     * @param transaction the current transaction
     * @param text the text node which has been stored to the db.
     * @param path the current node path
     */
    void characters(Txn transaction, CharacterDataImpl text, NodePath path);

    /**
     * Processed the closing tag of an element.
     *
     * @param transaction the current transaction
     * @param element the element which has been stored to the db
     * @param path the current node path
     */
    void endElement(Txn transaction, ElementImpl element, NodePath path);
}