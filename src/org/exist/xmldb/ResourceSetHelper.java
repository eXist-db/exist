/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2005,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 *  $Id:
 */
package org.exist.xmldb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author jmv
 *
 */
public class ResourceSetHelper {
	public static ResourceSet intersection(ResourceSet s1, ResourceSet s2) throws XMLDBException {
		Map m1 = new MapResourceSet(s1).getResourcesMap();
		Map m2 = new MapResourceSet(s2).getResourcesMap();
		Set set1 = m1.keySet();
		Set set2 = m2.keySet();
		set1.retainAll(set2);
		Map m = new HashMap();
		Iterator iter = set1.iterator();
		while ( iter.hasNext() ) {
          String key = (String)iter.next();
          Resource resource = (Resource)m1.get(key);
          m.put(key, resource);
		}
		MapResourceSet res = new MapResourceSet(m); 
		/*
		VectorResourceSet res = new VectorResourceSet(); 
		Collection c1 = new VectorResourceSet(s1).getResources();
		res.getResources().addAll( c1 );
		Collection c2 = new VectorResourceSet(s2).getResources();
		res.getResources().retainAll(c2);
	 return res;
	 */
	 return res;
	}
}
