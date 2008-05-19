/*
 *  eXist SQL Module Extension ExecuteFunction
 *  Copyright (C) 2006 Adam Retter <adam@exist-db.org>
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
 *  $Id: ExecuteFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * eXist SQL Module Extension ExecuteFunction
 * 
 * Execute a SQL statement against a SQL capable Database
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @serial 2008-05-19
 * @version 1.11
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */
public class ExecuteFunction extends BasicFunction {
	public final static FunctionSignature[] signatures = { new FunctionSignature(
			new QName("execute", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
			"Executes a SQL statement $b against a SQL db using the connection indicated by the connection handle in $a. $c indicates whether the xml nodes should be formed from the column names (in this mode a space in a Column Name will be replaced by an underscore!)",
			new SequenceType[] {
					new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)) };

	/**
	 * ExecuteFunction Constructor
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 */
	public ExecuteFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/**
	 * evaluate the call to the XQuery execute() function, it is really the main
	 * entry point of this class
	 * 
	 * @param args
	 *            arguments from the execute() function call
	 * @param contextSequence
	 *            the Context Sequence to operate on (not used here internally!)
	 * @return A node representing the SQL result set
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// was a connection and SQL statement specified?
		if (args[0].isEmpty() || args[1].isEmpty()) {
			return (Sequence.EMPTY_SEQUENCE );
		}

		// get the Connection
		long connectionUID = ((IntegerValue) args[0].itemAt(0)).getLong();
		Connection con = SQLModule.retrieveConnection(context, connectionUID);
		if (con == null) {
			return Sequence.EMPTY_SEQUENCE;
		}

		Statement stmt = null;
		ResultSet rs = null;

		try {
			StringBuffer xmlBuf = new StringBuffer();

			// get the SQL statement
			String sql = args[1].getStringValue();

			// execute the SQL statement
			stmt = con.createStatement();

			// execute the query statement
			if (stmt.execute(sql)) {
				/* SQL Query returned results */

				// iterate through the result set building an XML document
				rs = stmt.getResultSet();
				ResultSetMetaData rsmd = rs.getMetaData();
				int iColumns = rsmd.getColumnCount();
				int iRows = 0;

				while (rs.next()) {
					xmlBuf.append("<" + SQLModule.PREFIX + ":row index=\""
							+ rs.getRow() + "\">");

					// get each tuple in the row
					for (int i = 0; i < iColumns; i++) {
						String columnName = rsmd.getColumnName(i + 1);

						if (columnName != null) {

							String colValue = rs.getString(i + 1);
							String sqlNull = "";

							if (rs.wasNull()) {
								// Add a null indicator attributed if the value
								// was SQL Null
								sqlNull = " " + SQLModule.PREFIX
										+ ":null=\"true\"";
							}

							if (((BooleanValue) args[2].itemAt(0))
									.effectiveBooleanValue()) {
								// use column names as the XML node

								/**
								 * Spaces in column names are replaced with
								 * underscore's
								 */
								xmlBuf
										.append("<"
												+ escapeXmlAttr(columnName
														.replace(' ', '_'))
												+ " "
												+ SQLModule.PREFIX
												+ ":type=\""
												+ rsmd.getColumnTypeName(i + 1)
												+ "\" xs:type=\""
												+ Type
														.getTypeName(sqlTypeToXMLType(rsmd
																.getColumnType(i + 1)))
												+ "\"" + sqlNull + " >");
								if (colValue != null) {
									xmlBuf.append(escapeXmlText(colValue));
								}
								xmlBuf.append("</"
										+ escapeXmlAttr(columnName.replace(' ',
												'_')) + ">");
							} else {
								// DONT use column names as the XML node
								xmlBuf
										.append("<"
												+ SQLModule.PREFIX
												+ ":field name=\""
												+ escapeXmlAttr(columnName)
												+ "\" sql:type=\""
												+ rsmd.getColumnTypeName(i + 1)
												+ "\" xs:type=\""
												+ Type
														.getTypeName(sqlTypeToXMLType(rsmd
																.getColumnType(i + 1)))
												+ "\"" + sqlNull + " >");
								if (colValue != null) {
									xmlBuf.append(escapeXmlText(colValue));
								}
								xmlBuf.append("</" + SQLModule.PREFIX
										+ ":field>");
							}
						}
					}

					xmlBuf.append("</" + SQLModule.PREFIX + ":row>");

					iRows++;
				}
				xmlBuf.insert(0, "<" + SQLModule.PREFIX + ":result xmlns:"
						+ SQLModule.PREFIX + "=\"" + SQLModule.NAMESPACE_URI
						+ "\" xmlns:xs=\"" + Namespaces.SCHEMA_NS
						+ "\" count=\"" + iRows + "\">");
				xmlBuf.append("</" + SQLModule.PREFIX + ":result>");
			} else {
				/* SQL Query performed updates */
				xmlBuf.append("<" + SQLModule.PREFIX + ":result xmlns:"
						+ SQLModule.PREFIX + "=\"" + SQLModule.NAMESPACE_URI
						+ "\" updateCount=\"" + stmt.getUpdateCount() + "\"/>");
			}

			// return the XML result set
			return ModuleUtils.stringToXML(context, xmlBuf.toString());
		} catch (SAXException se) {
			throw new XPathException(se);
		} catch (SQLException e) {
			throw new XPathException(e);
		} finally {
			// close any record set or statement
			try {
				if (rs != null) {
					rs.close();
				}

				if (stmt != null) {
					stmt.close();
				}
			} catch (SQLException se) {
				LOG.debug("Unable to cleanup JDBC results", se);
			}

			// explicitly ready for Garbage Collection
			rs = null;
			stmt = null;
		}
	}

	/**
	 * Converts a SQL data type to an XML data type
	 * 
	 * @param sqlType
	 *            The SQL data type as specified by JDBC
	 * 
	 * @return The XML Type as specified by eXist
	 */
	private int sqlTypeToXMLType(int sqlType) {
		switch (sqlType) {
		case Types.ARRAY:
			return Type.NODE;

		case Types.BIGINT:
			return Type.INT;

		case Types.BINARY:
			return Type.BASE64_BINARY;

		case Types.BIT:
			return Type.INT;

		case Types.BLOB:
			return Type.BASE64_BINARY;

		case Types.BOOLEAN:
			return Type.BOOLEAN;

		case Types.CHAR:
			return Type.STRING;

		case Types.CLOB:
			return Type.STRING;

		case Types.DECIMAL:
			return Type.DECIMAL;

		case Types.DOUBLE:
			return Type.DOUBLE;

		case Types.FLOAT:
			return Type.FLOAT;

		case Types.LONGVARCHAR:
			return Type.STRING;

		case Types.NUMERIC:
			return Type.NUMBER;

		case Types.SMALLINT:
			return Type.INT;

		case Types.TINYINT:
			return Type.INT;

		case Types.INTEGER:
			return Type.INTEGER;

		case Types.VARCHAR:
			return Type.STRING;

		default:
			return Type.ANY_TYPE;
		}
	}

	private static String escapeXmlText(String text) {
		String work = null;

		if (text != null) {
			work = text.replaceAll("\\&", "\\&amp;");
			work = work.replaceAll("<", "\\&lt;");
			work = work.replaceAll(">", "\\&gt;");
		}

		return (work);
	}

	private static String escapeXmlAttr(String attr) {
		String work = null;

		if (attr != null) {
			work = escapeXmlText(attr);
			work = work.replaceAll("'", "\\&apos;");
			work = work.replaceAll("\"", "\\&quot;");
		}

		return (work);
	}
}
