/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.mail;

import java.util.Map.Entry;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.modules.ModuleUtils.ContextMapEntryModifier;
import org.exist.xquery.modules.ModuleUtils.ContextMapModifierWithoutResult;

/**
 * eXist Mail Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows email to
 * be sent from XQuery using either SMTP or Sendmail.  
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:josemariafg@gmail.com">ljo
 * @author José María Fernández</a>
 * @serial 2011-09-06
 * @version 1.4.1
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 */
public class MailModule extends AbstractInternalModule { 
    
    private final static Logger LOG = LogManager.getLogger( MailModule.class );
	
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/mail";
	
    public final static String PREFIX = "mail";
    // JavaMail-based from 2009-03-14
    // makes the need for versioning of the functions obvious too /ljo
    public final static String INCLUSION_DATE = "2005-05-12, 2009-03-14";
    public final static String RELEASED_IN_VERSION = "eXist-1.2 (JavaMail-based in trunk)";
	
    private final static FunctionDef[] functions = {
        new FunctionDef(MailSessionFunctions.signatures[0], MailSessionFunctions.class),
        new FunctionDef(MailStoreFunctions.signatures[0], MailStoreFunctions.class),
        new FunctionDef(MailStoreFunctions.signatures[1], MailStoreFunctions.class),
        new FunctionDef(MailFolderFunctions.signatures[0], MailFolderFunctions.class),
        new FunctionDef(MailFolderFunctions.signatures[1], MailFolderFunctions.class),
        new FunctionDef(MessageListFunctions.signatures[0], MessageListFunctions.class),
        new FunctionDef(MessageListFunctions.signatures[1], MessageListFunctions.class),
        new FunctionDef(MessageListFunctions.signatures[2], MessageListFunctions.class),
        new FunctionDef(MessageListFunctions.signatures[3], MessageListFunctions.class),
        new FunctionDef(MessageFunctions.signatures[0], MessageFunctions.class),

        new FunctionDef(SendEmailFunction.signatures[0], SendEmailFunction.class),

        // deprecated functions:
        new FunctionDef(SendEmailFunction.deprecated, SendEmailFunction.class)
    };
	
    public final static String SESSIONS_CONTEXTVAR = "_eXist_mail_sessions";
    public final static String STORES_CONTEXTVAR = "_eXist_mail_stores";
    public final static String FOLDERS_CONTEXTVAR = "_eXist_mail_folders";
    public final static String FOLDERMSGLISTS_CONTEXTVAR = "_eXist_folder_message_lists";
    public final static String MSGLISTS_CONTEXTVAR = "_eXist_mail_message_lists";

