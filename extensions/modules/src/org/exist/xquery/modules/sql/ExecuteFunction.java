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

import org.apache.log4j.Logger;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.w3c.dom.Node;

/**
 * eXist SQL Module Extension ExecuteFunction
 * 
 * Execute a SQL statement against a SQL capable Database
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @serial 2009-01-25
 * @version 1.13
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */
public class ExecuteFunction extends BasicFunction 
{
	private static final Logger logger = Logger.getLogger(ExecuteFunction.class);
	
	public final static FunctionSignature[] signatures = { new FunctionSignature(
			new QName( "execute", SQLModule.NAMESPACE_URI, SQLModule.PREFIX ),
				"Executes a SQL statement against a SQL db using the connection " +
				"indicated by the connection handle.",
				new SequenceType[] {
						new FunctionParameterSequenceType( "handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "connection handle" ),
						new FunctionParameterSequenceType( "sql-statement", Type.STRING, Cardinality.EXACTLY_ONE, "" ),
						new FunctionParameterSequenceType( "make-node-from-column-name", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "indicates whether the xml nodes should be formed from the column names (in this mode a space in a Column Name will be replaced by an underscore!)" ) },
				new FunctionReturnSequenceType( Type.NODE, Cardinality.ZERO_OR_ONE, "the results" ) 
			) };

	/**
	 * ExecuteFunction Constructor
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 */
	public ExecuteFunction( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
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
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		logger.info("Entering " + SQLModule.PREFIX + ":" + getName().getLocalName());
		// was a connection and SQL statement specified?
		if( args[0].isEmpty() || args[1].isEmpty() ) {
			return( Sequence.EMPTY_SEQUENCE );
		}

		// get the Connection
		long connectionUID = ((IntegerValue) args[0].itemAt(0)).getLong();
		Connection con = SQLModule.retrieveConnection( context, connectionUID );
		if( con == null ) {
			return( Sequence.EMPTY_SEQUENCE );
		}

		// get the SQL statement
		String sql = args[1].getStringValue();

		Statement stmt = null;
		ResultSet rs = null;

		try
		{
			MemTreeBuilder builder = context.getDocumentBuilder();
			int iRow = 0;

			// execute the SQL statement
			stmt = con.createStatement();

			// execute the query statement
			if(stmt.execute(sql))
			{
				/* SQL Query returned results */

				// iterate through the result set building an XML document
				rs = stmt.getResultSet();
				ResultSetMetaData rsmd = rs.getMetaData();
				int iColumns = rsmd.getColumnCount();
				
				builder.startDocument();
				
				builder.startElement(new QName("result", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
				builder.addAttribute(new QName("count", null, null), String.valueOf(-1 ));
				
				while(rs.next())
				{
					builder.startElement(new QName("row", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        			builder.addAttribute(new QName("index", null, null), String.valueOf(rs.getRow()));

					// get each tuple in the row
					for(int i = 0; i < iColumns; i++)
					{
						String columnName = rsmd.getColumnLabel(i + 1);

						if(columnName != null)
						{
							String colValue = rs.getString(i + 1);
							
							String colElement = "field";

							if(((BooleanValue)args[2].itemAt(0)).effectiveBooleanValue() && columnName.length() > 0)
							{
								// use column names as the XML node

								/**
								 * Spaces in column names are replaced with
								 * underscore's
								 */
								
								colElement = escapeXmlAttr(columnName.replace(' ', '_'));
							} 
						
							builder.startElement(new QName(colElement, SQLModule.NAMESPACE_URI, SQLModule.PREFIX ), null);
							
							if(!((BooleanValue)args[2].itemAt(0)).effectiveBooleanValue() || columnName.length() <= 0)
							{
								String name;
								
								if(columnName.length() > 0) {
									name = escapeXmlAttr(columnName);
								} else {
									name = "Column: " + String.valueOf(i + 1);
								}
								
								builder.addAttribute(new QName("name", null, null), name);
							}
							
							builder.addAttribute(new QName("type", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), rsmd.getColumnTypeName(i + 1));
							builder.addAttribute(new QName("type", Namespaces.SCHEMA_NS, "xs"), Type.getTypeName(sqlTypeToXMLType(rsmd.getColumnType(i + 1))));
							
							if(rs.wasNull())
							{
								// Add a null indicator attribute if the value was SQL Null
								builder.addAttribute(new QName("null", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), "true");
							}
							
							if(colValue != null)
							{
								builder.characters(escapeXmlText(colValue));
							}
							
							builder.endElement();
						}
					}

					builder.endElement();
					
					iRow++;
				}
				
				builder.endElement();
		
			}
			else
			{
				/* SQL Query performed updates */
				
				builder.startDocument();
				
				builder.startElement(new QName("result", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        		builder.addAttribute(new QName("updateCount", null, null), String.valueOf(stmt.getUpdateCount()));

				builder.endElement();
			}
			
			// Change the root element count attribute to have the correct value
			
			NodeValue node = (NodeValue)builder.getDocument().getDocumentElement();

			Node count = node.getNode().getAttributes().getNamedItem("count");
			
			if(count != null)
			{
				count.setNodeValue(String.valueOf(iRow));
			}
			builder.endDocument();
			
			logger.info("Exiting " + SQLModule.PREFIX + ":" + getName().getLocalName());
			
			// return the XML result set
			return(node);
			
		} 
		catch(SQLException sqle)
		{
			LOG.error("sql:execute() Caught SQLException \"" + sqle.getMessage() + "\" for SQL: \"" + sql + "\"", sqle);
			
			//return details about the SQLException
			MemTreeBuilder builder = context.getDocumentBuilder();
			
			builder.startDocument();
			builder.startElement(new QName("exception", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
			
			boolean recoverable = false;
			/* if(sqle instanceof SQLRecoverableException)
			{
				recoverable = true;
			}*/ // NOT UNTIL JDK 6!
			builder.addAttribute(new QName("recoverable", null, null), String.valueOf(recoverable));
			
			
			builder.startElement(new QName("state", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
			builder.characters(sqle.getSQLState());
			builder.endElement();
			
			builder.startElement(new QName("message", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
			builder.characters(sqle.getMessage());
			builder.endElement();
			
			builder.startElement(new QName("stack-trace", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
			ByteArrayOutputStream bufStackTrace = new ByteArrayOutputStream();
			sqle.printStackTrace(new PrintStream(bufStackTrace));
			builder.characters(new String(bufStackTrace.toByteArray()));
			builder.endElement();
			
			builder.startElement(new QName("sql", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
			builder.characters(escapeXmlText(sql));
			builder.endElement();
			
				int line = getLine();
				int column = getColumn();
				
				builder.startElement(new QName("xquery", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
				builder.addAttribute(new QName("line", null, null), String.valueOf(line));
				builder.addAttribute(new QName("column", null, null), String.valueOf(column));
				builder.endElement();

			builder.endElement();
			builder.endDocument();
			
			return (NodeValue)builder.getDocument().getDocumentElement();
		} 
		finally
		{
			// close any record set or statement
			try
			{
				if(rs != null)
				{
					rs.close();
				}

				if(stmt != null)
				{
					stmt.close();
				}
			} 
			catch(SQLException se)
			{
				LOG.warn("Unable to cleanup JDBC results", se);
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
	private int sqlTypeToXMLType( int sqlType ) 
	{
		switch(sqlType)
		{
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

	private static String escapeXmlText(String text) 
	{
		String work = null;

		if(text != null)
		{
			work = text.replaceAll( "\\&", "\\&amp;" );
			work = work.replaceAll( "<", "\\&lt;" );
			work = work.replaceAll( ">", "\\&gt;" );
		}

		return(work);
	}

	private static String escapeXmlAttr(String attr)
	{
		String work = null;

		if(attr != null)
		{
			work = escapeXmlText(attr);
			work = work.replaceAll("'", "\\&apos;");
			work = work.replaceAll("\"", "\\&quot;");
		}

		return(work);
	}
}
