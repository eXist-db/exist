
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import org.w3c.dom.NodeList;
import org.exist.dom.*;

public class ValueNodeSet extends Value {

    protected NodeSet nodes = null;

    public ValueNodeSet( NodeSet value ) {
        super( Value.isNodeList );
        nodes = value;
    }

    public boolean getBooleanValue() {
        return ( nodes.getLength() > 0 );
    }

    public NodeList getNodeList() {
        return nodes;
    }
	
	public int getLength() {
		return nodes.getLength();
	}
	
    public double getNumericValue() {
        // take the first node from the set and try
        // to convert it to a number
        if ( nodes.getLength() > 0 ) {
            NodeProxy p = nodes.get( 0 );
            String v = p.getNodeValue();
            try {
                return Double.parseDouble( v );
            } catch ( NumberFormatException f ) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    public String getStringValue() {
        StringBuffer val = new StringBuffer();
        for ( int i = 0; i < nodes.getLength(); i++ ) {
            NodeProxy p = nodes.get( i );
            val.append( p.getNodeValue() );
        }
        return val.toString();
    }
    
    public void setValue(NodeSet value) {
    	nodes = value;
    }
}

