// $Header$

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
     protected Map resources = new HashMap();
	protected Vector resourcesVector = new Vector();

    public MapResourceSet() {
    }

    /**
     *  Constructor 
     */
    public MapResourceSet(Map resources) {
        this.resources = resources;
		Iterator iter = resources.values().iterator();
		while ( iter.hasNext() ) {
			Resource res = (Resource)iter.next();
			resourcesVector.add(res);
		}
	}

    /**
     *  Constructor 
     */
    public MapResourceSet(ResourceSet rs) throws XMLDBException {
        for ( int i=0; i<rs.getSize(); i++ ){
        	Resource res = rs.getResource( i );
            resources.put(res.getId(), res);
			resourcesVector.add( rs.getResource( i ) );           
        }
    }

    public Map getResourcesMap() {
        return resources;
    }

    /**
     *  Adds a resource to the container
     *
     *@param  resource  The resource to be added to the object
     */
    public void addResource( Resource resource ) throws XMLDBException {
		resources.put(resource.getId(), resource);
		resourcesVector.addElement( resource );
    }

    /**
     *  Make the container empty
     *
     *@exception  XMLDBException  
     */
    public void clear() throws XMLDBException {
        resources.clear();
    }

    /**
     *  Gets the iterator property
     *
     *@return                     The iterator value
     *@exception  XMLDBException
     */
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

    /**
     *  Gets the membersAsResource property of the object
     *
     *@return                     The membersAsResource value
     *@exception  XMLDBException  Description of the Exception
     */
    public Resource getMembersAsResource() throws XMLDBException {
        throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
    }

    /**
     *  Gets the resource at a given position.
     *
     *@param  pos             position > 0
     *@return                     The resource value
     *@exception  XMLDBException  thrown if pos is out of range
     */
   public Resource getResource( long pos ) throws XMLDBException {
        if ( pos < 0 || pos >= resources.size() )
                return null;
        Object r = resourcesVector.get( (int) pos );
        if(r instanceof Resource)
        	return (Resource)r;
         return null;
    }

    /**
     *  Gets the size property
     *
     *@return                     The size value
     *@exception  XMLDBException
     */
    public long getSize() throws XMLDBException {
        return (long) resources.size();
    }

    /**
      *  Removes the resource at a given position.
     *
     *@param  pos                 position > 0
      *@exception  XMLDBException  thrown if pos is out of range
    */
      public void removeResource( long pos ) throws XMLDBException {
		Resource r = (Resource)resourcesVector.get( (int) pos );
		resourcesVector.removeElementAt( (int) pos );
		resources.remove( r.getId()  );
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

        /**
         *  Classical loop test.
         *
         *@return                     Description of the Return Value
         *@exception  XMLDBException  Description of the Exception
         */
        public boolean hasMoreResources() throws XMLDBException {
            return pos < resources.size();
        }

        /**
         *  Classical accessor to next Resource
         *
         *@return                     the next Resource
         *@exception  XMLDBException
         */
        public Resource nextResource() throws XMLDBException {
            return getResource( pos++ );
        }
    }
}
