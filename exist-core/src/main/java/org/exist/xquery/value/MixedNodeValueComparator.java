/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.NodeProxy;

import java.util.Comparator;

/**
 * @author wolf
 */
public class MixedNodeValueComparator implements Comparator {

    /* (non-Javadoc)
     * @see org.exist.dom.persistent.DocumentOrderComparator#compare(java.lang.Object, java.lang.Object)
     */
    public int compare(Object o1, Object o2) {
        final NodeValue n1 = (NodeValue) o1;
        final NodeValue n2 = (NodeValue) o2;
        if (n1.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
            if (n2.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                return ((NodeImpl) n1).compareTo((NodeImpl) n2);
            } else {
                return -1;
            }
        } else {
            if (n2.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                return ((NodeProxy) o1).compareTo((NodeProxy) o2);
            } else {
                return 1;
            }
        }
    }
}