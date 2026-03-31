/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import java.util.LinkedHashMap;
import java.util.Map;

import org.exist.dom.persistent.NodeProxy;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author jmv
 */
public class ResourceSetHelper {

    /**
     * Computes the intersection of two resource sets.
     *
     * <p>For {@link LocalXMLResource} instances backed by persistent nodes,
     * intersection is determined by node identity (document ID + node ID).
     * For other resources, the resource ID ({@link Resource#getId()}) is used
     * as the identity key.</p>
     *
     * @param s1 the first resource set
     * @param s2 the second resource set
     * @return a new resource set containing only resources present in both sets
     * @throws XMLDBException if an error occurs accessing the resources
     */
    public static ResourceSet intersection(final ResourceSet s1, final ResourceSet s2) throws XMLDBException {
        // Build a map from identity key to resource for the first set
        final Map<String, Resource> m1 = buildIdentityMap(s1);

        // Build the intersection: for each resource in s2, check if it's also in s1.
        // Use the node identity key (not res.getId()) as the MapResourceSet key
        // to avoid collapsing multiple nodes from the same document into one entry.
        final Map<String, Resource> result = new LinkedHashMap<>();
        for (long i = 0; i < s2.getSize(); i++) {
            final Resource res = s2.getResource(i);
            final String key = getIdentityKey(res);
            if (m1.containsKey(key) && !result.containsKey(key)) {
                result.put(key, m1.get(key));
            }
        }

        return new MapResourceSet(result);
    }

    /**
     * Builds a map from identity key to resource.
     */
    private static Map<String, Resource> buildIdentityMap(final ResourceSet rs) throws XMLDBException {
        final Map<String, Resource> map = new LinkedHashMap<>();
        for (long i = 0; i < rs.getSize(); i++) {
            final Resource res = rs.getResource(i);
            final String key = getIdentityKey(res);
            map.putIfAbsent(key, res);
        }
        return map;
    }

    /**
     * Returns a key that uniquely identifies a resource by node identity.
     *
     * <p>For {@link LocalXMLResource} instances backed by a {@link NodeProxy},
     * the key combines the document ID and the node ID to uniquely identify
     * the node within the database. For other resources, the resource ID string
     * is used as a fallback.</p>
     */
    private static String getIdentityKey(final Resource res) throws XMLDBException {
        if (res instanceof LocalXMLResource localRes) {
            final NodeProxy proxy = localRes.getNode();
            if (proxy != null) {
                return proxy.getOwnerDocument().getDocId() + "#" + proxy.getNodeId();
            }
        }
        return res.getId();
    }
}
