
package org.exist.xmldb;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.xmlrpc.RpcAPI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;


public class RemoteXPathQueryService implements XPathQueryServiceImpl, XQueryService {

    protected RemoteCollection collection;
	protected Hashtable namespaceMappings = new Hashtable(5);

    public RemoteXPathQueryService( RemoteCollection collection ) {
        this.collection = collection;
    }

	public ResourceSet query( String query ) throws XMLDBException {
		return query(query, null);
	}
	
    public ResourceSet query( String query, String sortExpr ) throws XMLDBException {
        try {
        	Hashtable optParams = new Hashtable();
            if(sortExpr != null)
            	optParams.put(RpcAPI.SORT_EXPR, sortExpr);
            optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            optParams.put(RpcAPI.BASE_URI, collection.getPath());
			Vector params = new Vector();
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(optParams);
            Hashtable result = (Hashtable) collection.getClient().execute( "queryP", params );
            Vector resources = (Vector)result.get("results");
            int handle = -1;
            if(resources != null && resources.size() > 0)
            	handle = ((Integer)result.get("id")).intValue();
            return new RemoteResourceSet( collection, resources, handle );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }

	public ResourceSet query( XMLResource res, String query )
		throws XMLDBException {
			return query(res, query, null);
	}
		
    public ResourceSet query( XMLResource res, String query, String sortExpr )
        throws XMLDBException {
        RemoteXMLResource resource = (RemoteXMLResource)res;
        try {
        	Hashtable optParams = new Hashtable();
        	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        	if(sortExpr != null)
        		optParams.put(RpcAPI.SORT_EXPR, sortExpr);
			optParams.put(RpcAPI.BASE_URI, collection.getPath());
            Vector params = new Vector();
            params.addElement( query.getBytes("UTF-8") );
            params.addElement( resource.path );
            params.addElement( resource.id );
            params.addElement( optParams );
			Hashtable result = (Hashtable) collection.getClient().execute( "queryP", params );
			Vector resources = (Vector)result.get("results");
			int handle = -1;
			if(resources != null && resources.size() > 0)
				handle = ((Integer)result.get("id")).intValue();
			return new RemoteResourceSet( collection, resources, handle );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }
    
    public ResourceSet queryResource( String resource, String query ) throws XMLDBException {
        return query( query );
    }

    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    public void setCollection( Collection col ) throws XMLDBException {
    }

    public String getName() throws XMLDBException {
        return "XPathQueryService";
    }

    public String getProperty( String property ) throws XMLDBException {
    	return collection.getProperty(property);
    }

    public void setProperty( String property, String value ) throws XMLDBException {
        collection.setProperty(property, value);
    }

    public void clearNamespaces() throws XMLDBException {
    	namespaceMappings.clear();
    }

    public void removeNamespace( String ns ) throws XMLDBException {
        for(Iterator i = namespaceMappings.values().iterator(); i.hasNext(); ) {
        	if(((String)i.next()).equals(ns))
        		i.remove();
        }
    }

    public void setNamespace( String prefix, String namespace )
             throws XMLDBException {
        namespaceMappings.put(prefix, namespace);
    }

    public String getNamespace( String prefix ) throws XMLDBException {
        return (String)namespaceMappings.get(prefix);
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XPathQueryServiceImpl#declareVariable(java.lang.String, java.lang.Object)
	 */
	public void declareVariable(String qname, Object initialValue) throws XMLDBException {
		// TODO Not implemented
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED,
			"method not implemented");
	}

	/**
	 * The XML-RPC server automatically caches compiled queries.
	 * Thus calling this method has no effect.
	 * 
	 * @see org.exist.xmldb.XQueryService#compile(java.lang.String)
	 */
	public CompiledExpression compile(String query) throws XMLDBException {
		return new RemoteCompiledExpression(query);
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#execute(org.exist.xmldb.CompiledExpression)
	 */
	public ResourceSet execute(CompiledExpression expression) throws XMLDBException {
		return query(((RemoteCompiledExpression)expression).getQuery());
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#setXPathCompatibility(boolean)
	 */
	public void setXPathCompatibility(boolean backwardsCompatible) {
		// TODO: not passed
	}

	/** 
	 * Calling this method has no effect. The server loads modules
	 * relative to its own context.
	 * 
	 * @see org.exist.xmldb.XQueryService#setModuleLoadPath(java.lang.String)
	 */
	public void setModuleLoadPath(String path) {		
	}
}

