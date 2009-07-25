/*
 *  eXist Mail Module Extension SendEmailFunction
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: SendEmailFunction.java,v 1.12 2006/03/01 13:52:00 deliriumsky Exp $
 */

package org.exist.xquery.modules.mail;


import java.io.IOException;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Type;

import javax.mail.MessagingException;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;

/**
 * eXist Mail Module Extension GetFolder
 * 
 * Get a mail store
 * 
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 2009-03-12
 * @version 1.3
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class MailFolderFunctions extends BasicFunction
{
	protected static final Logger logger = Logger.getLogger(MailFolderFunctions.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "get-mail-folder", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Open's a mail folder.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "mail-store-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "mail store handle retrieved from mail:get-mail-store()" ),
				new FunctionParameterSequenceType( "foldername", Type.STRING, Cardinality.EXACTLY_ONE, "the name of the folder to open" )
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the folder handle." )
			),
	
		new FunctionSignature(
			new QName( "close-mail-folder", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Closes's a mail folder.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "mail-folder-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "the mail folder handle retrieved from mail:get-mail-folder()" ),
				new FunctionParameterSequenceType( "expunge", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "a boolean that specifies whether to expunge the folder on close." )
			},
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
			)
		};

	/**
	 * MailFolderFunctions Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public MailFolderFunctions( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	/**
	 * evaluate the call to the xquery get-folder function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the get-folder() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A sequence representing the result of the get-folder() function call
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		logger.info("Entering " + MailModule.PREFIX + ":" + getName().getLocalName());
		if( isCalledAs( "get-mail-folder" ) ) {
			Sequence mailFolder = getMailFolder( args, contextSequence );
			logger.info("Exiting " + MailModule.PREFIX + ":" + getName().getLocalName());
			return mailFolder;
		} else if( isCalledAs( "close-mail-folder" ) ) {
			Sequence closeMailFolder = closeMailFolder( args, contextSequence );
			logger.info("Exiting " + MailModule.PREFIX + ":" + getName().getLocalName());
			return closeMailFolder;
		} 
			
		throw( new XPathException( this, "Invalid function name" ) );	
	}
	
	
	private Sequence getMailFolder( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Folder folder;
		
		// was a store handle specified?
		if( args[0].isEmpty() || args[1].isEmpty() ) {
			throw( new XPathException( this, "Store handle and/or folder name not specified" ) );
		}

		// get the Store
		long storeHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Store store = MailModule.retrieveStore( context, storeHandle );
		if( store == null ) {
			throw( new XPathException( this, "Invalid Store handle specified" ) );
		}
		
		// get the Folder Name
		String name = args[1].getStringValue();
		
		try {
			folder = store.getFolder( name );
			
			folder.open( Folder.READ_WRITE  );
		}
		catch( MessagingException me ) {
			throw( new XPathException( this, "Failed to open mail folder", me ) );
		}
		
		// save the folder and return the handle of the folder
			
		return( new IntegerValue( MailModule.storeFolder( context, folder ) ) );
	}
	
	
	private Sequence closeMailFolder( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		// was a folder handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException( this, "Folder handle not specified" ) );
		}
		
		boolean expunge = ((BooleanValue)args[1].itemAt(0)).effectiveBooleanValue();

		// get the Folder
		long folderHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Folder folder = MailModule.retrieveFolder( context, folderHandle );
		if( folder == null ) {
			throw( new XPathException( this, "Invalid Folder handle specified" ) );
		}
		
		try {
			folder.close( expunge );
		}
		catch( MessagingException me ) {
			throw( new XPathException( this, "Failed to close mail folder", me ) );
		}
		finally {
			MailModule.removeFolder( context, folderHandle );
		}
			
		return( Sequence.EMPTY_SEQUENCE );
	}
}
