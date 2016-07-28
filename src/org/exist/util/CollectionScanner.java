
package org.exist.util;

import java.util.ArrayList;
import java.util.List;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public class CollectionScanner {

	public final static Resource[] scan(Collection current, String vpath, String pattern) 
	throws XMLDBException {
		final List<Resource> list = new ArrayList<Resource>();
		scan(list, current, vpath, pattern);
		final Resource resources[] = new Resource[list.size()];
		return (Resource[])list.toArray(resources);
	}

	public final static void scan(List<Resource> list, Collection current, String vpath, String pattern) 
	throws XMLDBException {
		final String[] resources = current.listResources();
		String name;
		for (String resource : resources) {
			name = vpath + resource;
			System.out.println("checking " + name);
			if (DirectoryScanner.match(pattern, name)) {
				list.add(current.getResource(resource));
			}
		}
		final String[] childCollections = current.listChildCollections();
		for (String sub : childCollections) {
			name = vpath + sub;
			System.out.println("checking " + name + " = " + pattern);
			if (DirectoryScanner.matchStart(pattern, name))
			///TODO : use dedicated function in XmldbURI
			{
				scan(list, current.getChildCollection(sub), name + "/", pattern);
			}
		}
	}
}
