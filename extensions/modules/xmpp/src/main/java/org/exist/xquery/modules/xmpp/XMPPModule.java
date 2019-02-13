/*
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
 * $Id$
 */
package org.exist.xquery.modules.xmpp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.XMPPConnection;

/**
 * eXist XMPP Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows
 * chats via XMPP protocol.
 *   
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.5
 *
 */
public class XMPPModule extends AbstractInternalModule
{ 
	protected final static Logger LOG = LogManager.getLogger( XMPPModule.class );
	
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xmpp";
	
	public final static String PREFIX = "xmpp";
    public final static String INCLUSION_DATE = "2010-02-14";
    public final static String RELEASED_IN_VERSION = "eXist-2.0 (Smack-based)";
    
    private static HashMap<Long, XMPPConnection> connections = new HashMap<Long, XMPPConnection>();
    private static HashMap<Long, Chat> chats = new HashMap<Long, Chat>();
	
	private final static FunctionDef[] functions = {
		
		new FunctionDef( XMPPConnectionFunction.signatures[0], XMPPConnectionFunction.class ),
		new FunctionDef( XMPPDisconnectFunction.signatures[0], XMPPDisconnectFunction.class ),
		new FunctionDef( XMPPConnectFunction.signatures[0], XMPPConnectFunction.class ),
		new FunctionDef( XMPPLoginFunction.signatures[0], XMPPLoginFunction.class ),
		new FunctionDef( XMPPLoginFunction.signatures[1], XMPPLoginFunction.class ),
		new FunctionDef( XMPPLoginFunction.signatures[2], XMPPLoginFunction.class ),
		new FunctionDef( XMPPChatFunction.signatures[0],  XMPPChatFunction.class ),
		new FunctionDef( XMPPSendMessageFunction.signatures[0],  XMPPSendMessageFunction.class )
				
	};
	
	public final static String CONNECTIONS_CONTEXTVAR		= "_eXist_xmpp_connections";
	public final static String CHATS_CONTEXTVAR 			= "_eXist_xmpp_chats";

	private static long currentSessionHandle = System.currentTimeMillis();
	
	
	public XMPPModule(Map<String, List<? extends Object>> parameters)
	{
		super( functions, parameters );
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
		return( "A module for XMPP messaging" );
	}
	
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

	
	final static XMPPConnection retrieveConnection( long connectionHandle ) 
	{
		return ( connections.get( new Long( connectionHandle ) ) );
	}


	final static synchronized long storeConnection(XMPPConnection connection ) 
	{
		long connectionHandle = getHandle();
		connections.put( new Long( connectionHandle ), connection );
		return( connectionHandle );
	}

	final static synchronized  void closeConnection( long connectionHandle ) 
	{
		XMPPConnection connection = connections.get( connectionHandle );
		connection.disconnect();
		connections.remove( new Long( connectionHandle ) ) ;
	}
	
	
	final static Chat retrieveChat(long chatHandle ) 
	{
		return( chats.get( new Long( chatHandle ) ) );
	}

	final static synchronized long storeChat(Chat chat, long chatHandle ) 
	{
		chats.put( new Long( chatHandle ), chat );
		return( chatHandle );
	}
	
	final static synchronized long closeChat( XQueryContext context, Chat chat )
	{
		return storeChat( chat, getHandle() );
	}
	
	
	final static synchronized  void removeChat(long chatHandle ) 
	{
		chats.remove( new Long( chatHandle ) ) ;
	}
	
	protected static synchronized long getHandle() 
	{
		return( currentSessionHandle++ );
	}
	
}
