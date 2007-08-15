/*
 *  eXist SQL Module Extension
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
 *  $Id: SQLModule.java 3933 2006-09-18 21:08:38 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

/**
 * eXist SQL Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows queries
 * against SQL Databases, returning an XML representation of the result set.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-09-24
 * @version 1.0
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */


public class SQLModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/sql";
	
	public final static String PREFIX = "sql";
	
	public final static String CONNECTIONS_CONTEXTVAR = "_eXist_sql_connections";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(GetConnectionFunction.signatures[0], GetConnectionFunction.class),
		new FunctionDef(GetConnectionFunction.signatures[1], GetConnectionFunction.class),
		new FunctionDef(GetConnectionFunction.signatures[2], GetConnectionFunction.class),
		new FunctionDef(ExecuteFunction.signatures[0], ExecuteFunction.class)
	};
	
	public SQLModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing SQL queries against Databases, returning XML representations of the result sets.";
	}
	
	/**
	 * Resets the Module Context and closes any JDBC connections for the XQueryContext
	 * 
	 * @param xqueryContext The XQueryContext
	 */
	public void reset(XQueryContext xqueryContext)
	{
		//reset the module context
		super.reset(xqueryContext);
		
		//close any open connections
		GetConnectionFunction.closeAll(xqueryContext);
	}
}
