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
package org.exist.memtree;

import org.exist.dom.QName;
import org.exist.xquery.value.Type;


/**
 * A dynamically constructed namespace node.
 * 
 * @author wolf
 */
public class NamespaceNode extends NodeImpl {

    /**
     * @param doc
     * @param nodeNumber
     */
    public NamespaceNode(DocumentImpl doc, int nodeNumber) {
        super(doc, nodeNumber);
    }
    
    /* (non-Javadoc)
     * @see org.exist.memtree.NodeImpl#getNodeType()
     */
    public short getNodeType() {
        return NodeImpl.NAMESPACE_NODE;
    }

    /* (non-Javadoc)
     * @see org.exist.memtree.NodeImpl#getType()
     */
    public int getType() {
        return Type.NAMESPACE;
    }
    
    public String getPrefix() {
        QName qn = (QName)document.namePool.get(document.nodeName[nodeNumber]);
		return qn != null ? qn.getLocalName() : null;
    }
    
    public String getNamespaceURI() {
        return new String(document.characters, document.alpha[nodeNumber],
        		document.alphaLen[nodeNumber]);
    }
}
