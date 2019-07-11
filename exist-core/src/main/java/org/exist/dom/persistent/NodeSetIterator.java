/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
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
package org.exist.dom.persistent;

import java.util.Iterator;

/**
 * An iterator on a node set. Extends the {@link Iterator} interface with
 * an additional method to reposition the iterator.
 *
 * @author wolf
 */
public interface NodeSetIterator extends Iterator<NodeProxy> {

    /**
     * @return Look ahead: returns the node at the iterator's current position but
     * does not move the iterator to the next node.
     *
     */
    public NodeProxy peekNode();

    /**
     * Reposition the iterator on the given NodeProxy, so calling
     * {@link Iterator#next()} will return this NodeProxy. If the
     * node does not exist in the node set, the iterator will be positioned
     * to the end of the set.
     *
     * @param proxy the node
     */
    public void setPosition(NodeProxy proxy);

}
