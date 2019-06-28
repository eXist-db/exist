/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.oracle;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * eXist Oracle Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows execution of
 * PL/SQL Stored Procedures within an Oracle RDBMS, returning an XML representation
 * of the result set. In particular, this module gives access to a <code>ResultSet</code>
 * returned in an <code>OracleType.CURSOR</code>, functionality which is not provided by
 * the more generic SQL extension module.<b>Please note</b> that this module is
 * dependent on functionality contained within the SQL extension module and both modules
 * must be enabled in <code>conf.xml</code> for this module to function correctly.
 * 
 * @author <a href="mailto:robert.walpole@metoffice.gov.uk">Robert Walpole</a>
 * @serial 2010-03-23
 * @version 1.0
 * 
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */
public class OracleModule extends AbstractInternalModule{
	
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/oracle";
	
	public final static String PREFIX = "oracle";
	public final static String INCLUSION_DATE = "2010-03-23";
	public final static String RELEASED_IN_VERSION = "eXist-2.0";
	
	private final static FunctionDef[] functions = {
        new FunctionDef(ExecuteFunction.signatures[0], ExecuteFunction.class),
        new FunctionDef(ExecuteFunction.signatures[1], ExecuteFunction.class)
    };
	
	public OracleModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

	@Override
	public String getDefaultPrefix() {
		return PREFIX;
	}

	@Override
	public String getDescription() {
		return "A module for executing PL/SQL stored procedures against an Oracle Database where data is given in an Oracle cursor, returning an XML representations of the result set.";
	}

	@Override
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	@Override
	public String getReleaseVersion() {
		return RELEASED_IN_VERSION;
	}

}
