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
 *  $Id: MailModule.java 10647 2009-11-26 17:27:50Z shabanovd $
 */

package org.exist.xquery.modules.memcached;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.spy.memcached.MemcachedClient;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * eXist Memcached Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows
 * getting and setting stuff via Memcached protocol.
 *   
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.5
 *
 */
public class MemcachedModule extends AbstractInternalModule
{ 
	protected final static Logger LOG = LogManager.getLogger( MemcachedModule.class );
	
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/memcached";
	
	public final static String PREFIX = "mcache";
    public final static String INCLUSION_DATE = "2010-08-01";
    public final static String RELEASED_IN_VERSION = "eXist-2.0 (spymemcached-based)";
    
    private static final Map<Long, MemcachedClient> clients = new HashMap<>();
	
	private final static FunctionDef[] functions = {
		
		new FunctionDef( MemcachedClientFunction.signatures[0], MemcachedClientFunction.class ),
		new FunctionDef( MemcachedGetFunction.signatures[0], MemcachedGetFunction.class ),
		new FunctionDef( MemcachedStoreFunction.signatures[0], MemcachedStoreFunction.class ),
		new FunctionDef( MemcachedStoreFunction.signatures[1], MemcachedStoreFunction.class ),
		new FunctionDef( MemcachedStoreFunction.signatures[2], MemcachedStoreFunction.class ),
		new FunctionDef( MemcachedFlushFunction.signatures[0], MemcachedFlushFunction.class ),
		new FunctionDef( MemcachedDeleteFunction.signatures[0], MemcachedDeleteFunction.class ),
		new FunctionDef( MemcachedShutdownFunction.signatures[0], MemcachedShutdownFunction.class )
				
	};
	
	public final static String CLIENTS_CONTEXTVAR		= "_eXist_memcached_clients";

	private static AtomicLong currentSessionHandle = new AtomicLong(System.currentTimeMillis());
	
	
	public MemcachedModule(Map<String, List<? extends Object>> parameters)
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
		return( "A module for getting and setting stuff in memcached" );
	}
	
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

	
	final static synchronized MemcachedClient retrieveClient(final long clientHandle)
	{
		return clients.get(Long.valueOf(clientHandle));
	}


	final static synchronized long storeClient(final MemcachedClient client)
	{
		final long clientHandle = getHandle();
		clients.put(Long.valueOf(clientHandle), client);
		return clientHandle;
	}

	final static synchronized  void shutdownClient( final long clientHandle )
	{
		final MemcachedClient client = clients.remove(Long.valueOf(clientHandle));
		client.shutdown();
	}
	
	protected static long getHandle()
	{
		return currentSessionHandle.incrementAndGet();
	}
	
}
