/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 * $Id$
 */
package org.exist.dom.persistent;

/**
 * This interface is used to report changes of the node id or the storage address
 * of a node to classes which have to keep node sets up to date during processing.
 * Used by the XUpdate classes to update the query result sets.
 *
 * @author wolf
 */
public interface NodeIndexListener {

    /**
     * The internal id of a node has changed. The storage address is
     * still the same, so one can find the changed node by comparing
     * its storage address.
     *
     * @param node
     */
    void nodeChanged(NodeHandle node);

}
