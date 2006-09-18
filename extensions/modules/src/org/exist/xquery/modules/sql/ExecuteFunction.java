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
 * @serial 2006-09-18
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class ExecuteFunction extends BasicFunction
{   
	static long current = System.currentTimeMillis();
	
	public final static FunctionSignature[] signatures =
	{
		new FunctionSignature(
			new QName("execute", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
			"Executes a SQL statement $b against a SQL db using the connection indicated by the connection handle in $a.",
			new SequenceType[]
			{
				new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
			ResultSet rs = stmt.executeQuery(sql);
			
			//iterate through the result set building an xml document
			StringBuffer xmlBuf = new StringBuffer();
			xmlBuf.append("<sql:table xmlns:sql=\"" + SQLModule.NAMESPACE_URI + "\">");
			
			ResultSetMetaData rsmd = rs.getMetaData();
			int iColumns = rsmd.getColumnCount();
			
			while(rs.next())
			{
				xmlBuf.append("<sql:row index=\"" + rs.getRow() + "\">");
				
				//get each tuple in the row
				for(int i = 0; i < iColumns; i++)
				{
					String columnName = rsmd.getColumnName(i);
					if(columnName != null)
					{
						xmlBuf.append("<" + columnName + ">");
						xmlBuf.append(rs.getString(columnName));
						xmlBuf.append("</" + columnName + ">");
					}
				}
				
				xmlBuf.append("</sql:row>");
			}
			xmlBuf.append("</sql:table>");
			
			//return the xml result set
			return stringToXML(xmlBuf.toString());
		}
		catch(SQLException e)
		{
			throw new XPathException(e.getMessage());
		}
	}
	
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
}
