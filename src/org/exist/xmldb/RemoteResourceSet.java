
package org.exist.xmldb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.Vector;

import javax.xml.transform.OutputKeys;

import org.apache.xmlrpc.XmlRpcException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

public class RemoteResourceSet implements ResourceSet {

    protected RemoteCollection collection;
    protected int handle = -1;
    protected Vector resources;
    protected Properties outputProperties;

    public RemoteResourceSet( RemoteCollection col, Properties properties, Vector resources, int handle ) {
        this.handle = handle;
        this.resources = resources;
        this.collection = col;
        this.outputProperties = properties;
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
    	Vector params = new Vector();
    	params.addElement(new Integer(handle));
    	params.addElement(outputProperties);
    	try {
			byte[] data = (byte[]) collection.getClient().execute("retrieveAll", params);
			String content;
			try {
				content = new String(data, outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8"));
			} catch (UnsupportedEncodingException ue) {
				content = new String(data);
			}
			RemoteXMLResource res = new RemoteXMLResource( collection, handle, 0, 
	            	XmldbURI.create(""), null );
	        res.setContent( content );
	        res.setProperties(outputProperties);
	        return res;
		} catch (XmlRpcException xre) {
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
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
            XmldbURI docUri;
            try {
            	docUri = XmldbURI.xmldbUriFor(doc);
            } catch (URISyntaxException e) {
            	throw new XMLDBException(ErrorCodes.INVALID_URI,e.getMessage(),e);
            }
			RemoteCollection parent = 
				new RemoteCollection(collection.getClient(), null, docUri.removeLastSegment());
			parent.properties = outputProperties;
            RemoteXMLResource res =
                new RemoteXMLResource( parent, handle,
                	(int)pos, docUri, s_id );
            res.setProperties(outputProperties);
            return res;
        } else if ( resources.elementAt( (int) pos ) instanceof Resource )
            return (Resource) resources.elementAt( (int) pos );
        else {
            // value
            RemoteXMLResource res = new RemoteXMLResource( collection, handle, (int)pos, 
            	XmldbURI.create(Long.toString( pos )), null );
            res.setContent( resources.elementAt( (int) pos ) );
            res.setProperties(outputProperties);
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
}

