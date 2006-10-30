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
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

/**
 * Register an XMLDB driver with the XMLDB DatabaseManager.
 * 
 * @author wolf
 */
public class XMLDBRegisterDatabase extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("register-database", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Register an XMLDB driver class with the XMLDB Database Manager. "
					+ "This is only required if you want to access a database instance different "
					+ "from the one that executes the XQuery.",
			new SequenceType[]{
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	/**
	 * @param context
	 */
	public XMLDBRegisterDatabase(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)
			throws XPathException {
		String driverName = args[0].getStringValue();
		boolean createDatabase = args[1].effectiveBooleanValue();
		try {
			Class driver = Class.forName(driverName);
			Database database = (Database) driver.newInstance();
			database.setProperty("create-database", createDatabase
					? "true"
					: "false");
			DatabaseManager.registerDatabase(database);
		} catch (Exception e) {
			LOG.warn("failed to initiate XMLDB database driver: " + driverName,
					e);
			return BooleanValue.FALSE;
		}
		return BooleanValue.TRUE;
	}
}
