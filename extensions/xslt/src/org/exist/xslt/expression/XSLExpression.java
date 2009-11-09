/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2009 The eXist Project
 *  http://exist-db.org
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
package org.exist.xslt.expression;

import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.XPathException;
import org.exist.xslt.compiler.Names;
import org.w3c.dom.Attr;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface XSLExpression extends Names {
	
	public void setToDefaults();

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException;
	public void validate() throws XPathException;
	
	public void compileError(String error) throws XPathException;

	public Boolean getBoolean(String value) throws XPathException;
	
}
