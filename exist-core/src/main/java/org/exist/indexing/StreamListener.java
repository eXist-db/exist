/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.indexing;

import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;

/**
 * Callback interface which receives index events. StreamListeners are chained;
 * events should be forwarded to the next listener in the chain (if there is any).
 */
public interface StreamListener {

    enum ReindexMode {
        /**
         * Undefined mode
         */
        UNKNOWN,

        /**
         * Mode for storing nodes of a document
         */
        STORE,

        /**
         * Mode for replacing the nodes of a document with another document
         * this is really a group mode, which is later followed by {@link #REMOVE_ALL_NODES}
         * and then {@link #STORE}
         */
        REPLACE_DOCUMENT,

        /**
         * Mode for removing all the nodes of a document
         */
        REMOVE_ALL_NODES,

        /**
         * Mode for removing some nodes of a document
         */
        REMOVE_SOME_NODES,

        /**
         * Mode for removing a binary document
         */
        REMOVE_BINARY
    }

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
     * Starting to replace a document
     *
     * After which the sequence of {@link #startIndexDocument(Txn)} / events / {@link #endIndexDocument(Txn)}
     * will be called twice, first where the index mode will be {@link ReindexMode#REMOVE_ALL_NODES}
     * and second where the index mode will be {@link ReindexMode#STORE}
     * this is then finished by {@link #endReplaceDocument(Txn)}
     *
     * This can be used in conjunction with {@link #endReplaceDocument(Txn)} in indexes
     * which support differential updates
     *
     * @param transaction The current executing transaction
     */
    void startReplaceDocument(Txn transaction);

    /**
     * Finished replacing a document
     *
     * See {@link #startReplaceDocument(Txn)} for details
     *
     * @param transaction The current executing transaction
     */
    void endReplaceDocument(Txn transaction);

    /**
     * Starting to index a document
     *
     * @param transaction the current transaction
     */
    void startIndexDocument(Txn transaction);

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
    void characters(Txn transaction, AbstractCharacterData text, NodePath path);

    /**
     * Processed the closing tag of an element.
     *
     * @param transaction the current transaction
     * @param element the element which has been stored to the db
     * @param path the current node path
     */
    void endElement(Txn transaction, ElementImpl element, NodePath path);

    /**
     * Finishing storing a document
     *
     * @param transaction the current transaction
     */
    void endIndexDocument(Txn transaction);
}
