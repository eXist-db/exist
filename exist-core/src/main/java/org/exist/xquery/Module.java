/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

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
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface Module {
	
	/**
	 * Returns the namespace URI that uniquely identifies this module.
	 * 
	 * @return namespace URI 
	 */
	public String getNamespaceURI();
	
	/**
	 * Returns an optional default prefix (used if no prefix is supplied with
	 * the "import module" directive).
	 * 
	 * @return optional default prefix 
	 */
	public String getDefaultPrefix();
	
	/**
	 * Return a short description of this module to be displayed to a user.
	 * 
	 * @return short description of this module
	 */
	public String getDescription();

	/**
	 * Returns the release version in which the module was firstly available.
	 * 
	 * @return available from which release version
	 */
	public String getReleaseVersion();

	
	/**
	 * Is this an internal module?
	 * 
	 * @return True if is internal module.
	 */
	public boolean isInternalModule();
	
	/**
	 * Returns the signatures of all functions defined within this module.
	 * 
	 * @return signatures of all functions
	 */
	public FunctionSignature[] listFunctions();
	
	/**
	 * Try to find the signature of the function identified by its QName.
	 * 
	 * @param qname the function name
	 * @return the function signature or null if the function is not defined.
	 */
	public Iterator<FunctionSignature> getSignaturesForFunction(QName qname);
	
	public Variable resolveVariable(QName qname) throws XPathException;
	
	public Variable declareVariable(QName qname, Object value) throws XPathException;
	
    public Variable declareVariable(Variable var);
    
    public boolean isVarDeclared(QName qname);
    
    /**
     * Returns an iterator over all global variables in this modules, which were
     * either declared with "declare variable" (for external modules) or set in the
     * module implementation (internal modules).
	 *
	 * @return an iterator over the names of the global variables
     */
    public Iterator<QName> getGlobalVariables();

	/**
	 * Reset the module's internal state for being reused.
	 *
	 * @param context the xquery context
	 *
	 * @deprecated use {@link #reset(XQueryContext, boolean)} instead
	 */
	@Deprecated
    void reset(XQueryContext context);

	/**
	 * Reset the module's internal state for being reused.
	 *
	 * @param xqueryContext the xquery context
	 * @param keepGlobals true to keep global declarations
	 */
	public void reset(XQueryContext xqueryContext, boolean keepGlobals);

    /**
     * Check if this module has been fully loaded
     * and is ready for use.
     *
     * @return false while the module is being compiled.
     */
    public boolean isReady();

    void setContextItem(Sequence contextItem);
}
