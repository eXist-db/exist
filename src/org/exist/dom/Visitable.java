/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.dom;

import java.util.Iterator;

/**
 * Visitor pattern: interface to be implemented by a visitable node.
 *
 */
public interface Visitable {
    
    /**
     * Visit the current node. For element nodes,
     * the method recursively traverses through the child nodes and
     * calls accept on each of them.
     * 
     * @param iterator an iterator positioned at the current node
     * @param visitor the visitor to call.
     * @return the value returned by the NodeVisitor.visit method.
     */
    public boolean accept(Iterator iterator, NodeVisitor visitor);  
}