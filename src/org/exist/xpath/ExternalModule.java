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
package org.exist.xpath;

import org.exist.dom.QName;

/**
 * An external library module implemented in XQuery and loaded
 * through the "import module" directive.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public interface ExternalModule extends Module {

	/**
	 * Declare a new function. Called by the XQuery compiler
	 * when parsing a library module for every function declaration.
	 * 
	 * @param func
	 */
	public void declareFunction(UserDefinedFunction func);
	
	/**
	 * Try to find the function identified by qname. Returns null
	 * if the function is undefined.
	 * 
	 * @param qname
	 * @return
	 */
	public UserDefinedFunction getFunction(QName qname);
	
	public void declareVariable(QName qname, VariableDeclaration decl) throws XPathException;
}
