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
import java.util.HashMap;
import java.util.Map;

import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.xmldb.XmldbURI;
import org.jgroups.*;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.MethodLookup;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.RspList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("cluster")
public class Communicator extends ReceiverAdapter {

	@ConfigurationFieldAsAttribute("id")
	private String id = "eXist-dzone";

	@ConfigurationFieldAsElement("collection")
	protected XmldbURI collection = XmldbURI.ROOT_COLLECTION_URI;
	
	@ConfigurationFieldAsElement("protocol")
	private String protocol="udp.xml";
	
	private static final short CREATE=1;
	private static final short UPDATE=2;
	private static final short DELETE=3;
	
	protected static Map<Short,Method> methods;

	static {
		try {
			methods=new HashMap<Short,Method>(3);
			methods.put(CREATE, 
					Communicator.class.getMethod("_create",
						String.class, XmldbURI.class));
			methods.put(UPDATE, 
					Communicator.class.getMethod("_update",
						String.class, XmldbURI.class));
			methods.put(DELETE, 
					Communicator.class.getMethod("_delete",
						String.class, XmldbURI.class));
		} catch(NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected JChannel channel;
	protected RpcDispatcher dispatcher;
	
	private RequestOptions opts = RequestOptions.SYNC;

	protected Communicator() throws ChannelException {
		channel = new JChannel(protocol);
		
		init();

		channel.connect(id);
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
	
	public void create(XmldbURI uri) {
		callRemoteMethods( new MethodCall(CREATE, channel.getName(), uri) );
		
	}

	public void update(XmldbURI uri) {
		callRemoteMethods( new MethodCall(UPDATE, channel.getName(), uri) );
	}
	
	public void delete(XmldbURI uri) {
		callRemoteMethods( new MethodCall(DELETE, channel.getName(), uri) );
	}

	public void _create(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" create uri = "+uri);
	}

	public void _update(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" update uri = "+uri);
	}
	
	public void _delete(String eventOwner, XmldbURI uri) {
		if (!channel.getName().equals(eventOwner))
			System.out.println(""+channel.getName()+" delete uri = "+uri);
	}
}
