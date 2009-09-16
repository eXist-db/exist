/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.debuggee;

import java.net.InetSocketAddress;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.exist.debuggee.dgbp.DGBPProtocolHandler;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeConnection implements Runnable {
	
	private String host = "127.0.0.1";
	private int port = 9000;
	
	private NioSocketConnector connector = null;
	private ConnectFuture cf = null;
	
	public DebuggeeConnection() {
		Thread thread = new Thread(this);
		thread.start();
	}
	
	public void run() {
		// Create TCP/IP connector.
		connector = new NioSocketConnector();
		
		// Set connect timeout for 5 seconds.
		connector.setConnectTimeoutMillis(5*1000L);

		// Start communication.
		connector.setHandler(new DGBPProtocolHandler());
		cf = connector.connect(new InetSocketAddress(host, port));

		// Wait for the connection attempt to be finished.
		cf.awaitUninterruptibly();
		cf.getSession().getCloseFuture().awaitUninterruptibly();
		
		connector.dispose();
	}

	public boolean isConnected() {
		//XXX: resolve concurrency problem here!!!
		return (cf != null);
	}
}
