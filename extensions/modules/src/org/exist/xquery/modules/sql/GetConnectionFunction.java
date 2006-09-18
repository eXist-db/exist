/*
 * eXist SQL Module Extension GetConnectionFunction
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
 *  $Id: GetConnectionFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;

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
 * eXist SQL Module Extension GetConnectionFunction 
 * 
 * Get a connection to an sql db
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-09-18
 * @version 1.0
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
			"Open's a connection to a SQL Database. Expects a JDBC Style URL in $a. Returns an xs:long representing the connection handle.",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)
		),
		
		new FunctionSignature(
				new QName("get-connection", SQLModule.NAMESPACE_URI, SQLModule.PREFIX),
				"Open's a connection to a SQL Database. Expects a JDBC Style URL in $a, a username in $b and a password in $c. Returns an xs:long representing the connection handle.",
				new SequenceType[]
				{
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.LONG, Cardinality.ZERO_OR_ONE)
			),
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
		//was a db url specified?
		if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
		
		try
		{
			Connection con = null;
			
			//get the db connection details
			String dbURL = args[0].getStringValue();
			
			if(args.length > 1)
			{
				String dbUser = args[1].getStringValue();
				String dbPassword = args[2].getStringValue();

				//try and get the connection
				con = DriverManager.getConnection(dbURL, dbUser, dbPassword);
			}
			else
			{
				//try and get the connection
				con = DriverManager.getConnection(dbURL);
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
		catch(SQLException e)
		{
			throw new XPathException(e.getMessage());
		}
	}
	
	//return a unique id
	private static synchronized long getUID()
	{
	    return current++;
	}
}
