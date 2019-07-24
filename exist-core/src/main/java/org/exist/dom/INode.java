/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.dom;

/**
 * Interface for Nodes in eXist
 * used for both persistent and
 * in-memory nodes.
 * 
 * @param <T> The type of the persistent
 * or in-memory document
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public interface INode<D extends org.w3c.dom.Document, T extends INode> extends org.w3c.dom.Node,
    INodeHandle<D>, Comparable<T> {

    /**
     * The node is a <code>Namespace</code>.
     */
    short NAMESPACE_NODE              = 13;

    /**
     * Get the qualified name of the Node
     * 
     * @return The qualified name of the Node
     */
    QName getQName();

    //TODO try and get rid of this after decoupling nameTyping from QName class (AR)?
    void setQName(QName qname);
}
