package org.exist.storage;

/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import java.util.TreeMap;
import java.util.Iterator;
import org.apache.log4j.Category;
import org.exist.dom.*;

public class ElementPool {
    
    class CachedElementSet {
        DocumentSet documents;
        NodeSet nodes;
        String elementName;
        
        public CachedElementSet(DocumentSet documents, 
				NodeSet nodes, String name) {
            this.documents = documents;
            this.nodes = nodes;
            this.elementName = name;
        }
    }
    
    protected TreeMap map = new TreeMap();
    protected long lastAccess = 0;
    protected int LIMIT;
    protected int TIMEOUT;
    
    private static Category LOG = Category.getInstance(ElementPool.class.getName());
    public ElementPool() {
        LIMIT = 10;
        TIMEOUT = 300000;
    }
    
    public ElementPool(int limit) {
        LIMIT = limit;
        TIMEOUT = 300000;
    }

    public void add(DocumentSet docs, NodeSet nodes, String elementName) {
        if(map.size() == LIMIT) {
            LOG.debug("cleaning up ...");
	    int j = 0;
	    String name;
	    for(Iterator i = map.keySet().iterator(); i.hasNext() && j < LIMIT / 4; j++) {
		name = (String)i.next();
		map.remove(name);
	    }
        }
        CachedElementSet cache = 
            new CachedElementSet(docs, nodes, elementName);
        map.put(elementName, cache);
    }
	
    public void clear() {
        LOG.debug("cleaning up element pool ..");
        map.clear();
    }

    public boolean inCache(DocumentSet docs, String elementName) {
        //checkLastAccess();
        CachedElementSet cache = (CachedElementSet)map.get(elementName);
        if(cache == null) return false;
        if(cache.documents.contains(docs))
	    return true;
            // if document set in cache is bigger than 2*docs, return false
            //return (docs.getLength() * 2 > cache.documents.getLength());
        return false;
    }
	
    public NodeSet getNodeSet(DocumentSet docs, String elementName) {
        //checkLastAccess();
        CachedElementSet cache = (CachedElementSet)map.get(elementName);
        if(cache == null) return null;
        if(cache.documents.contains(docs))
            return cache.nodes;
        return null;
    }
    
    protected void checkLastAccess() {
        if(System.currentTimeMillis() - lastAccess > TIMEOUT)
            map.clear();
        lastAccess = System.currentTimeMillis();
    }
}
