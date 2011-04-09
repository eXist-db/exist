/*
 *  eXist Mail Module Extension
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

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

/**
 * eXist Mail Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows email to
 * be sent from XQuery using either SMTP or Sendmail.  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @author ljo
 * @author José María Fernández <josemariafg@gmail.com>
 * @serial 2010-03-19
 * @version 1.4
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 */
public class MailModule extends AbstractInternalModule
{ 
	protected final static Logger LOG = Logger.getLogger( MailModule.class );
	
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/mail";
	
	public final static String PREFIX = "mail";
    // JavaMail-based from 2009-03-14
    // makes the need for versioning of the functions obvious too /ljo
    public final static String INCLUSION_DATE = "2005-05-12, 2009-03-14";
    public final static String RELEASED_IN_VERSION = "eXist-1.2 (JavaMail-based in trunk)";
	
	private final static FunctionDef[] functions = {
		
		new FunctionDef( MailSessionFunctions.signatures[0], MailSessionFunctions.class ),
		new FunctionDef( MailStoreFunctions.signatures[0],   MailStoreFunctions.class ),
		new FunctionDef( MailStoreFunctions.signatures[1],   MailStoreFunctions.class ),
		new FunctionDef( MailFolderFunctions.signatures[0],  MailFolderFunctions.class ),
		new FunctionDef( MailFolderFunctions.signatures[1],  MailFolderFunctions.class ),
		new FunctionDef( MessageListFunctions.signatures[0], MessageListFunctions.class ),
		new FunctionDef( MessageListFunctions.signatures[1], MessageListFunctions.class ),
		new FunctionDef( MessageListFunctions.signatures[2], MessageListFunctions.class ),
		new FunctionDef( MessageListFunctions.signatures[3], MessageListFunctions.class ),
				
		new FunctionDef( SendEmailFunction.signatures[0], SendEmailFunction.class ),
		
		// deprecated functions:
		new FunctionDef( SendEmailFunction.deprecated, SendEmailFunction.class )
	};
	
	public final static String SESSIONS_CONTEXTVAR 			= "_eXist_mail_sessions";
	public final static String STORES_CONTEXTVAR 			= "_eXist_mail_stores";
	public final static String FOLDERS_CONTEXTVAR 			= "_eXist_mail_folders";
	public final static String FOLDERMSGLISTS_CONTEXTVAR 	= "_eXist_folder_message_lists";
	public final static String MSGLISTS_CONTEXTVAR 			= "_eXist_mail_message_lists";

	private static long currentSessionHandle = System.currentTimeMillis();
	
	
	public MailModule()
	{
		super(functions);
	}
	

	public String getNamespaceURI()
	{
		return( NAMESPACE_URI );
	}
	

	public String getDefaultPrefix()
	{
		return( PREFIX );
	}
	

	public String getDescription()
	{
		return( "A module for performing email related functions" );
	}
	
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

	
	//***************************************************************************
	//*
	//*    Session Methods
	//*
	//***************************************************************************/
	
	/**
	 * Retrieves a previously stored Session from the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery containing the Session
	 * @param sessionHandle	 	The handle of the Session to retrieve from the Context of the XQuery
	 */
	final static Session retrieveSession( XQueryContext context, long sessionHandle ) 
	{
		Session session = null;
		
		// get the existing sessions map from the context
		
		HashMap<Long, Session> sessions = (HashMap<Long, Session>)context.getXQueryContextVar( MailModule.SESSIONS_CONTEXTVAR );
		
		if( sessions != null ) {
			session = sessions.get( new Long( sessionHandle ) );
		}
		
		return( session );
	}


	/**
	 * Stores a Session in the Context of an XQuery
	 * 
	 * @param context 	The Context of the XQuery to store the Session in
	 * @param session 	The Session to store
	 * 
	 * @return A unique handle representing the Session
	 */
	final static synchronized long storeSession( XQueryContext context, Session session ) 
	{
		// get the existing sessions map from the context
		
		HashMap<Long, Session> sessions = (HashMap<Long, Session>)context.getXQueryContextVar( MailModule.SESSIONS_CONTEXTVAR );
		
		if( sessions == null ) {
			// if there is no sessions map, create a new one
			sessions = new HashMap<Long, Session>();
		}

		// get an handle for the session
		long sessionHandle = getHandle();

		// place the session in the sessions map
		sessions.put( new Long( sessionHandle ), session );
		
		// store the updated sessions map back in the context
		context.setXQueryContextVar( MailModule.SESSIONS_CONTEXTVAR, sessions );

		return( sessionHandle );
	}
	
	
	//***************************************************************************
	//*
	//*    Store Methods
	//*
	//***************************************************************************/
	
