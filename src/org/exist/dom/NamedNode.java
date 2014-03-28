/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import org.exist.numbering.NodeId;
import org.w3c.dom.DOMException;

/**
 * A node with a QName, i.e. an element or attribute.
 * 
 * @author wolf
 */
//TODO : rename as StoredNamedNode ? -pb
public class NamedNode extends StoredNode implements QNameable {

    protected QName nodeName = null;
    
    public NamedNode(short nodeType) {
        super(nodeType);
    }

    /**
     * @param nodeType
     */
    public NamedNode(short nodeType, QName qname) {
        super(nodeType);
        this.nodeName = qname;
    }

    /**
     * 
     * 
     * @param nodeId 
     * @param qname 
     * @param nodeType 
     */
    public NamedNode(short nodeType, NodeId nodeId, QName qname) {
        super(nodeType, nodeId);
        this.nodeName = qname;
    }

    public NamedNode(NamedNode other) {
        super(other);
        this.nodeName = other.nodeName;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeImpl#getQName()
     */
    public QName getQName() {
        return nodeName;
    }

    public void setNodeName(QName name) {
        nodeName = name;
    }

    public void setNodeName(QName name, SymbolTable symbols) throws DOMException {
        nodeName = name;
        if (symbols.getSymbol(nodeName.getLocalName()) < 0) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR,
                    "Too many element/attribute names registered in the database. No of distinct names is limited to 16bit. Aborting store.");
        }
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeImpl#clear()
     */
    public void clear() {
        super.clear();
        nodeName = null;
    }

}
