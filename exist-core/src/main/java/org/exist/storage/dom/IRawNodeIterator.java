/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist team
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.storage.dom;

import org.exist.dom.persistent.NodeHandle;
import org.exist.storage.btree.Value;

import java.io.Closeable;
import java.io.IOException;

public interface IRawNodeIterator extends Closeable {

    /**
     * Reposition the iterator to the start of the specified node.
     *
     * @param node the start node where the iterator will be positioned.
     * @throws IOException if an I/O error occurs
     */
    public void seek(NodeHandle node) throws IOException;


    /**
     * Returns the raw data of the next node in document order.
     *
     * @return the raw data of the node
     */
    public Value next();

    /**
     * Close the iterator
     */
    @Override
    public void close();
}
