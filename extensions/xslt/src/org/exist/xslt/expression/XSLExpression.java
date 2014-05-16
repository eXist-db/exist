/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.XPathException;
import org.exist.xslt.XSLContext;
import org.exist.xslt.compiler.Names;
import org.w3c.dom.Attr;

/**
 * The XSL expression interface.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface XSLExpression extends Names {
	
	/**
	 * Clean-up setting.
	 */
	public void setToDefaults();

	/**
	 * Collect expressions attributes' information.
	 * 
	 * @param context
	 * @param attr
	 * @throws XPathException
	 */
	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException;
	
	/**
	 * Validate structure and settings.
	 * 
	 * @throws XPathException
	 */
	public void validate() throws XPathException;
	
	/**
	 * Report error message.
	 * 
	 * @param code
	 * @throws XPathException
	 */
	public void compileError(String code) throws XPathException;
	
	/**
	 * Report error message.
	 * 
	 * @param code
	 * @param description
	 * @throws XPathException
	 */
	public void compileError(ErrorCode code, String description) throws XPathException;
	
	/**
	 * Process expression.
	 *  
	 * @param sequenceIterator
	 * @param context
	 * @deprecated Use {@link #process(XSLContext,SequenceIterator)} instead
	 */
	public void process(SequenceIterator sequenceIterator, XSLContext context);

	/**
	 * Process expression.
	 * 
	 * @param context
	 * @param sequenceIterator
	 */
	public void process(XSLContext context, SequenceIterator sequenceIterator);

	public Boolean getBoolean(String value) throws XPathException;
}
