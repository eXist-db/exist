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

import org.apache.log4j.Category;
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

	//collection's events
	//create
	protected static final short BEFORE_CREATE_COLLECTION	= 0;
	protected static final short AFTER_CREATE_COLLECTION	= 1;
	//update
	protected static final short BEFORE_UPDATE_COLLECTION	= 2;
	protected static final short AFTER_UPDATE_COLLECTION	= 3;
	//copy
	protected static final short BEFORE_COPY_COLLECTION	= 4;
	protected static final short AFTER_COPY_COLLECTION	= 5;
	//move
	protected static final short BEFORE_MOVE_COLLECTION	= 6;
	protected static final short AFTER_MOVE_COLLECTION	= 7;
	//delete
	protected static final short BEFORE_DELETE_COLLECTION	= 8;
	protected static final short AFTER_DELETE_COLLECTION	= 9;
	
	//document's events
	//create
	protected static final short BEFORE_CREATE_DOCUMENT = 10;
	protected static final short AFTER_CREATE_DOCUMENT = 11;
	//update
	protected static final short BEFORE_UPDATE_DOCUMENT = 12;
	protected static final short AFTER_UPDATE_DOCUMENT = 13;
	//copy
	protected static final short BEFORE_COPY_DOCUMENT = 14;
	protected static final short AFTER_COPY_DOCUMENT = 15;
	//move
	protected static final short BEFORE_MOVE_DOCUMENT = 16;
	protected static final short AFTER_MOVE_DOCUMENT = 17;
	//delete
	protected static final short BEFORE_DELETE_DOCUMENT = 18;
	protected static final short AFTER_DELETE_DOCUMENT = 19;

	//permissions's events //TODO: code
	//update
	protected static final short BEFORE_UPDATE_PERMISSIONS = 10;
	protected static final short AFTER_UPDATE_PERMISSIONS = 11;

	protected static Map<Short,Method> methods;

	static {
		try {
			methods=new HashMap<Short,Method>(6);
			methods.put(BEFORE_CREATE_COLLECTION, 
					Communicator.class.getMethod("beforeCreateCollection",
						String.class, XmldbURI.class));
			methods.put(AFTER_CREATE_COLLECTION, 
					Communicator.class.getMethod("afterCreateCollection",
						String.class, XmldbURI.class));
			methods.put(BEFORE_UPDATE_COLLECTION, 
					Communicator.class.getMethod("beforeUpdateCollection",
						String.class, XmldbURI.class));
			methods.put(AFTER_UPDATE_COLLECTION, 
					Communicator.class.getMethod("afterUpdateCollection",
						String.class, XmldbURI.class));
			methods.put(BEFORE_COPY_COLLECTION, 
					Communicator.class.getMethod("beforeCopyCollection",
						String.class, XmldbURI.class));
			methods.put(AFTER_COPY_COLLECTION, 
					Communicator.class.getMethod("afterCopyCollection",
						String.class, XmldbURI.class));
			methods.put(BEFORE_DELETE_COLLECTION, 
					Communicator.class.getMethod("beforeDeleteCollection",
						String.class, XmldbURI.class));
			methods.put(AFTER_DELETE_COLLECTION, 
					Communicator.class.getMethod("afterDeleteCollection",
						String.class, XmldbURI.class));

			methods.put(BEFORE_CREATE_DOCUMENT, 
					Communicator.class.getMethod("beforeCreateDocument",
						String.class, XmldbURI.class));
			methods.put(AFTER_CREATE_DOCUMENT, 
					Communicator.class.getMethod("afterCreateDocument",
						String.class, XmldbURI.class));
			methods.put(BEFORE_UPDATE_DOCUMENT, 
					Communicator.class.getMethod("beforeUpdateDocument",
						String.class, XmldbURI.class));
			methods.put(AFTER_UPDATE_DOCUMENT, 
					Communicator.class.getMethod("afterUpdateDocument",
						String.class, XmldbURI.class));
			methods.put(BEFORE_COPY_DOCUMENT, 
					Communicator.class.getMethod("beforeCopyDocument",
						String.class, XmldbURI.class));
			methods.put(AFTER_COPY_DOCUMENT, 
					Communicator.class.getMethod("afterCopyDocument",
						String.class, XmldbURI.class));
			methods.put(BEFORE_MOVE_DOCUMENT, 
					Communicator.class.getMethod("beforeMoveDocument",
						String.class, XmldbURI.class));
			methods.put(AFTER_MOVE_DOCUMENT, 
					Communicator.class.getMethod("afterMoveDocument",
						String.class, XmldbURI.class));
			methods.put(BEFORE_DELETE_DOCUMENT, 
					Communicator.class.getMethod("beforeDeleteDocument",
						String.class, XmldbURI.class));
			methods.put(AFTER_DELETE_DOCUMENT, 
					Communicator.class.getMethod("afterDeleteDocument",
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
	
	protected JChannel getChannel() {
		return channel;
	}

	protected void callRemoteMethods(MethodCall methodCall) {
		try {
			dispatcher.callRemoteMethods(null, methodCall, opts);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	public void beforeCreateCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before create collection uri = "+uri);
	}

	public void afterCreateCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after create collection uri = "+uri);
	}

	public void beforeUpdateCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before update collection uri = "+uri);
	}

	public void afterUpdateCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after update collection uri = "+uri);
	}

	public void beforeCopyCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before copy collection uri = "+uri);
	}

	public void afterCopyCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after copy collection uri = "+uri);
	}

	public void beforeMoveCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before move collection uri = "+uri);
	}

	public void afterMoveCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after move collection uri = "+uri);
	}

	public void beforeDeleteCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before delete collection uri = "+uri);
	}

	public void afterDeleteCollection(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after delete collection uri = "+uri);
	}

	public void beforeCreateDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before create collection uri = "+uri);
	}

	public void afterCreateDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after create collection uri = "+uri);
	}

	public void beforeUpdateDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before update collection uri = "+uri);
	}

	public void afterUpdateDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after update collection uri = "+uri);
	}

	public void beforeCopyDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before copy collection uri = "+uri);
	}

	public void afterCopyDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after copy collection uri = "+uri);
	}

	public void beforeMoveDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before move collection uri = "+uri);
	}

	public void afterMoveDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after move collection uri = "+uri);
	}

	public void beforeDeleteDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" before delete collection uri = "+uri);
	}

	public void afterDeleteDocument(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" after delete collection uri = "+uri);
	}

}
