/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xquery;

import org.exist.dom.QName;

/**
 * Defines an XQuery library module. A module consists of function definitions
 * and global variables. It is uniquely identified by a namespace URI and an optional
 * default namespace prefix. All functions provided by the module have to be defined 
 * in the module's namespace.
 * 
 * Modules can be either internal or external: internal modules are collections of Java
 * classes, each being a subclass of {@link org.exist.xquery.Function}. External modules
 * are defined by the XQuery "module" directive and can be loaded with "import module".
 * 
 * Modules are dynamically loaded by class {@link org.exist.xquery.XQueryContext}, either
 * during the initialization phase of the query engine (for the standard library modules) or
 * upon an "import module" directive. 
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public interface Module {

	/**
	 * Returns the namespace URI that uniquely identifies this module.
	 * 
	 * @return
	 */
	public String getNamespaceURI();
	
	/**
	 * Returns an optional default prefix (used if no prefix is supplied with
	 * the "import module" directive).
	 * 
	 * @return
	 */
	public String getDefaultPrefix();
	
	/**
	 * Is this an internal module?
	 * 
	 * @return
	 */
	public boolean isInternalModule();
	
	/**
	 * Returns the signatures of all functions defined within this module.
	 * 
	 * @return
	 */
	public FunctionSignature[] listFunctions();
	
	/**
	 * Try to find the signature of the function identified by its QName.
	 * 
	 * @param qname
	 * @return the function signature or null if the function is not defined.
	 */
	public FunctionSignature getSignatureForFunction(QName qname);
	
	public Variable resolveVariable(QName qname) throws XPathException;
	
	public Variable declareVariable(QName qname, Object value) throws XPathException;
}