    private static long currentSessionHandle = System.currentTimeMillis();
	
	
    public MailModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
    }
	

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }
	

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }
	

    @Override
    public String getDescription() {
        return "A module for performing email related functions";
    }
	
    @Override
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
    static Session retrieveSession(XQueryContext context, long sessionHandle) {
        return ModuleUtils.retrieveObjectFromContextMap(context, MailModule.SESSIONS_CONTEXTVAR, sessionHandle);
    }

    /**
     * Stores a Session in the Context of an XQuery
     * 
     * @param context 	The Context of the XQuery to store the Session in
     * @param session 	The Session to store
     * 
     * @return A unique handle representing the Session
     */
    static long storeSession(XQueryContext context, Session session) {
        return ModuleUtils.storeObjectInContextMap(context, MailModule.SESSIONS_CONTEXTVAR, session);
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
    static Store retrieveStore(XQueryContext context, long storeHandle) {
        return ModuleUtils.retrieveObjectFromContextMap(context, MailModule.STORES_CONTEXTVAR, storeHandle);
    }

    /**
     * Saves a Store in the Context of an XQuery
     * 
     * @param context 	The Context of the XQuery to save the Store in
     * @param store 	The Store to store
     * 
     * @return A unique handle representing the Store
     */
    static long storeStore(XQueryContext context, Store store) {
        return ModuleUtils.storeObjectInContextMap(context, MailModule.STORES_CONTEXTVAR, store);
    }
	
    /**
     * Remove the store from the specified XQueryContext
     * 
     * @param context The context to remove the store for
     */
    static void removeStore(XQueryContext context, final long storeHandle) {
        
        ModuleUtils.modifyContextMap(context, MailModule.STORES_CONTEXTVAR, (ContextMapModifierWithoutResult<Store>) map -> map.remove(storeHandle));
        
        //update the context
        //context.setXQueryContextVar(MailModule.STORES_CONTEXTVAR, stores);
    }
	
    /**
     * Closes all the open stores for the specified XQueryContext
     * 
     * @param context The context to close stores for
     */
    private static void closeAllStores(XQueryContext context)  {
        ModuleUtils.modifyContextMap(context,  MailModule.STORES_CONTEXTVAR, new ContextMapEntryModifier<Store>(){
            @Override
            public void modifyWithoutResult(final Map<Long, Store> map) {
                super.modifyWithoutResult(map);
                
                //remove all stores from map
                map.clear();
            }

            @Override
            public void modifyEntry(final Entry<Long, Store> entry) {
                final Store store = entry.getValue();
                try {
                    // close the store
                    store.close();
                }  catch(MessagingException me) {
                    LOG.warn("Unable to close Mail Store: {}", me.getMessage(), me);
                }
            }
        });
        
        // update the context
        //context.setXQueryContextVar(MailModule.STORES_CONTEXTVAR, stores);
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
    static Folder retrieveFolder(XQueryContext context, long folderHandle) {
        return ModuleUtils.retrieveObjectFromContextMap(context, MailModule.FOLDERS_CONTEXTVAR, folderHandle);
    }

    /**
     * Saves a Folder in the Context of an XQuery
     * 
     * @param context 	The Context of the XQuery to save the Folder in
     * @param folder 	The Folder to store
     * 
     * @return A unique handle representing the Store
     */
    static long storeFolder(XQueryContext context, Folder folder) {
        return ModuleUtils.storeObjectInContextMap(context,  MailModule.FOLDERS_CONTEXTVAR, folder);
    }
	
    /**
     * Remove the folder from the specified XQueryContext
     * 
     * @param context The context to remove the store for
     */
    static void removeFolder(final XQueryContext context, final long folderHandle) {
            
        ModuleUtils.modifyContextMap(context, MailModule.FOLDERS_CONTEXTVAR, (ContextMapModifierWithoutResult<Folder>) map -> {

            //remove the message lists for the folder
            ModuleUtils.modifyContextMap(context, MailModule.FOLDERMSGLISTS_CONTEXTVAR, (ContextMapModifierWithoutResult<Map<Long, Message[]>>) map12 -> {

                final Map<Long, Message[]> folderMsgList = map12.get(folderHandle);

                ModuleUtils.modifyContextMap(context, MailModule.MSGLISTS_CONTEXTVAR, (ContextMapModifierWithoutResult<Message[]>) map1 -> folderMsgList.keySet().forEach(map1::remove));

                //remove the folder message kist
                map12.remove(folderHandle);
            });

            //remove the folder
            map.remove(folderHandle);
        });
    }
	
	
    /**
     * Closes all the open folders for the specified XQueryContext
     * 
     * @param context The context to close folders for
     */
    private static void closeAllFolders(XQueryContext context) {
        ModuleUtils.modifyContextMap(context, MailModule.FOLDERS_CONTEXTVAR, new ContextMapEntryModifier<Folder>(){

            @Override
            public void modifyWithoutResult(final Map<Long, Folder> map) {
                super.modifyWithoutResult(map);
                
                //remove all from the folders map
                map.clear();
            }
            
            @Override
            public void modifyEntry(final Entry<Long, Folder> entry) {
                final Folder folder = entry.getValue();

                //close the folder
                try {
                    folder.close(false);
                } catch(MessagingException me) {
                    LOG.warn("Unable to close Mail Folder: {}", me.getMessage(), me);
                }
            }
        });
        
        // update the context
        // context.setXQueryContextVar( MailModule.FOLDERS_CONTEXTVAR, folders );
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
    static Message[] retrieveMessageList(XQueryContext context, long msgListHandle) {
        return ModuleUtils.retrieveObjectFromContextMap(context, MailModule.MSGLISTS_CONTEXTVAR, msgListHandle);
    }


    /**
     * Saves a MessageList in the Context of an XQuery
     * 
     * @param context 	The Context of the XQuery to save the MessageList in
     * @param msgList 	The MessageList to store
     * 
     * @return A unique handle representing the Store
     */
    static long storeMessageList(XQueryContext context, final Message[] msgList, final long folderHandle) {
            
        final long msgListHandle = ModuleUtils.storeObjectInContextMap(context, MailModule.MSGLISTS_CONTEXTVAR, msgList);

        ModuleUtils.modifyContextMap(context, MailModule.FOLDERMSGLISTS_CONTEXTVAR, (ContextMapModifierWithoutResult<Map<Long, Message[]>>) map -> {
            Map<Long, Message[]> folderMsgList = map.computeIfAbsent(folderHandle, k -> new HashMap<>());

            folderMsgList.put(msgListHandle, msgList);
        });

        return msgListHandle;
    }
	

    /**
     * Remove the MessageList from the specified XQueryContext
     * 
     * @param context The context to remove the MessageList for
     */
    static void removeMessageList(XQueryContext context, final long msgListHandle) {
        ModuleUtils.modifyContextMap(context, MailModule.MSGLISTS_CONTEXTVAR, (ContextMapModifierWithoutResult<Message[]>) map -> map.remove(msgListHandle));
        
        // update the context
        //context.setXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR, msgLists );
    }
	
    /**
     * Closes all the open MessageLists for the specified XQueryContext
     * 
     * @param context The context to close MessageLists for
     */
    private static void closeAllMessageLists(XQueryContext context) {
        ModuleUtils.modifyContextMap(context, MailModule.MSGLISTS_CONTEXTVAR, (ContextMapModifierWithoutResult<Message[]>) Map::clear);
        
        // update the context
        //context.setXQueryContextVar( MailModule.MSGLISTS_CONTEXTVAR, msgLists );
    }
	
	
    /**
     * Resets the Module Context and closes any open mail stores/folders/message lists for the XQueryContext
     * 
     * @param context The XQueryContext
     */
    @Override
    public void reset( XQueryContext context, boolean keepGlobals ) {
        // reset the module context
        super.reset(context, keepGlobals);

        // close any open MessageLists
        closeAllMessageLists(context);

        // close any open folders
        closeAllFolders(context);

        // close any open stores
        closeAllStores(context);
    }
}