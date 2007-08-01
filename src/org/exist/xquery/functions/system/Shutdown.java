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
 *  $Id:
 */
package org.exist.xquery.functions.system;


import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Shutdown the eXist server (must be dba)
 * 
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class Shutdown extends BasicFunction
{

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("shutdown", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Shutdown eXist. $a is the username and $b is the password.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		
		new FunctionSignature(
			new QName("shutdown", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Shutdown eXist. $a is the username, $b is the password and $c is the delay in milliseconds.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.LONG, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
		

	public Shutdown(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		String username = args[0].getStringValue();
		String password = args[1].getStringValue();
		long delay = 0;
		if(args.length == 3)
		{
			if(!args[2].isEmpty())
			{
				delay = ((NumericValue)args[2].itemAt(0)).getLong();
			}
		}
		
		try
		{
			//get the root collection
			Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, username, password);
			
			DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
			
			//get the database instance manager
			if(mgr == null)
			{
				System.err.println("service is not available");
			}
			else if(mgr.isLocalInstance())
			{
				System.out.println("shutting down database...");
				if(delay == 0)
				{
					mgr.shutdown();
				}
				else
				{
					mgr.shutdown(delay);
				}
			}
			
			return Sequence.EMPTY_SEQUENCE;
		}
		catch(XMLDBException xmldbe)
		{
			throw new XPathException(getASTNode(), "Exception while retrieving root collection: " + xmldbe.getMessage(), xmldbe);
		}
		
	}
}
