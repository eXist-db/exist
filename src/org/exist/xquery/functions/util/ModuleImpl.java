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
package org.exist.xquery.functions.util;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ModuleImpl extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/util";
	
	public final static String PREFIX = "util";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(BuiltinFunctions.signature, BuiltinFunctions.class),
		new FunctionDef(DescribeFunction.signature, DescribeFunction.class),
		new FunctionDef(EvalFunction.signature, EvalFunction.class),
		new FunctionDef(MD5.signature, MD5.class),
		new FunctionDef(DocumentName.signature, DocumentName.class),
		new FunctionDef(DocumentId.signature, DocumentId.class),
		new FunctionDef(CollectionName.signature, CollectionName.class),
		new FunctionDef(LogFunction.signature, LogFunction.class)
	};
	
	public ModuleImpl() {
		super(functions);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}
}