	/**
	 * Retrieves a previously saved Store from the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery containing the Store
	 * @param storeHandle	 	The handle of the Store to retrieve from the Context of the XQuery
	 */
	final static Store retrieveStore( XQueryContext context, long storeHandle ) 
	{
		Store store = null;
		
		// get the existing stores map from the context
		
		HashMap<Long, Store> stores = (HashMap<Long, Store>)context.getXQueryContextVar( MailModule.STORES_CONTEXTVAR );
		
		if( stores != null ) {
			store = stores.get( new Long( storeHandle ) );
		}

		return( store );
	}


	/**
	 * Saves a Store in the Context of an XQuery
	 * 
	 * @param context 	The Context of the XQuery to save the Store in
	 * @param store 	The Store to store
	 * 
	 * @return A unique handle representing the Store
	 */
	final static synchronized long storeStore( XQueryContext context, Store store ) 
	{
		// get the existing stores map from the context
		
		HashMap<Long, Store> stores = (HashMap<Long, Store>)context.getXQueryContextVar( MailModule.STORES_CONTEXTVAR );
		
		if( stores == null ) {
			// if there is no stores map, create a new one
			stores = new HashMap<Long, Store>();
		}

		// get an handle for the store
		long storeHandle = getHandle();

		// place the store in the stores map
		stores.put( new Long( storeHandle ), store );

		// save the updated stores map back in the context
		context.setXQueryContextVar( MailModule.STORES_CONTEXTVAR, stores );

		return( storeHandle );
	}
	
	
	/**
	 * Remove the store from the specified XQueryContext
	 * 
	 * @param context The context to remove the store for
	 */
	final static synchronized  void removeStore( XQueryContext context, long storeHandle ) 
	{
		// get the existing stores map from the context
		HashMap<Long, Store> stores = (HashMap<Long, Store>)context.getXQueryContextVar( MailModule.STORES_CONTEXTVAR );
		
		if( stores != null ) {
			stores.remove( new Long( storeHandle ) ) ;

			// update the context
			context.setXQueryContextVar( MailModule.STORES_CONTEXTVAR, stores );
		}
	}
	
	
	/**
	 * Closes all the open stores for the specified XQueryContext
	 * 
	 * @param context The context to close stores for
	 */
	private final static synchronized void closeAllStores( XQueryContext context ) 
	{
		// get the existing stores map from the context
		HashMap<Long, Store> stores = (HashMap<Long, Store>)context.getXQueryContextVar( MailModule.STORES_CONTEXTVAR );
		
		if( stores != null ) {
			// iterate over each store
			Set<Long> keys = stores.keySet();
			for( Iterator<Long> itKeys = keys.iterator(); itKeys.hasNext(); ) {
				// get the store
				Long storeHandle = itKeys.next();
				Store store = stores.get( storeHandle );
				
				try {
					// close the store
					store.close();

					// remove it from the stores map
					stores.remove( storeHandle) ;
				} 
				catch( MessagingException me ) {
					LOG.debug( "Unable to close Mail Store", me );
				}
			}

			// update the context
			context.setXQueryContextVar( MailModule.STORES_CONTEXTVAR, stores );
		}
	}
	
	
	//***************************************************************************
	//*
	//*    Folder Methods
	//*
	//***************************************************************************/
	
	/**
	 * Retrieves a previously saved Folder from the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery containing the Folder
	 * @param folderHandle	 	The handle of the Folder to retrieve from the Context of the XQuery
	 */
	final static Folder retrieveFolder( XQueryContext context, long folderHandle ) 
	{
		Folder folder = null;
		
		// get the existing folders map from the context
		
		HashMap<Long, Folder> folders = (HashMap<Long, Folder>)context.getXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR );
		
