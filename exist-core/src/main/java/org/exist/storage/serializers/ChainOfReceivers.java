/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
package org.exist.storage.serializers;

import org.exist.dom.persistent.NodeHandle;
import org.exist.util.serializer.Receiver;

public interface ChainOfReceivers extends Receiver<NodeHandle> {

    /**
     * Register the next receiver in the chain. All
     * events should be forwarded to this.
     *
     * @param next the next receiver in the chain.
     */
    void setNextInChain(Receiver next);

    /**
     * Returns the next receiver in the chain.
     * @return the next receiver
     */
    Receiver getNextInChain();

    /**
     * Walks the chain and returns the final receiver.
     * @return the last receiver in the chain
     */
    Receiver getLastInChain();
}
