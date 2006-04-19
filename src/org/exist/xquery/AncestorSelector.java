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
package org.exist.xquery;

import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.w3c.dom.Node;

public class AncestorSelector implements NodeSelector {

	private final NodeSet descendants;
	private final int contextId;
	private final boolean includeSelf;
    private NodeSet ancestors = null;
    
    public AncestorSelector(NodeSet descendants, int contextId, boolean includeSelf) {
    	this.descendants  = descendants;
    	this.contextId = contextId;
    	this.includeSelf = includeSelf;    
    }

    public NodeProxy match(NodeProxy proxy) {  	
		switch (proxy.getNodeType()) {
		case NodeProxy.UNKNOWN_NODE_TYPE:
			break;
		case Node.ELEMENT_NODE :
			break;			
		case Node.ATTRIBUTE_NODE :
			break;
		case Node.TEXT_NODE :
			break;
		case Node.PROCESSING_INSTRUCTION_NODE :
			break;
		case Node.COMMENT_NODE :
			break;
		case Node.DOCUMENT_NODE:	
			//TODO : Should we raise an error error ?
			return null;
		default:
			throw new IllegalArgumentException("Unknown node type");			
		}		    	
    	if (ancestors == null)
    		ancestors = descendants.getAncestors(contextId, includeSelf);  
    	//TODO : help proxy by adding a node type ?
        return ancestors.get(proxy.getDocument(), proxy.getGID());          
    }
}
