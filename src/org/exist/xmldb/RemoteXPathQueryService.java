
package org.exist.xmldb;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;


public class RemoteXPathQueryService implements XPathQueryServiceImpl {

    protected int indentXML = 0;
    protected String encoding = "UTF-8";
    protected RemoteCollection collection;
	protected Hashtable namespaceMappings = new Hashtable(5);

    public RemoteXPathQueryService( RemoteCollection collection ) {
        this.collection = collection;
    }

	public ResourceSet query( String query ) throws XMLDBException {
		return query(query, null);
	}
	
    public ResourceSet query( String query, String sortExpr ) throws XMLDBException {
        if ( !( query.startsWith( "document(" ) || query.startsWith( "collection(" ) ||
        	query.startsWith("xcollection("))) {
            if ( collection.getName().equals( "/" ) )
                query = "document(*)" + query;
            else
                query = "collection('" + collection.getPath() + "')" + query;
        }
        try {
            Vector params = new Vector();
            params.addElement(query.getBytes("UTF-8"));
            if(sortExpr != null)
            	params.addElement(sortExpr.getBytes("UTF-8"));
            params.addElement(namespaceMappings);
            Hashtable result = (Hashtable) collection.getClient().execute( "queryP", params );
            Vector resources = (Vector)result.get("results");
            int handle = -1;
            if(resources != null && resources.size() > 0)
            	handle = ((Integer)result.get("id")).intValue();
            return new ResourceSetImpl( collection, resources, handle, indentXML, encoding );
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
        if(resource.id == null) {
            // resource is a document
            if(!(query.startsWith( "document(" ) || query.startsWith("collection(") ||
            	query.startsWith("xcollection("))) 
                query = "document('" + res.getDocumentId() + "')" + query;
            return query( query );
        }
        try {
            Vector params = new Vector();
            params.addElement( query.getBytes("UTF-8") );
            params.addElement( resource.path );
            params.addElement( resource.id );
            if(sortExpr != null)
            	params.addElement( sortExpr.getBytes("UTF-8") );
			params.addElement(namespaceMappings);
			Hashtable result = (Hashtable) collection.getClient().execute( "queryP", params );
			Vector resources = (Vector)result.get("results");
			int handle = -1;
			if(resources != null && resources.size() > 0)
				handle = ((Integer)result.get("id")).intValue();
			return new ResourceSetImpl( collection, resources, handle, indentXML, encoding );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }
    
    /**
     *  Description of the Method
     *
     *@param  resource            Description of the Parameter
     *@param  query               Description of the Parameter
     *@return                     Description of the Return Value
     *@exception  XMLDBException  Description of the Exception
     */
    public ResourceSet queryResource( String resource, String query ) throws XMLDBException {
        if ( !( query.startsWith( "document(" ) || query.startsWith( "collection(" ) ) )
            query = "document('" + resource + "')" + query;
        return query( query );
    }


    /**
     *  Gets the version attribute of the XPathQueryServiceImpl object
     *
     *@return                     The version value
     *@exception  XMLDBException  Description of the Exception
     */
    public String getVersion() throws XMLDBException {
        return "1.0";
    }


    /**
     *  Sets the collection attribute of the XPathQueryServiceImpl object
     *
     *@param  col                 The new collection value
     *@exception  XMLDBException  Description of the Exception
     */
    public void setCollection( Collection col ) throws XMLDBException {
    }


    /**
     *  Gets the name attribute of the XPathQueryServiceImpl object
     *
     *@return                     The name value
     *@exception  XMLDBException  Description of the Exception
     */
    public String getName() throws XMLDBException {
        return "XPathQueryService";
    }


    /**
     *  Gets the property attribute of the XPathQueryServiceImpl object
     *
     *@param  property            Description of the Parameter
     *@return                     The property value
     *@exception  XMLDBException  Description of the Exception
     */
    public String getProperty( String property ) throws XMLDBException {
        if ( property.equals( "pretty" ) )
            return ( indentXML == 1 ) ? "true" : "false";
        if ( property.equals( "encoding" ) )
            return encoding;
        return null;
    }


    /**
     *  Sets the property attribute of the XPathQueryServiceImpl object
     *
     *@param  property            The new property value
     *@param  value               The new property value
     *@exception  XMLDBException  Description of the Exception
     */
    public void setProperty( String property, String value ) throws XMLDBException {
        if ( property.equals( "pretty" ) )
            indentXML = ( value.equals( "true" ) ? 1 : -1 );
        if ( property.equals( "encoding" ) )
            encoding = value;
    }


    /**
     *  Description of the Method
     *
     *@exception  XMLDBException  Description of the Exception
     */
    public void clearNamespaces() throws XMLDBException {
    	namespaceMappings.clear();
    }


    /**
     *  Description of the Method
     *
     *@param  ns                  Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void removeNamespace( String ns ) throws XMLDBException {
        for(Iterator i = namespaceMappings.values().iterator(); i.hasNext(); ) {
        	if(((String)i.next()).equals(ns))
        		i.remove();
        }
    }


    /**
     *  Sets the namespace attribute of the XPathQueryServiceImpl object
     *
     *@param  prefix              The new namespace value
     *@param  namespace           The new namespace value
     *@exception  XMLDBException  Description of the Exception
     */
    public void setNamespace( String prefix, String namespace )
             throws XMLDBException {
        namespaceMappings.put(prefix, namespace);
    }


    /**
     *  Gets the namespace attribute of the XPathQueryServiceImpl object
     *
     *@param  prefix              Description of the Parameter
     *@return                     The namespace value
     *@exception  XMLDBException  Description of the Exception
     */
    public String getNamespace( String prefix ) throws XMLDBException {
        return (String)namespaceMappings.get(prefix);
    }
}

