//$Header$

package org.exist.xmldb;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

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
