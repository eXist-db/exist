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
package org.exist.debuggee;

import java.net.InetSocketAddress;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.exist.debuggee.dbgp.CodecFactory;
import org.exist.debuggee.dbgp.ProtocolHandler;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeConnectionTCP implements DebuggeeConnection {
	
	private String host = "127.0.0.1";
	private int port = 9000;
	
	private NioSocketConnector connector;
	
	public DebuggeeConnectionTCP() {
		// Create TCP/IP connector.
		connector = new NioSocketConnector();
		
		// Set connect timeout for 30 seconds.
		connector.setConnectTimeoutMillis(30*1000L);

		connector.getFilterChain().addLast( "protocol", new ProtocolCodecFilter(new CodecFactory()) );
		
		// Start communication.
		connector.setHandler(new ProtocolHandler());
	}
	
	public IoSession connect() {
		synchronized (connector) {
			try {
				ConnectFuture future = connector.connect(new InetSocketAddress(host, port));
				future.awaitUninterruptibly();
				return future.getSession();
			} catch (RuntimeIoException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
}
