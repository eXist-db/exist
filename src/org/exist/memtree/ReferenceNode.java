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

import org.exist.dom.NodeProxy;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author wolf
 */
public class ReferenceNode extends NodeImpl {

    /**
     * @param doc
     * @param nodeNumber
     */
    public ReferenceNode(DocumentImpl doc, int nodeNumber) {
        super(doc, nodeNumber);
    }

    public NodeProxy getReference() {
        int p = document.alpha[nodeNumber];
        return document.references[p];
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("reference[ ");    
    	result.append(getReference().getNode().toString());   
    	result.append(" ]");  
        return result.toString();
    } 
    
    public String getNamespaceURI() {
    	//TODO : improve performance ?
        return getReference().getNode().getNamespaceURI();
    }  
    
    public String getLocalName() {
    	//TODO : improve performance ?
        return getReference().getNode().getLocalName();
    } 
    
    public NamedNodeMap getAttributes() {
    	//TODO : improve performance ?
        return getReference().getNode().getAttributes();
    }
    
    public Node getFirstChild() {
    	//TODO : improve performance ?
        //TODO : how to make this node a reference as well ?
        return getReference().getNode().getFirstChild();
    }    
}
