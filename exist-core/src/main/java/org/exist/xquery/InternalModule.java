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
package org.exist.xquery;

import java.util.List;

import org.exist.dom.QName;

/**
 * Defines an internal module implemented in Java. The class maintains a collection of 
 * Java classes each being a subclass of {@link org.exist.xquery.Function}. For internal
 * modules, a new function object is created from its class for each function reference in the
 * XQuery script.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface InternalModule extends Module {

	/**
	 * Prepare the module for use.
	 *
	 * @param context The XQuery Context.
	 *
	 * @throws XPathException if an XPath error occurs
	 */
	default void prepare(final XQueryContext context) throws XPathException {
		// no-op
	}

	/**
	 * Returns the implementing class for the function identified
	 * by qname or null if it is not defined. Called by
	 * {@link FunctionFactory}.
	 * 
	 * @param qname function QName to get definition for
	 * @param argCount arity of the function
	 * @return implementing class for the function
	 */
	FunctionDef getFunctionDef(QName qname, int argCount);
	
	/**
	 * Returns all functions defined in this module matching the
	 * specified qname.
	 * 
	 * @param qname function QName to match
	 * @return all functions defined in this module
	 */
	List<FunctionSignature> getFunctionsByName(QName qname);
}