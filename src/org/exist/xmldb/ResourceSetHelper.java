/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
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

    public static ResourceSet intersection(final ResourceSet s1, final ResourceSet s2) throws XMLDBException {
        final Map<String, Resource> m1 = new MapResourceSet(s1).getResourcesMap();
        final Map<String, Resource> m2 = new MapResourceSet(s2).getResourcesMap();
        final Set<String> set1 = m1.keySet();
        final Set<String> set2 = m2.keySet();
        set1.retainAll(set2);
        final Map<String, Resource> m = new HashMap<>();
        for (String key : set1) {
            final Resource resource = m1.get(key);
            m.put(key, resource);
        }
        final MapResourceSet res = new MapResourceSet(m);
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
