/*
 *  eXist SQL Module Extension
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
 *  $Id: SQLModule.java 3933 2006-09-18 21:08:38 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

/**
 * eXist SQL Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows queries
 * against SQL Databases, returning an XML representation of the result set.
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @serial 2008-05-19
 * @version 1.1
 * 
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */

public class SQLModule extends AbstractInternalModule {

	protected final static Logger LOG = Logger.getLogger(SQLModule.class);

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/sql";

	public final static String PREFIX = "sql";

	private final static FunctionDef[] functions = {
			new FunctionDef(GetConnectionFunction.signatures[0],
					GetConnectionFunction.class),
			new FunctionDef(GetConnectionFunction.signatures[1],
					GetConnectionFunction.class),
			new FunctionDef(GetConnectionFunction.signatures[2],
					GetConnectionFunction.class),
			new FunctionDef(GetJNDIConnectionFunction.signatures[0],
					GetJNDIConnectionFunction.class),
			new FunctionDef(GetJNDIConnectionFunction.signatures[1],
					GetJNDIConnectionFunction.class),
			new FunctionDef(ExecuteFunction.signatures[0],
					ExecuteFunction.class),

	};

	private static long currentConnectionUID = System.currentTimeMillis();
	public final static String CONNECTIONS_CONTEXTVAR = "_eXist_sql_connections";

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
	 * Retrieves a previously stored Connection from the Context of an XQuery
	 * 
	 * @param context
	 *            The Context of the XQuery containing the Connection
	 * @param connectionUID
	 *            The UID of the Connection to retrieve from the Context of the
	 *            XQuery
	 */
	public final static Connection retrieveConnection(XQueryContext context,
			long connectionUID) {
		// get the existing connections map from the context
		HashMap connections = (HashMap) context
				.getXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR);
		if (connections == null) {
			return null;
		}

		// get the connection
		return (Connection) connections.get(new Long(connectionUID));
	}

	/**
	 * Stores a Connection in the Context of an XQuery
	 * 
	 * @param context
	 *            The Context of the XQuery to store the Connection in
	 * @param con
	 *            The connection to store
	 * 
	 * @return A unique ID representing the connection
	 */
	public final static synchronized long storeConnection(
			XQueryContext context, Connection con) {
		// get the existing connections map from the context
		HashMap connections = (HashMap) context
				.getXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR);
		if (connections == null) {
			// if there is no connections map, create a new one
			connections = new HashMap();
		}

		// get an id for the connection
		long conID = getUID();

		// place the connection in the connections map
		connections.put(new Long(conID), con);

		// store the updated connections map back in the context
		context.setXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR,
				connections);

		return conID;
	}

	/**
	 * Closes all the open DB connections for the specified XQueryContext
	 * 
	 * @param xqueryContext
	 *            The context to close JDBC connections for
	 */
	private final static void closeAllConnections(XQueryContext xqueryContext) {
		// get the existing connections map from the context
		HashMap connections = (HashMap) xqueryContext
				.getXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR);
		if (connections != null) {
			// iterate over each connection
			Set keys = connections.keySet();
			for (Iterator itKeys = keys.iterator(); itKeys.hasNext();) {
				// get the connection
				Long conID = (Long) itKeys.next();
				Connection con = (Connection) connections.get(conID);
				try {
					// close the connection
					con.close();

					// remove it from the connections map
					connections.remove(conID);
				} catch (SQLException se) {
					LOG.debug("Unable to close JDBC connection", se);
				}
			}

			// update the context
			xqueryContext.setXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR,
					connections);
		}
	}

	/**
	 * Returns a Unique ID based on the System Time
	 * 
	 * @return The Unique ID
	 */
	private static synchronized long getUID() {
		return currentConnectionUID++;
	}

	/**
	 * Resets the Module Context and closes any DB connections for the
	 * XQueryContext
	 * 
	 * @param xqueryContext
	 *            The XQueryContext
	 */
	public void reset(XQueryContext xqueryContext) {
		// reset the module context
		super.reset(xqueryContext);

		// close any open connections
		closeAllConnections(xqueryContext);
	}
}
