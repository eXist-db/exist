/*
 *  eXist SQL Module Extension GetConnectionFunction
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
 *  $Id: GetConnectionFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Properties;

import org.exist.dom.QName;
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

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

/**
 * eXist SQL Module Extension GetConnectionFunction 
 * 
 * Get a connection to a SQL Database
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2007-07-10
 * @version 1.1
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class GetConnectionFunction extends BasicFunction
{   
	static long current = System.currentTimeMillis();
	
	public final static FunctionSignature[] signatures =
	{
		new FunctionSignature(
			new QName("get-connection", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
			"Open's a connection to a SQL Database. Expects a JDBC Driver class name in $a and a JDBC URL in $b. Returns an xs:long representing the connection handle.",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)
		),
		
		new FunctionSignature(
			new QName("get-connection", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
			"Open's a connection to a SQL Database. Expects " +
			"a JDBC Driver class name in $a and a JDBC URL in $b." +
			" Additional JDBC properties may be set in $c in the" +
			" form <properties><property name=\"\" value=\"\"/></properties>. " +
			"Returns an xs:long representing the connection handle.",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ELEMENT, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)
		),
		
		new FunctionSignature(
			new QName("get-connection", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
			"Open's a connection to a SQL Database. Expects a JDBC Driver class name in $a, a JDBC URL in $b, a username in $c and a password in $d. Returns an xs:long representing the connection handle.",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)
		)
	};
	/**
	 * GetConnectionFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public GetConnectionFunction(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
    }

	/**
	 * evaluate the call to the xquery get-connection() function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the get-connection() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A xs:long representing a handle to the connection
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//was a db driver and url specified?
		if (args[0].isEmpty() || args[1].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
		
		try
		{
			Connection con = null;
			
			//get the db connection details
			String dbDriver = args[0].getStringValue();
			String dbURL = args[1].getStringValue();
			
			//load the driver
			Class.forName(dbDriver).newInstance();
			
			if(args.length == 2)
			{
				//try and get the connection
				con = DriverManager.getConnection(dbURL);
			}
			else if(args.length == 3)
			{
				//try and get the connection
				Properties props = parseProperties(((NodeValue)args[2].itemAt(0)).getNode());
				con = DriverManager.getConnection(dbURL, props);
			}
			else if(args.length == 4)
			{
				String dbUser = args[2].getStringValue();
				String dbPassword = args[3].getStringValue();
				
				//try and get the connection
				con = DriverManager.getConnection(dbURL, dbUser, dbPassword);
			}
			
			//get the existing connections map from the context
			HashMap connections = (HashMap)context.getXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR);
			if(connections == null)
			{
				//if there is no connections map, create a new one
				connections = new HashMap();
			}
			
			//gent an id for the connection
			long conID = getUID();

			//place the connection in the connections map 
			connections.put(new Long(conID), con);
			
			//store the updated connections map back in the context
			context.setXQueryContextVar(SQLModule.CONNECTIONS_CONTEXTVAR, connections);
			
			//return the uid handle of the connection
			return new IntegerValue(conID);
		}
		catch(Exception e)
		{
			throw new XPathException(e.getMessage());
		}
	}
	
	/**
	 * Parses property parameters
	 * 
	 * @param properties The properties node e.g. <properties><property name="encoding" value="UTF-8"/></properties>
	 * @return the properties
	 */
	private final static Properties parseProperties(Node properties) throws XPathException
	{
		Properties props = new Properties();
		
		if(properties.getNodeType() == Node.ELEMENT_NODE && properties.getLocalName().equals("properties"))
		{
			NodeList propertiesList = properties.getChildNodes();
			
			for(int i = 0; i < propertiesList.getLength(); i++)
			{
				Node property = propertiesList.item(i);
				if(property.getNodeType() == Node.ELEMENT_NODE && property.getLocalName().equals("property"))
				{
					String name = ((Element)property).getAttribute("name");
					String value = ((Element)property).getAttribute("value");
					if(name == null || value == null)
					{
						throw new XPathException("Name or value attribute missing for property parameter");
					}
					
					props.put(name, value);
				}
			}
		}
		
		return props;
	}
	
	/**
	 * Returns a Unique ID based on the System Time
	 * 
	 * @return The Unique ID
	 */
	private static synchronized long getUID()
	{
	    return current++;
	}
}