		if( folders != null ) {
			folder = folders.get( new Long( folderHandle ) );
		}

		return( folder );
	}


	/**
	 * Saves a Folder in the Context of an XQuery
	 * 
	 * @param context 	The Context of the XQuery to save the Folder in
	 * @param folder 	The Folder to store
	 * 
	 * @return A unique handle representing the Store
	 */
	final static synchronized long storeFolder( XQueryContext context, Folder folder ) 
	{
		// get the existing stores map from the context
		
		HashMap<Long, Folder> folders = (HashMap<Long, Folder>)context.getXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR );
		
		if( folders == null ) {
			// if there is no folders map, create a new one
			folders = new HashMap<Long, Folder>();
		}

		// get an handle for the folder
		long folderHandle = getHandle();

		// place the store in the folders map
		folders.put( new Long( folderHandle ), folder );

		// save the updated folders map back in the context
		context.setXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR, folders );

		return( folderHandle );
	}
	
	
	/**
	 * Remove the folder from the specified XQueryContext
	 * 
	 * @param context The context to remove the store for
	 */
	final static synchronized void removeFolder( XQueryContext context, long folderHandle ) 
	{
		// get the existing folders map from the context
		HashMap<Long, Folder> folders = (HashMap<Long, Folder>)context.getXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR );
		
		if( folders != null ) {
			folders.remove( new Long( folderHandle ) ) ;

			// update the context
			context.setXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR, folders );
			
			// get the existing folderMsgLists map from the context and remove all the folder's message lists
		
			HashMap<Long, HashMap<Long, Object>> folderMsgLists = (HashMap<Long, HashMap<Long, Object>>)context.getXQueryContextVar( MailModule.FOLDERMSGLISTS_CONTEXTVAR );
			HashMap msgLists = (HashMap)context.getXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR );
		
			if( folderMsgLists != null ) {
				// get the folders message list
				HashMap<Long, Object> folderMsgList = (HashMap<Long, Object>)folderMsgLists.get( new Long( folderHandle ) );
				
				if( folderMsgList != null ) {
					// iterate over each message list in this folder
					Set<Long> keys = folderMsgList.keySet();
					for( Iterator<Long> itKeys = keys.iterator(); itKeys.hasNext(); ) {
						Long msgList = itKeys.next();
						if( msgLists != null ) {
							msgLists.remove( msgList ) ;
						}
					}
					
					folderMsgLists.remove( new Long( folderHandle ) );
				}
				
				// update the context
				context.setXQueryContextVar( MailModule.FOLDERMSGLISTS_CONTEXTVAR, folderMsgLists );
			}
		}	
	}
	
	
	/**
	 * Closes all the open folders for the specified XQueryContext
	 * 
	 * @param context The context to close folders for
	 */
	private final static synchronized void closeAllFolders( XQueryContext context ) 
	{
		// get the existing folders map from the context
		HashMap<Long, Folder> folders = (HashMap<Long, Folder>)context.getXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR );
		
		if( folders != null ) {
			// iterate over each folder
			Set<Long> keys = folders.keySet();
			for( Iterator<Long> itKeys = keys.iterator(); itKeys.hasNext(); ) {
				// get the folder
				Long folderHandle = itKeys.next();
				Folder folder = folders.get( folderHandle );
				
				try {
					// close the folder
					folder.close( false );

					// remove it from the folders map
					folders.remove( folderHandle ) ;
				} 
				catch( MessagingException me ) {
					LOG.debug( "Unable to close Mail Folder", me );
				}
			}

			// update the context
			context.setXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR, folders );
		}
	}
	
	
	//***************************************************************************
	//*
	//*    Message List Methods
	//*
	//***************************************************************************/
	
	/**
	 * Retrieves a previously saved MessageList from the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery containing the Message List
	 * @param msgListHandle	 	The handle of the Message List to retrieve from the Context of the XQuery
	 */
	final static Message[] retrieveMessageList( XQueryContext context, long msgListHandle ) 
	{
		Message[] msgList = null;
		
		// get the existing msgLists map from the context
		
		HashMap<Long, Message[]> msgLists = (HashMap<Long, Message[]>)context.getXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR );
		
		if( msgLists != null ) {
			msgList = msgLists.get( new Long( msgListHandle ) );
		}

		return( msgList );
	}


	/**
	 * Saves a MessageList in the Context of an XQuery
	 * 
	 * @param context 	The Context of the XQuery to save the MessageList in
	 * @param msgList 	The MessageList to store
	 * 
	 * @return A unique handle representing the Store
	 */
	final static synchronized long storeMessageList( XQueryContext context, Message[] msgList, long folderHandle ) 
	{
		// get the existing msgLists map from the context
		
		HashMap<Long, Message[]> msgLists = (HashMap<Long, Message[]>)context.getXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR );
		
		if( msgLists == null ) {
			// if there is no msgLists map, create a new one
			msgLists = new HashMap<Long, Message[]>();
		}

		// get an handle for the msgList
		long msgListHandle = getHandle();

		// place the msgList in the msgLists map
		msgLists.put( new Long( msgListHandle ), msgList );

		// save the updated msgLists map back in the context
		context.setXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR, msgLists );
		
		// get the existing folderMsgLists map from the context
		
		HashMap<Long, HashMap<Long, Message[]>> folderMsgLists = (HashMap<Long, HashMap<Long, Message[]>>)context.getXQueryContextVar( MailModule.FOLDERMSGLISTS_CONTEXTVAR );
		
		if( folderMsgLists == null ) {
			// if there is no folderMsgLists map, create a new one
			folderMsgLists = new HashMap<Long, HashMap<Long, Message[]>>();
		}	
		
		// get the folders message list
		HashMap<Long, Message[]> folderMsgList = (HashMap<Long, Message[]>)folderMsgLists.get( new Long( folderHandle ) );
		
		if( folderMsgList == null ) {
			folderMsgList = new HashMap<Long, Message[]>();
			folderMsgLists.put( new Long( folderHandle ), folderMsgList );
		}
		
		// place the msgList in the folderMsgList map
		folderMsgList.put( new Long( msgListHandle ), msgList );

		// save the updated folderMsgLists map back in the context
		context.setXQueryContextVar( MailModule.FOLDERMSGLISTS_CONTEXTVAR, folderMsgLists );

		return( msgListHandle );
	}
	
	
	/**
	 * Remove the MessageList from the specified XQueryContext
	 * 
	 * @param context The context to remove the MessageList for
	 */
	final static synchronized void removeMessageList( XQueryContext context, long msgListHandle ) 
	{
		// get the existing msgLists map from the context
		HashMap<Long, Message[]> msgLists = (HashMap<Long, Message[]>)context.getXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR );
		
		if( msgLists != null ) {
			msgLists.remove( new Long( msgListHandle ) ) ;

			// update the context
			context.setXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR, msgLists );
		}
	}
	
	
	/**
	 * Closes all the open MessageLists for the specified XQueryContext
	 * 
	 * @param context The context to close MessageLists for
	 */
	private final static synchronized void closeAllMessageLists( XQueryContext context ) 
	{
		// get the existing msgLists map from the context
		HashMap<Long, Message[]> msgLists = (HashMap<Long, Message[]>)context.getXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR );
		
		if( msgLists != null ) {
			// iterate over each msgList
			Set<Long> keys = msgLists.keySet();
			for( Iterator<Long> itKeys = keys.iterator(); itKeys.hasNext(); ) {
				msgLists.remove( itKeys.next() ) ;
			}

			// update the context
			context.setXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR, msgLists );
		}
	}
	
	
	
	//***************************************************************************
	//*
	//*    Common Methods
	//*
	//***************************************************************************/
	
	/**
	 * Returns a Unique handle based on the System Time
	 * 
	 * @return The Unique handle
	 */
	private static synchronized long getHandle() 
	{
		return( currentSessionHandle++ );
	}
	
	
	/**
	 * Resets the Module Context and closes any open mail stores/folders/message lists for the XQueryContext
	 * 
	 * @param context The XQueryContext
	 */
	public void reset( XQueryContext context ) 
	{
		// reset the module context
		super.reset( context );
		
		// close any open MessageLists
		closeAllMessageLists( context );

		// close any open folders
		closeAllFolders( context );
		
		// close any open stores
		closeAllStores( context );
	}
}
