
package org.exist.xmldb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import javax.xml.transform.OutputKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.VirtualTempFile;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

public class RemoteResourceSet implements ResourceSet {

    protected RemoteCollection collection;
    protected int handle = -1;
    protected int hash = -1;
    protected List<Object> resources;
    protected Properties outputProperties;
    
    private static Logger LOG = LogManager.getLogger(RemoteResourceSet.class.getName());

    public RemoteResourceSet(RemoteCollection col, Properties properties, Object[] resources, int handle, int hash) {
        this.handle = handle;
        this.hash = hash;
        this.resources = new ArrayList<Object>(resources.length);
        for (int i = 0; i < resources.length; i++) {
            this.resources.add(resources[i]);
        }
        this.collection = col;
        this.outputProperties = properties;
    }

    public void addResource( Resource resource ) {
        resources.add( resource );
    }

    public void clear() throws XMLDBException {
        if (handle < 0)
            {return;}
        final List<Object> params = new ArrayList<Object>(1);
    	params.add(Integer.valueOf(handle));
        if (hash > -1)
            params.add(Integer.valueOf(hash));
        try {
            collection.getClient().execute("releaseQueryResult", params);
        } catch (final XmlRpcException e) {
            System.err.println("Failed to release query result on server: " + e.getMessage());
        }
        handle = -1;
        hash = -1;
        resources.clear();
    }

    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }

    public ResourceIterator getIterator( long start ) throws XMLDBException {
        return new NewResourceIterator( start );
    }


    public Resource getMembersAsResource() throws XMLDBException {
        final List<Object> params = new ArrayList<Object>(2);
    	params.add(Integer.valueOf(handle));
    	params.add(outputProperties);
		
    	try {


    		VirtualTempFile vtmpfile=null;
		try {
			vtmpfile = new VirtualTempFile();
			vtmpfile.setTempPrefix("eXistRRS");
			vtmpfile.setTempPostfix(".xml");
			
			Map<?,?> table = (Map<?,?>) collection.getClient().execute("retrieveAllFirstChunk", params);
			
			long offset = ((Integer)table.get("offset")).intValue();
			byte[] data = (byte[])table.get("data");
			boolean isCompressed="yes".equals(outputProperties.getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no"));
			// One for the local cached file
			Inflater dec = null;
			byte[] decResult = null;
			int decLength = 0;
			if(isCompressed) {
				dec = new Inflater();
				decResult = new byte[65536];
				dec.setInput(data);
				do {
					decLength = dec.inflate(decResult);
					vtmpfile.write(decResult,0,decLength);
				} while(decLength==decResult.length || !dec.needsInput());
			} else {
				vtmpfile.write(data);
			}
			while(offset > 0) {
				params.clear();
				params.add(table.get("handle"));
				params.add(Long.toString(offset));
				table = (Map<?,?>) collection.getClient().execute("getNextExtendedChunk", params);
				offset = Long.valueOf((String)table.get("offset")).longValue();
				data = (byte[])table.get("data");
				// One for the local cached file
				if(isCompressed) {
					dec.setInput(data);
					do {
						decLength = dec.inflate(decResult);
						vtmpfile.write(decResult,0,decLength);
					} while(decLength==decResult.length || !dec.needsInput());
				} else {
					vtmpfile.write(data);
				}
			}
			if(dec!=null)
				{dec.end();}
			
			final RemoteXMLResource res = new RemoteXMLResource( collection, handle, 0, XmldbURI.EMPTY_URI, null );
			res.setContent( vtmpfile );
			res.setProperties(outputProperties);
			return res;
		} catch (final XmlRpcException xre) {
			final byte[] data = (byte[]) collection.getClient().execute("retrieveAll", params);
			String content;
			try {
				content = new String(data, outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8"));
			} catch (final UnsupportedEncodingException ue) {
				LOG.warn(ue);
				content = new String(data);
			}
			final RemoteXMLResource res = new RemoteXMLResource( collection, handle, 0,
	            	XmldbURI.EMPTY_URI, null );
	        res.setContent( content );
	        res.setProperties(outputProperties);
	        return res;
		} catch (final IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		} catch (final DataFormatException dfe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, dfe.getMessage(), dfe);
		} finally {
			if(vtmpfile!=null) {
				try {
					vtmpfile.close();
				} catch(final IOException ioe) {
					//IgnoreIT(R)
				}
			}
		}

		
	
	} catch (final XmlRpcException xre) {
		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
	}
    }

    @Override
    public Resource getResource(final long pos) throws XMLDBException {
        if(pos >= resources.size()) {
            return null;
        }
        
        // node or value?
        if(resources.get((int)pos) instanceof Object[]) {
            // node
            final Object[] v = (Object[]) resources.get( (int) pos );
            final String doc = (String) v[0];
            final String s_id = (String) v[1];
            final XmldbURI docUri;
            try {
            	docUri = XmldbURI.xmldbUriFor(doc);
            } catch(final URISyntaxException e) {
            	throw new XMLDBException(ErrorCodes.INVALID_URI,e.getMessage(),e);
            }
            
            final RemoteCollection parent;
            if(docUri.startsWith(XmldbURI.DB)) {
                parent = RemoteCollection.instance(collection.getClient(), docUri.removeLastSegment());
            } else {
                //fake to provide a RemoteCollection for local files that have been transferred by xml-rpc
                parent = collection;
            }
             
            
            parent.properties = outputProperties;
            final RemoteXMLResource res = new RemoteXMLResource(parent, handle, (int)pos, docUri, s_id );
            res.setProperties(outputProperties);
            return res;
        } else if (resources.get((int)pos) instanceof Resource) {
            return (Resource)resources.get((int)pos);
        } else {
            // value
            final RemoteXMLResource res = new RemoteXMLResource(collection, handle, (int)pos, XmldbURI.create(Long.toString(pos)), null);
            res.setContent(resources.get((int)pos));
            res.setProperties(outputProperties);
            return res;
        }
    }

    public long getSize() throws XMLDBException {
        return resources == null ? 0 : (long) resources.size();
    }

    public void removeResource( long pos ) throws XMLDBException {
        resources.get( (int) pos );
    }

    protected void finalize() throws Throwable {
        try {
            clear();
        } finally {
            super.finalize();
        }
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

