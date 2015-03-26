/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import java.io.Writer;

import org.exist.source.Source;
import org.exist.xquery.XPathException;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * Execute XQuery expressions on the database.
 * 
 * This interface is similar to {@link org.xmldb.api.modules.XPathQueryService}, but
 * provides additional methods to compile an XQuery into an internal representation, which
 * can be executed repeatedly. Since XQuery scripts can be very large, compiling an expression
 * in advance can save a lot of time.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public interface XQueryService extends org.xmldb.api.modules.XQueryService {
	
	/**
	 * Execute the specified query and return the results as a {@link ResourceSet}.
	 * 
	 * @param query
	 * @throws XMLDBException
	 */
	public ResourceSet query(String query) throws XMLDBException;
	
	/**
	 * Process a query based on the result of a previous query.
	 * The XMLResource contains the result received from a previous
	 * query.
	 * 
	 * @param res an XMLResource as obtained from a previous query.
	 * @param query the XPath query
	 */
	public ResourceSet query( XMLResource res, String query )
	throws XMLDBException;
	
	/**
	 * Compiles the specified XQuery and returns a handle to the compiled
	 * code, which can then be passed to {@link #execute(CompiledExpression)}.
     *
     * Note: {@link CompiledExpression} is not thread safe. Please make sure you don't
     * call the same compiled expression from two threads at the same time.
	 *  
	 * @param query
	 * @throws XMLDBException
	 */
	public CompiledExpression compile(String query) throws XMLDBException;
	
    /**
     * Tries to compile the specified XQuery and returns a handle to the compiled
     * code, which can then be passed to {@link #execute(CompiledExpression)}.
     * If a static error is detected, an {@link XPathException} will be thrown.
     * 
     * @param query
     */
	public CompiledExpression compileAndCheck(String query) throws XMLDBException, XPathException;
    
	public ResourceSet execute(Source source) throws XMLDBException;
	
	/**
	 * Execute a compiled XQuery.
	 * 
	 * The implementation should pass all namespaces and variables declared through
	 * {@link XQueryService} to the compiled XQuery code.
	 *
     * Note: {@link CompiledExpression} is not thread safe. Please make sure you don't
     * call the same compiled expression from two threads at the same time.
	 * @param expression
	 * @throws XMLDBException
	 */
	public ResourceSet execute(CompiledExpression expression) throws XMLDBException;
	
	public ResourceSet execute(XMLResource res, CompiledExpression expression) throws XMLDBException;
	
	/**
	* Returns the URI string associated with <code>prefix</code> from
	* the internal namespace map. If <code>prefix</code> is null or empty the
	* URI for the default namespace will be returned. If a mapping for the
	* <code>prefix</code> can not be found null is returned.
	*
	* @param prefix The prefix to retrieve from the namespace map.
	* @return The URI associated with <code>prefix</code>
	* @throws XMLDBException with expected error codes.<br />
	*  <code>ErrorCodes.VENDOR_ERROR</code> for any vendor
	*  specific errors that occur.<br />
	*/
	public String getNamespace(String prefix) throws XMLDBException;
	
	/**
	* Sets a namespace mapping in the internal namespace map used to evaluate
	* queries. If <code>prefix</code> is null or empty the default namespace is
	* associated with the provided URI. A null or empty <code>uri</code> results
	* in an exception being thrown.
	*
	* @param prefix The prefix to set in the map. If
	*  <code>prefix</code> is empty or null the
	*  default namespace will be associated with the provided URI.
	* @param namespace The URI for the namespace to be associated with prefix.
	* @throws XMLDBException with expected error codes.<br />
	*  <code>ErrorCodes.VENDOR_ERROR</code> for any vendor
	*  specific errors that occur.<br />
	*  TODO: probably need some special error here.
	*/
	public void setNamespace(String prefix, String namespace) throws XMLDBException;
	
	/**
	* Removes the namespace mapping associated with <code>prefix</code> from
	* the internal namespace map. If <code>prefix</code> is null or empty the
	* mapping for the default namespace will be removed.
	*
	* @param ns The prefix to remove from the namespace map. If
	*  <code>prefix</code> is null or empty the mapping for the default
	*  namespace will be removed.
	* @throws XMLDBException with expected error codes.<br />
	*  <code>ErrorCodes.VENDOR_ERROR</code> for any vendor
	*  specific errors that occur.<br />
	*/
	public void removeNamespace(String ns) throws XMLDBException;

	/**
	 * Declare a global, external XQuery variable and assign a value to it. The variable
	 * has the same status as a variable declare through the <code>declare variable</code>
	 * statement in the XQuery prolog.
	 * 
	 * The variable can be referenced inside the XQuery expression as
	 * <code>$variable</code>. For example, if you declare a variable with
	 * 
	 * <pre>
	 * 	declareVariable("name", "HAMLET");
	 * </pre>
	 * 
	 * you may use the variable in an XQuery expression as follows:
	 * 
	 * <pre>
	 * 	//SPEECH[SPEAKER=$name]
	 * </pre>
	 * 
	 * Any Java object may be passed as initial value. The implementation will try
	 * to map the Java object into a corresponding XQuery value. In particular, all
	 * basic Java types (Integer, Long, Double, String) should be mapped into the corresponding XML 
	 * Schema atomic types. A Java array is mapped to an XQuery sequence. The implemenation
	 * should also recognize all DOM node types.
	 * 
	 * As a special case, an XMLResource as obtained from a {@link ResourceSet} will be 
	 * converted into a node.
	 *  
	 * @param qname a valid QName by which the variable is identified. Any
	 * prefix should have been mapped to a namespace, using {@link #setNamespace(String, String)}.
	 * For example, if a variable is called <b>x:name</b>, a prefix/namespace mapping should have
	 * been defined for prefix <code>x</code> before calling this method.
	 * 
	 * @param initialValue the initial value, which is assigned to the variable
	 * 
	 * @throws XMLDBException
	 */
	public void declareVariable(String qname, Object initialValue) throws XMLDBException;
	
	/**
	 * Enable or disable XPath 1.0 compatibility mode. In XPath 1.0
	 * compatibility mode, some XQuery expressions will behave different.
	 * In particular, additional automatic type conversions will be applied
	 * to the operands of numeric operators.
	 * 
	 * @param backwardsCompatible
	 */
	public void setXPathCompatibility(boolean backwardsCompatible);
	
	public void setModuleLoadPath(String path);
	
	/**
	 * Return a diagnostic dump of the query. The query should have been executed
	 * before calling this function.
	 * 
	 * @param expression
	 * @param writer
	 * @throws XMLDBException
	 */
	public void dump(CompiledExpression expression, Writer writer) throws XMLDBException;
}
