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

public class ContextItem {

    boolean isTransverseAxis= false;
    private NodeProxy node;
    //"direct" axis are ancestor, parent, self, child, descendant and...
    //attribute.
    //The later, although conceptually transverse is considered as direct, 
    //thanks to its dpth of 1
    private ContextItem nextDirect;
    //"transverse" axis are preceding, preceding-sibling, following-sibling, following
    private ContextItem nextTransverse;
	
	
    public ContextItem(NodeProxy node) {        
        this.node = node;
    }
    
	public NodeProxy getNode() {
		return node;
	}
    
    public boolean hasNextDirect() {
        return (nextDirect != null);
    }
    
    public boolean hasNextTransverse() {
        return (nextTransverse != null);
    }     
	
    public ContextItem getNextDirect() {
        return nextDirect;
    }
    
    public ContextItem getNextTransverse() {
        return nextTransverse;
    }    
    
    public void setTransverseAxis() {
        isTransverseAxis = true;  
    }
	
    public void setNextContextItem(ContextItem next) {
        if (isTransverseAxis)
            nextTransverse = next;
        else
            nextDirect = next;
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer(); 
        if (nextTransverse != null)
            buf.append("(");
        buf.append(node);
        ContextItem temp = nextTransverse;
        while (temp != null) {
            buf.append(", " + temp);
            temp = temp.nextTransverse;
        }
        if (nextTransverse != null)
            buf.append(")");        
        if (nextDirect != null)
            buf.append("/" + nextDirect);
        return buf.toString();
    }    
}
