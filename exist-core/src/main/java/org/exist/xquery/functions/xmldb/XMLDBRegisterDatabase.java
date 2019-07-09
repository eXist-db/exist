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
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
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
	protected static final Logger logger = LogManager.getLogger(XMLDBRegisterDatabase.class);
	public final static FunctionSignature signature = new FunctionSignature(
			new QName("register-database", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Registers an XMLDB driver class with the XMLDB Database Manager. "
					+ "This is only required if you want to access a database instance different "
					+ "from the one that executes the XQuery.",
			new SequenceType[]{
                new FunctionParameterSequenceType("driver", Type.STRING, Cardinality.EXACTLY_ONE, "The DB driver"),
                new FunctionParameterSequenceType("create-db", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The flag to create the db if it does not exist")},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if successfully registered, false() otherwise")
           );

	public XMLDBRegisterDatabase(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)
			throws XPathException {
		final String driverName = args[0].getStringValue();
		final boolean createDatabase = args[1].effectiveBooleanValue();
		try {
			final Class<?> driver = Class.forName(driverName);
			final Database database = (Database) driver.newInstance();
			database.setProperty("create-database", createDatabase
					? "true"
					: "false");
			DatabaseManager.registerDatabase(database);
		} catch (final Exception e) {
			logger.error("failed to initiate XMLDB database driver: " + driverName);
            return BooleanValue.FALSE;
		}
		return BooleanValue.TRUE;
	}
}
