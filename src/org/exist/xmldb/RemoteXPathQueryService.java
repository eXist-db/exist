
package org.exist.xmldb;

import java.io.IOException;
import java.io.Writer;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.source.Source;
import org.exist.xmlrpc.RpcAPI;
import org.exist.xquery.XPathException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class RemoteXPathQueryService implements XPathQueryServiceImpl, XQueryService {

    protected RemoteCollection collection;
	protected Hashtable namespaceMappings = new Hashtable(5);
	protected Hashtable variableDecls = new Hashtable();
	protected Properties outputProperties = null;
	
    public RemoteXPathQueryService( RemoteCollection collection ) {
        this.collection = collection;
        this.outputProperties = new Properties(collection.properties);
    }

	public ResourceSet query( String query ) throws XMLDBException {
		return query(query, null);
	}
	
    public ResourceSet query( String query, String sortExpr ) throws XMLDBException {
        try {
        	Hashtable optParams = new Hashtable();
            if(sortExpr != null)
            	optParams.put(RpcAPI.SORT_EXPR, sortExpr);
            if(namespaceMappings.size() > 0)
            	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            if(variableDecls.size() > 0)
            	optParams.put(RpcAPI.VARIABLES, variableDecls);
            optParams.put(RpcAPI.BASE_URI, 
                    outputProperties.getProperty("base-uri", collection.getPath()));
			Vector params = new Vector();
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(optParams);
            Hashtable result = (Hashtable) collection.getClient().execute( "queryP", params );
            
            if(result.get(RpcAPI.ERROR) != null)
            	throwException(result);
            
            Vector resources = (Vector)result.get("results");
            int handle = -1;
            if(resources != null && resources.size() > 0)
            	handle = ((Integer)result.get("id")).intValue();
            return new RemoteResourceSet( collection, outputProperties, resources, handle );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }

    public CompiledExpression compile(String query) throws XMLDBException {
        try {
            return compileAndCheck(query);
        } catch (XPathException e) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, e.getMessage(), e );
        }
    }
    
    public CompiledExpression compileAndCheck(String query) throws XMLDBException, XPathException {
        try {
            Hashtable optParams = new Hashtable();
            if(namespaceMappings.size() > 0)
                optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            if(variableDecls.size() > 0)
                optParams.put(RpcAPI.VARIABLES, variableDecls);
            optParams.put(RpcAPI.BASE_URI, 
                    outputProperties.getProperty("base-uri", collection.getPath()));
            Vector params = new Vector();
            params.addElement(query.getBytes("UTF-8"));
            params.addElement(optParams);
            Hashtable result = (Hashtable) collection.getClient().execute( "compile", params );
            
            if (result.get(RpcAPI.ERROR) != null)
                throwXPathException(result);
            return new RemoteCompiledExpression(query);
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }
    
    /**
	 * @param result
     * @throws XPathException 
	 */
	private void throwException(Hashtable result) throws XMLDBException {
		String message = (String)result.get(RpcAPI.ERROR);
		Integer lineInt = (Integer)result.get(RpcAPI.LINE);
		Integer columnInt = (Integer)result.get(RpcAPI.COLUMN);
		int line = lineInt == null ? 0 : lineInt.intValue();
		int column = columnInt == null ? 0 : columnInt.intValue();
		XPathException cause = new XPathException(message, line, column);
		throw new XMLDBException(ErrorCodes.VENDOR_ERROR, message, cause);
	}

    private void throwXPathException(Hashtable result) throws XPathException {
        String message = (String)result.get(RpcAPI.ERROR);
        Integer lineInt = (Integer)result.get(RpcAPI.LINE);
        Integer columnInt = (Integer)result.get(RpcAPI.COLUMN);
        int line = lineInt == null ? 0 : lineInt.intValue();
        int column = columnInt == null ? 0 : columnInt.intValue();
        throw new XPathException(message, line, column);
    }
    
	/* (non-Javadoc)
     * @see org.exist.xmldb.XQueryService#execute(org.exist.source.Source)
     */
    public ResourceSet execute(Source source) throws XMLDBException {
        try {
            String xq = source.getContent();
            return query(xq, null);
        } catch (IOException e) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, e.getMessage(), e );
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
        	if(namespaceMappings.size() > 0)
            	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            if(variableDecls.size() > 0)
            	optParams.put(RpcAPI.VARIABLES, variableDecls);
        	if(sortExpr != null)
        		optParams.put(RpcAPI.SORT_EXPR, sortExpr);
			optParams.put(RpcAPI.BASE_URI, 
                    outputProperties.getProperty("base-uri", collection.getPath()));
            Vector params = new Vector();
            params.addElement( query.getBytes("UTF-8") );
            params.addElement( resource.path.toString() );
            if(resource.id == null)
            	params.addElement("");
            else
            	params.addElement( resource.id );
            params.addElement( optParams );
			Hashtable result = (Hashtable) collection.getClient().execute( "queryP", params );
			
			if(result.get(RpcAPI.ERROR) != null)
            	throwException(result);
			
			Vector resources = (Vector)result.get("results");
			int handle = -1;
			if(resources != null && resources.size() > 0)
				handle = ((Integer)result.get("id")).intValue();
			return new RemoteResourceSet( collection, outputProperties, resources, handle );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }
    
    public ResourceSet queryResource( String resource, String query ) throws XMLDBException {
    	Resource res = collection.getResource(resource);
    	if(res == null)
    		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + " not found");
    	if(!"XMLResource".equals(res.getResourceType()))
    		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + 
    				" is not an XML resource");
        return query( (XMLResource)res, query );
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
    	return outputProperties.getProperty(property);
    }

    public void setProperty( String property, String value ) throws XMLDBException {
        outputProperties.setProperty(property, value);
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
    	if (prefix == null)
    		prefix = "";
        namespaceMappings.put(prefix, namespace);
    }

    public String getNamespace( String prefix ) throws XMLDBException {
    	if (prefix == null)
    		prefix = "";
        return (String)namespaceMappings.get(prefix);
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XPathQueryServiceImpl#declareVariable(java.lang.String, java.lang.Object)
	 */
	public void declareVariable(String qname, Object initialValue) throws XMLDBException {
		variableDecls.put(qname, initialValue);
	}
    
	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#execute(org.exist.xmldb.CompiledExpression)
	 */
	public ResourceSet execute(CompiledExpression expression) throws XMLDBException {
		return query(((RemoteCompiledExpression)expression).getQuery());
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#execute(org.xmldb.api.modules.XMLResource, org.exist.xmldb.CompiledExpression)
	 */
	public ResourceSet execute(XMLResource res, CompiledExpression expression)
			throws XMLDBException {
		return query(res, ((RemoteCompiledExpression)expression).getQuery());
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

    /* (non-Javadoc)
     * @see org.exist.xmldb.XQueryService#dump(org.exist.xmldb.CompiledExpression, java.io.Writer)
     */
    public void dump(CompiledExpression expression, Writer writer) throws XMLDBException {
        String query = ((RemoteCompiledExpression)expression).getQuery();
        Hashtable optParams = new Hashtable();
    	if(namespaceMappings.size() > 0)
        	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        if(variableDecls.size() > 0)
        	optParams.put(RpcAPI.VARIABLES, variableDecls);
        optParams.put(RpcAPI.BASE_URI, 
                outputProperties.getProperty("base-uri", collection.getPath()));
        Vector params = new Vector();
        params.addElement(query);
        params.addElement(optParams);
        try {
            String dump = (String)collection.getClient().execute("printDiagnostics", params);
            writer.write(dump);
        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch (IOException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.XPathQueryServiceImpl#beginProtected()
     */
    public void beginProtected() {
        // not yet supported
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.XPathQueryServiceImpl#endProtected()
     */
    public void endProtected() {
        // not yet supported
    }
}

