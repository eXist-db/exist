
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.storage;

import java.util.HashMap;
import java.util.TreeMap;
import org.exist.dom.*;

/**
 * a simple pool for caching nodes
 * 
 */
public final class ObjectPool {

    protected TreeMap map;
    protected int MAX = 15000;

    public ObjectPool(int max) {
	MAX = max;
        map = new TreeMap();
    }
    
    public ObjectPool() {
	map = new TreeMap();
    }

    public void add(NodeImpl node) {
	NodeProxy p = new NodeProxy((DocumentImpl)node.getOwnerDocument(), 
				    node.getGID());
	if(map.size() == MAX) {
	    System.out.println("cleaning up ObjectPool");
	    map.clear();
	}
	map.put(p, node);
    }

    public NodeImpl get(DocumentImpl doc, long gid) {
        return (NodeImpl)map.get(new NodeProxy(doc, gid));
    }
    
    public boolean contains(NodeImpl node) {
	return map.containsKey(new NodeProxy((DocumentImpl)node.getOwnerDocument(), node.getGID()));
    }
    
    public boolean contains(DocumentImpl doc, long gid) {
        return map.containsKey(new NodeProxy(doc, gid));
    }

    public void clear() {
	map.clear();
    }
    
    public int getMax() {
	return MAX;
    }
}
