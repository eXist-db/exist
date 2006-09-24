/*
 * eXist SQL Module Extension ExecuteFunction
 *
 * Released under the BSD License
 *
 * Copyright (c) 2006, Adam retter <adam.retter@devon.gov.uk>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 		Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *  	Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  	Neither the name of Adam Retter nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  $Id: ExecuteFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * eXist SQL Module Extension ExecuteFunction 
 * 
 * Execute a sql statement against a sql db
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-09-24
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class ExecuteFunction extends BasicFunction
{   
	private static long current = System.currentTimeMillis();
	private StringBuffer xmlBuf = new StringBuffer();
	
	public final static FunctionSignature[] signatures =
	{
		new FunctionSignature(
			new QName("execute", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
			"Executes a SQL statement $b against a SQL db using the connection indicated by the connection handle in $a. $c indicates whether the xml nodes should be formed from the column names (in this mode a space in a Column Name will be replaced by an underscore!)",
			new SequenceType[]
			{
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
		)
	};
	/**
	 * ExecuteFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public ExecuteFunction(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
    }

	/**
	 * evaluate the call to the xquery execute() function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the execute() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		An xs:node representing the sql result set
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//was a connection and sql statement specified?
		if (args[0].isEmpty() || args[1].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
		
		//get the existing connections map from the context
		HashMap connections = (HashMap)context.getXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR);
		if(connections == null)
			return Sequence.EMPTY_SEQUENCE;
		
		//get the connection
		long conID = ((IntegerValue)args[0].itemAt(0)).getLong();
		Connection con = (Connection)connections.get(new Long(conID));
		if(con == null)
			return Sequence.EMPTY_SEQUENCE;
		
		try
		{
			//get the sql statement
			String sql = args[1].getStringValue();
			
			//execute the sql statement
			Statement stmt = con.createStatement();
			
			//execute the query statement
			if(stmt.execute(sql))
			{
				/* SQL Query returned results */
				
				//iterate through the result set building an xml document
				ResultSet rs = stmt.getResultSet();
				ResultSetMetaData rsmd = rs.getMetaData();
				int iColumns = rsmd.getColumnCount();
				int iRows = 0;
				
				while(rs.next())
				{
					xmlBuf.append("<sql:row index=\"" + rs.getRow() + "\">");
					
					//get each tuple in the row
					for(int i = 0; i < iColumns; i++)
					{
						String columnName = rsmd.getColumnName(i+1);
						
						if(columnName != null)
						{
							if(((BooleanValue)args[2].itemAt(0)).effectiveBooleanValue())
							{
								//use column names as the xml node
								
								/** Spaces in column names are replaced with underscore's */
								xmlBuf.append("<" + columnName.replace(' ', '_') + " sql:type=\"" + rsmd.getColumnTypeName(i+1) + "\" xs:type=\"" + Type.getTypeName(sqlTypeToXMLType(rsmd.getColumnType(i+1))) + "\">");
								xmlBuf.append(rs.getString(i+1));
								xmlBuf.append("</" + columnName.replace(' ', '_') + ">");
							}
							else
							{
								//DONT use column names as the xml node
								xmlBuf.append("<sql:field name=\"" + columnName + "\" sql:type=\"" + rsmd.getColumnTypeName(i+1) + "\" xs:type=\"" + Type.getTypeName(sqlTypeToXMLType(rsmd.getColumnType(i+1))) + "\">");
								xmlBuf.append(rs.getString(i+1));
								xmlBuf.append("</sql:field>");
							}
						}
					}
					
					xmlBuf.append("</sql:row>");
					
					iRows++;
				}
				xmlBuf.insert(0, "<sql:result xmlns:sql=\"" + SQLModule.NAMESPACE_URI + "\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" count=\"" + iRows + "\">");
				xmlBuf.append("</sql:result>");
			}
			else
			{
				/* SQL Query performed updates */
				xmlBuf.append("<sql:result xmlns:sql=\"" + SQLModule.NAMESPACE_URI + "\" updateCount=\"" + stmt.getUpdateCount() + "\"/>");
			}
			
			//return the xml result set
			return stringToXML(xmlBuf.toString());
		}
		catch(SQLException e)
		{
			throw new XPathException(e.getMessage());
		}
	}
	
	/**
	 * Takes a String of XML and Creates an XML Node from it using SAX in the context of the query
	 * 
	 * @param xml	The String of XML
	 * 
	 * @return	The NodeValue of XML 
	 * */
	private NodeValue stringToXML(String xml) throws XPathException
	{
		context.pushDocumentContext();
		try
		{ 
			//try and construct xml document from input stream, we use eXist's in-memory DOM implementation
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);	
			//TODO : we should be able to cope with context.getBaseURI()				
			InputSource src = new InputSource(new ByteArrayInputStream(xml.getBytes()));
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			MemTreeBuilder builder = context.getDocumentBuilder();
			DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
			reader.setContentHandler(receiver);
			reader.parse(src);
			Document doc = receiver.getDocument();
			return (NodeValue)doc.getDocumentElement();
		}
		catch (ParserConfigurationException e)
		{				
			throw new XPathException(e.getMessage());
		}
		catch (SAXException e)
		{
			throw new XPathException(e.getMessage());
		}
		catch (IOException e)
		{
			throw new XPathException(e.getMessage());
		}
		finally
		{
         context.popDocumentContext();
		}
	}
	
	/**
	 * Converts a SQL data type to an XML data type
	 * 
	 * @param	sqlType	The SQL data type as specified by JDBC
	 *
	 * @return	The XML Type as specified by eXist
	 */
	private int sqlTypeToXMLType(int sqlType)
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
}
