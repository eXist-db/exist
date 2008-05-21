/*
 *  eXist SQL Module Extension GetJNDIConnectionFunction
 *  Copyright (C) 2008 Adam Retter <adam@exist-db.org>
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
 *  $Id: GetConnectionFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import java.sql.Connection;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist SQL Module Extension GetJNDIConnectionFunction
 * 
 * Get a connection to a SQL Database via JNDI
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @serial 2008-05-19
 * @version 1.2
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */
public class GetJNDIConnectionFunction extends BasicFunction {

	public final static FunctionSignature[] signatures = {
			new FunctionSignature(
					new QName("get-jndi-connection", SQLModule.NAMESPACE_URI,
							SQLModule.PREFIX),
					"Open's a connection to a SQL Database. Expects a JNDI name in $a. Returns an xs:long representing the connection handle.",
					new SequenceType[] {
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)),

			new FunctionSignature(
					new QName("get-jndi-connection", SQLModule.NAMESPACE_URI,
							SQLModule.PREFIX),
					"Open's a connection to a SQL Database. Expects a JNDI name in $a, a username in $b and a password in $c. Returns an xs:long representing the connection handle.",
					new SequenceType[] {
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)) };

	/**
	 * GetJNDIConnectionFunction Constructor
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 */
	public GetJNDIConnectionFunction(XQueryContext context,
			FunctionSignature signature) {
		super(context, signature);
	}

	/**
	 * evaluate the call to the xquery get-jndi-connection() function, it is
	 * really the main entry point of this class
	 * 
	 * @param args
	 *            arguments from the get-jndi-connection() function call
	 * @param contextSequence
	 *            the Context Sequence to operate on (not used here internally!)
	 * @return A xs:long representing a handle to the connection
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// was a JNDI name specified?
		if (args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;

		try {
			Connection con = null;

			// get the JNDI source
			String jndiName = args[0].getStringValue();
			Context ctx = new InitialContext();
			DataSource ds = (DataSource) ctx.lookup(jndiName);

			// try and get the connection
			if (args.length == 1) {
				con = ds.getConnection();
			}
			if (args.length == 3) {
				String jndiUser = args[1].getStringValue();
				String jndiPassword = args[2].getStringValue();

				con = ds.getConnection(jndiUser, jndiPassword);
			}

			// store the connection and return the uid handle of the connection
			return new IntegerValue(SQLModule.storeConnection(context, con));
		} catch (Exception e) {
			throw new XPathException(e.getMessage());
		}
	}
}
