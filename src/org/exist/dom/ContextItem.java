/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

import org.exist.xquery.Expression;

public class ContextItem {

    private NodeProxy node;

    private ContextItem nextDirect;
	
	private int contextId;
	
	public ContextItem(NodeProxy node) {
		this(Expression.NO_CONTEXT_ID, node);
	}
	
    public ContextItem(int contextId, NodeProxy node) {
    	this.contextId = contextId;
        this.node = node;
    }
    
	public NodeProxy getNode() {
		return node;
	}
    
	public int getContextId() {
		return contextId;
	}
	
    public boolean hasNextDirect() {
        return (nextDirect != null);
    }
	
    public ContextItem getNextDirect() {
        return nextDirect;
    }
	
    public void setNextContextItem(ContextItem next) {
            nextDirect = next;
    }
    
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(node);        
        if (nextDirect != null)
            buf.append("/" + nextDirect);
        return buf.toString();
    }    
}
