
package org.exist.util;

import org.xmldb.api.*;
import org.xmldb.api.base.*;
import java.util.List;
import java.util.ArrayList;

public class CollectionScanner {

	public final static Resource[] scan(Collection current, String vpath, String pattern) 
	throws XMLDBException {
		List list = new ArrayList();
		scan(list, current, vpath, pattern);
		Resource resources[] = new Resource[list.size()];
		return (Resource[])list.toArray(resources);
	}

	public final static void scan(List list, Collection current, String vpath, String pattern) 
	throws XMLDBException {
		String[] resources = current.listResources();
		String name;
		for(int i = 0; i < resources.length; i++) {
			name = vpath + resources[i];
			System.out.println("checking " + name);
			if(DirectoryScanner.match(pattern, name))
				list.add(current.getResource(resources[i]));
		}
		String[] subs = current.listChildCollections();
		for(int i = 0; i < subs.length; i++) {
			name = vpath + subs[i];
			System.out.println("checking " + name + " = " + pattern);
			if(DirectoryScanner.matchStart(pattern, name))
				scan(list, current.getChildCollection(subs[i]), name + '/', pattern);
		}
	}
}
