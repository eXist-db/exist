
package org.exist.xmldb;

import java.io.IOException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class RemoteResourceSet implements ResourceSet {

    protected RemoteCollection collection;
    protected int handle = -1;
    protected Vector resources;

    protected XmlRpcClient rpcClient;

    public RemoteResourceSet( RemoteCollection col ) {
        this.collection = col;
        resources = new Vector();
    }

    public RemoteResourceSet( RemoteCollection col, Vector resources, int handle ) {
        this.handle = handle;
        this.resources = resources;
        this.collection = col;
    }

    public void addResource( Resource resource ) {
        resources.addElement( resource );
    }

    public void clear() throws XMLDBException {
        resources.clear();
    }

    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }

    public ResourceIterator getIterator( long start ) throws XMLDBException {
        return new NewResourceIterator( start );
    }


    public Resource getMembersAsResource() throws XMLDBException {
    	// TODO: implement this
        throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
    }

    public Resource getResource( long pos ) throws XMLDBException {
        if ( pos >= resources.size() )
            return null;
        // node or value?
        if ( resources.elementAt( (int) pos ) instanceof Vector ) {
            // node
            Vector v = (Vector) resources.elementAt( (int) pos );
            String doc = (String) v.elementAt( 0 );
            String s_id = (String) v.elementAt( 1 );
			String path = doc.substring(0, doc.lastIndexOf('/'));
			RemoteCollection parent = 
				new RemoteCollection(collection.getClient(), null, path);
			parent.properties = collection.properties;
            XMLResource res =
                new RemoteXMLResource( parent, handle,
                	(int)pos, doc, s_id );
            return res;
        } else if ( resources.elementAt( (int) pos ) instanceof Resource )
            return (Resource) resources.elementAt( (int) pos );
        else {
            // value
            XMLResource res = new RemoteXMLResource( collection, handle, (int)pos, 
            	Long.toString( pos ), null );
            res.setContent( resources.elementAt( (int) pos ) );
            return res;
        }
    }

    public long getSize() throws XMLDBException {
        return resources == null ? 0 : (long) resources.size();
    }

    public void removeResource( long pos ) throws XMLDBException {
        resources.removeElementAt( (int) pos );
    }

    class NewResourceIterator implements ResourceIterator {

        long pos = 0;

        public NewResourceIterator() { }

        public NewResourceIterator( long start ) {
            pos = start;
        }

        public boolean hasMoreResources() throws XMLDBException {
            return resources == null ? false : pos < resources.size();
        }

        public Resource nextResource() throws XMLDBException {
            return getResource( pos++ );
        }
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize() throws Throwable {
		System.err.println("releasing query results");
		try {
			Vector params = new Vector(1);
			params.addElement(new Integer(handle));
			rpcClient.execute("releaseQueryResult", params);
		} catch(XmlRpcException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

}

