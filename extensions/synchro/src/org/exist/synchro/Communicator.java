/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
 *  http://exist-db.org
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
 *  $Id$
 */
package org.exist.synchro;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.jgroups.*;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.MethodLookup;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RpcDispatcher;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("cluster")
public class Communicator extends ReceiverAdapter {

	@ConfigurationFieldAsAttribute("id")
	private String id = "eXist-dzone";

	@ConfigurationFieldAsElement("collection")
	protected XmldbURI collectionURI = XmldbURI.ROOT_COLLECTION_URI;
	
	@ConfigurationFieldAsElement("protocol")
	private String protocol="udp.xml";
	
	private final static Logger LOG = Logger.getLogger(Communicator.class);

	public static final String COMMUNICATOR = "cluster.communicator";
	
	private static final short CREATE_COLLECTION = 1;
	private static final short UPDATE_COLLECTION = 2;
	private static final short DELETE_COLLECTION = 3;
	
	private static final short CREATE_DOCUMENT = 5;
	private static final short UPDATE_DOCUMENT = 6;
	private static final short DELETE_DOCUMENT = 7;

	protected static Map<Short,Method> methods;

	static {
		try {
			methods=new HashMap<Short,Method>(6);
			methods.put(CREATE_COLLECTION, 
					Communicator.class.getMethod("_createCollection",
						String.class, XmldbURI.class));
			methods.put(UPDATE_COLLECTION, 
					Communicator.class.getMethod("_updateCollection",
						String.class, XmldbURI.class));
			methods.put(DELETE_COLLECTION, 
					Communicator.class.getMethod("_deleteCollection",
						String.class, XmldbURI.class));

			methods.put(CREATE_DOCUMENT, 
					Communicator.class.getMethod("_createDocument",
						String.class, XmldbURI.class));
			methods.put(UPDATE_DOCUMENT, 
					Communicator.class.getMethod("_updateDocument",
						String.class, XmldbURI.class));
			methods.put(DELETE_DOCUMENT, 
					Communicator.class.getMethod("_deleteDocument",
						String.class, XmldbURI.class));
		} catch(NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected JChannel channel;
	protected RpcDispatcher dispatcher;
	
	private RequestOptions opts = RequestOptions.SYNC;

	protected Communicator(Database db) throws EXistException {
		DBBroker broker = null;
		try {
			channel = new JChannel(protocol);
			
			init();

			channel.connect(id);

			broker = db.get(db.getSecurityManager().getSystemSubject());
	        
			//initialize watcher triggers
	        Collection collection = broker.getCollection(collectionURI);
	        if (collection != null) {
	        	CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
	        	CollectionConfiguration collConf = manager.getOrCreateCollectionConfiguration(broker, collection);

        		Map<String, List<?>> params = new HashMap<String, List<?>>();
        		List<Communicator> val = new ArrayList<Communicator>();
        		val.add(this);
        		params.put(COMMUNICATOR, val);
        		
        		try {
					collConf.registerTrigger(broker, 
							"store,update,remove", 
							"org.exist.synchro.WatchDocument", params);

					collConf.registerTrigger(broker, 
							"create-collection,rename-collection,delete-collection", 
							"org.exist.synchro.WatchCollection", params);
	        	} catch (CollectionConfigurationException e) {
					LOG.error("Changers watcher could not the initialized.", e);
				}
	        }
		} catch (ChannelException e) {
			throw new EXistException(e);
		} finally {
			db.release(broker);
		}
	}

	public void shutdown() {
		if (dispatcher != null) {
			dispatcher.stop();
			dispatcher=null;
		}
		if (channel != null) {
			channel.close();
			channel = null;
		}
	}
	
	protected final void init() {
		dispatcher=new RpcDispatcher(channel, this, this, this);
		dispatcher.setMethodLookup(new MethodLookup() {
			public Method findMethod(short id) {
				return methods.get(id);
			}
		});
		dispatcher.setServerObject(this);
		dispatcher.start();
	}
	
	private void callRemoteMethods(MethodCall methodCall) {
		try {
			dispatcher.callRemoteMethods(null, methodCall, opts);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	public void createCollection(XmldbURI uri) {
		callRemoteMethods( new MethodCall(CREATE_COLLECTION, channel.getName(), uri) );
		
	}

	public void updateCollection(XmldbURI uri) {
		callRemoteMethods( new MethodCall(UPDATE_COLLECTION, channel.getName(), uri) );
	}
	
	public void deleteCollection(XmldbURI uri) {
		callRemoteMethods( new MethodCall(DELETE_COLLECTION, channel.getName(), uri) );
	}

	public void createDocument(XmldbURI uri) {
		callRemoteMethods( new MethodCall(CREATE_DOCUMENT, channel.getName(), uri) );
		
	}

	public void updateDocument(XmldbURI uri) {
		callRemoteMethods( new MethodCall(UPDATE_DOCUMENT, channel.getName(), uri) );
	}
	
	public void deleteDocument(XmldbURI uri) {
		callRemoteMethods( new MethodCall(DELETE_DOCUMENT, channel.getName(), uri) );
	}


	public void _createCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" create collection uri = "+uri);
	}

	public void _updateCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" update collection uri = "+uri);
	}
	
	public void _deleteCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" delete collection uri = "+uri);
	}

	public void _createDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" create document uri = "+uri);
	}

	public void _updateDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" update document uri = "+uri);
	}
	
	public void _deleteDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" delete document uri = "+uri);
	}
}
