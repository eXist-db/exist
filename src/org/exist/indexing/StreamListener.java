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

import org.exist.storage.txn.Txn;
import org.exist.storage.NodePath;
import org.exist.dom.ElementImpl;
import org.exist.dom.AttrImpl;
import org.exist.dom.TextImpl;
import org.exist.dom.DocumentImpl;

/**
 * Callback interface which receives index events. StreamListeners are chained;
 * events should be forwarded to the next listener in the chain (if there is any).
 */
public interface StreamListener {

    public final static int STORE = 0;

    public final static int REMOVE = 1;

    public final static int REMOVE_NODES = 2;

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
     * Reset this listener to operate on the specified document, using the mode
     * given. mode will be one of {@link #STORE}, {@link #REMOVE} or
     * {@link #REMOVE_NODES}.
     *
     * @param doc the document which is processed
     * @param mode the current operation mode
     */
    void setDocument(DocumentImpl doc, int mode);

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
     * Processed the closing tag of an element.
     *
     * @param transaction the current transaction
     * @param element the element which has been stored to the db
     * @param path the current node path
     */
    void endElement(Txn transaction, ElementImpl element, NodePath path);

    /**
     * A text node has been stored.
     * @param transaction the current transaction
     * @param text the text node which has been stored to the db.
     * @param path the current node path
     */
    void characters(Txn transaction, TextImpl text, NodePath path);
}