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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Implementation of ResourceSet (a container of Resource objects), using
 * internally both a Map and a Vector. The Map is keyed by the Id of each
 * resource.
 *
 * @author Jean-Marc Vanel (2 April 2003)
 */
public class MapResourceSet implements ResourceSet 
{
    private final Map<String, Resource> resources;
    private final List<Resource> resourcesVector = new ArrayList<>();

    public MapResourceSet() {
        this.resources = new HashMap<>();
    }

    public MapResourceSet(final Map<String, Resource> resources) {
        this.resources = resources;
        for (Resource res : resources.values()) {
            resourcesVector.add(res);
        }
    }

    public MapResourceSet(ResourceSet rs) throws XMLDBException {
        this.resources = new HashMap<>();
        for (int i = 0; i < rs.getSize(); i++) {
            final Resource res = rs.getResource(i);
            resources.put(res.getId(), res);
            resourcesVector.add(rs.getResource(i));
        }
    }

    public Map<String, Resource> getResourcesMap() {
        return resources;
    }

    /**
     * Adds a resource to the container
     *
     * @param resource The resource to be added to the object
     * @param resource the resource to add.
     *
     * @throws XMLDBException if an error occurs whilst add the resource.
     */
    @Override
    public void addResource(final Resource resource) throws XMLDBException {
        resources.put(resource.getId(), resource);
        resourcesVector.add(resource);
    }

    @Override
    public void addAll(final ResourceSet resourceSet) throws XMLDBException {
        for (long i = 0; i < resourceSet.getSize(); i++) {
            addResource(resourceSet.getResource(i));
        }
    }

    /**
     * Make the container empty
     *
     * @throws XMLDBException if an error occurs whilst clearing the resource set.
     */
    @Override
    public void clear() throws XMLDBException {
        resources.clear();
    }

    /**
     * Get an iterator over the resource set.
     *
     * @return The iterator
     *
     * @throws XMLDBException if an error occurs whilst getting the iterator.
     */
    @Override
    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }

    /**
     * Gets the iterator property, starting from a given position
     *
     * @param start starting position &gt; 0 for the iterator
     *
     * @return The iterator value
     *
     * @throws XMLDBException thrown if pos is out of range
     */
    public ResourceIterator getIterator(final long start) throws XMLDBException {
        return new NewResourceIterator(start);
    }

    /**
     * Gets the membersAsResource property of the object
     *
     * @return The membersAsResource value
     *
     * @throws XMLDBException Description of the Exception
     */
    @Override
    public Resource getMembersAsResource() throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
    }

    /**
     * Gets the resource at a given position.
     *
     * @param pos position &gt; 0
     * @return The resource value
     * @throws XMLDBException thrown if pos is out of range
     */
    @Override
    public Resource getResource(final long pos) throws XMLDBException {
        if (pos < 0 || pos >= resources.size()) {
            return null;
        }
        final Object r = resourcesVector.get((int) pos);
        if (r instanceof Resource) {
            return (Resource) r;
        }
        return null;
    }

    /**
     * Gets the size property.
     *
     * @return The size value
     *
     * @throws XMLDBException if an error occurs getting the size.
     */
    @Override
    public long getSize() throws XMLDBException {
        return (long) resources.size();
    }

    /**
     * Removes the resource at a given position.
     *
     * @param pos position &gt; 0
     * @throws XMLDBException thrown if pos is out of range
     */
    @Override
    public void removeResource(final long pos) throws XMLDBException {
        final Resource r = resourcesVector.get((int) pos);
        resourcesVector.remove((int) pos);
        resources.remove(r.getId());
    }

    /**
     * Inner resource Iterator Class
     *
     */
    class NewResourceIterator implements ResourceIterator {

        long pos = 0;

        /**
         * Constructor for the NewResourceIterator object
         */
        public NewResourceIterator() {
        }

        /**
         * Constructor for the NewResourceIterator object
         *
         * @param start starting position>0 for the iterator
         */
        public NewResourceIterator(long start) {
            pos = start;
        }

        /**
         * Classical loop test.
         *
         * @return Description of the Return Value
         * @throws XMLDBException Description of the Exception
         */
        @Override
        public boolean hasMoreResources() throws XMLDBException {
            return pos < resources.size();
        }

        /**
         * Classical accessor to next Resource
         *
         * @return the next Resource
         * @throws XMLDBException
         */
        @Override
        public Resource nextResource() throws XMLDBException {
            return getResource(pos++);
        }
    }
}
