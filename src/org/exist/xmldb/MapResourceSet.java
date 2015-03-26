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
import java.util.Vector;

import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *  Implementation of ResourceSet (a container of Resource objects), using internally both a Map and a Vector.
 *  The Map is keyed by the Id of each resource.
 * 
 *@author     Jean-Marc Vanel (2 April 2003)
 */
public class MapResourceSet implements ResourceSet 
{
     protected Map<String, Resource> resources = new HashMap<String, Resource>();
	protected Vector<Resource> resourcesVector = new Vector<Resource>();

    public MapResourceSet() {
    }

    /**
     *  Constructor 
     */
    public MapResourceSet(Map<String, Resource> resources) {
        this.resources = resources;
		final Iterator<Resource> iter = resources.values().iterator();
		while ( iter.hasNext() ) {
			final Resource res = iter.next();
			resourcesVector.add(res);
		}
	}

    /**
     *  Constructor 
     */
    public MapResourceSet(ResourceSet rs) throws XMLDBException {
        for ( int i=0; i<rs.getSize(); i++ ){
        	final Resource res = rs.getResource( i );
            resources.put(res.getId(), res);
			resourcesVector.add( rs.getResource( i ) );           
        }
    }

    public Map<String, Resource> getResourcesMap() {
        return resources;
    }

    @Override
    public void addResource(final Resource resource) throws XMLDBException {
        resources.put(resource.getId(), resource);
        resourcesVector.addElement(resource);
    }

    @Override
    public void clear() throws XMLDBException {
        resources.clear();
    }

    @Override
    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }

    /**
     *  Gets the iterator property, starting from a given position
     *
     *@param  start            starting position>0 for the iterator
     *@return                     The iterator value
     *@exception  XMLDBException   thrown if pos is out of range
     */
    public ResourceIterator getIterator( long start ) throws XMLDBException {
        return new NewResourceIterator( start );
    }

    @Override
    public Resource getMembersAsResource() throws XMLDBException {
        throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
    }

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

    @Override
    public long getSize() throws XMLDBException {
        return (long) resources.size();
    }

    @Override
    public void removeResource(final long pos) throws XMLDBException {
        final Resource r = resourcesVector.get((int) pos);
        resourcesVector.remove((int) pos);
        resources.remove(r.getId());
    }

    /**
     *  Inner resource Iterator Class
     *
     */
    class NewResourceIterator implements ResourceIterator {

        long pos = 0;

        /**  Constructor for the NewResourceIterator object */
        public NewResourceIterator() { }


        /**
         *  Constructor for the NewResourceIterator object
         *
         *@param  start  starting position>0 for the iterator 
         */
        public NewResourceIterator( long start ) {
            pos = start;
        }

        @Override
        public boolean hasMoreResources() throws XMLDBException {
            return pos < resources.size();
        }

        @Override
        public Resource nextResource() throws XMLDBException {
            return getResource( pos++ );
        }
    }
}
