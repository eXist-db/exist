package org.exist.xmldb;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 * XPathQueryServiceImpl.java
 * 
 * @author wolf
 *
 */
public interface XPathQueryServiceImpl extends XPathQueryService {

	/**
	 * Process an XPath query based on the result of a previous query.
	 * The XMLResource contains the result received from a previous
	 * query.
	 */
    public ResourceSet query( XMLResource res, String query )
    throws XMLDBException;
    
    public ResourceSet query( XMLResource res, String query, String sortExpr)
    throws XMLDBException;
    
    public ResourceSet query( String query, String sortExpr)
    throws XMLDBException;
}
