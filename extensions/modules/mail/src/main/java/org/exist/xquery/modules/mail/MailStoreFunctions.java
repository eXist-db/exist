/*
 *  eXist Mail Module Extension MailStoreFunctions
 *  Copyright (C) 2006-09 Adam Retter <adam.retter@devon.gov.uk>
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */

package org.exist.xquery.modules.mail;


import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist Mail Module Extension GetStore
 * 
 * Get a mail store
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @serial 2009-03-12
 * @version 1.3
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class MailStoreFunctions extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MailStoreFunctions.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "get-mail-store", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Opens a mail store. Host/User/Password/Protocol values will be obtained from the session.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "mail-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The JavaMail session handle retrieved from mail:get-mail-session()" )
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the store handle." )
			),
	
		new FunctionSignature(
			new QName( "close-mail-store", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Closes a mail store.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "mail-store-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The mail store handle retrieved from mail:get-mail-store()" )
			},
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
			)
		};

	public MailStoreFunctions( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	@Override
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		if( isCalledAs( "get-mail-store" ) ) {
			return getMailStore( args, contextSequence );
		} else if( isCalledAs( "close-mail-store" ) ) {
			return closeMailStore( args, contextSequence );
		}
	
		throw( new XPathException( this, "Invalid function name" ) );	
	}

	private Sequence getMailStore( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Store store;
		
		// was a session handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException( this, "Session handle not specified" ) );
		}

		// get the Session
		long sessionHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Session session = MailModule.retrieveSession( context, sessionHandle );
		if( session == null ) {
			throw( new XPathException( this, "Invalid Session handle specified" ) );
		}
		
		try {
			String password = session.getProperty( "mail." + session.getProperty( "mail.store.protocol" ) + ".password" ); 
			
			if( password == null ) {
				password = session.getProperty( "mail.password" ); 
			}
			
			store = session.getStore();
			
			store.connect( null, null, password );
		}
		catch( MessagingException me ) {
			throw( new XPathException( this, "Failed to open mail store", me ) );
		}
		
		// save the store and return the handle of the store
			
		return( new IntegerValue( MailModule.storeStore( context, store ) ) );
	}

	private Sequence closeMailStore( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		// was a store handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException( this, "Store handle not specified" ) );
		}

		// get the Store
		long storeHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Store store = MailModule.retrieveStore( context, storeHandle );
		if( store == null ) {
			throw( new XPathException( this, "Invalid Store handle specified" ) );
		}
		
		try {
			store.close();
		}
		catch( MessagingException me ) {
			throw( new XPathException( this, "Failed to close mail store", me ) );
		}
		finally {
			MailModule.removeStore( context, storeHandle );
		}
			
		return( Sequence.EMPTY_SEQUENCE );
	}
}
