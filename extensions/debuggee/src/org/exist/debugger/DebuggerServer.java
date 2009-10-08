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
package org.exist.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.log4j.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.exist.debuggee.dbgp.CodecFactory;
import org.exist.debugger.dbgp.ProtocolHandler;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggerServer extends IoHandlerAdapter {

    protected final static Logger LOG = Logger.getLogger(DebuggerServer.class);

    private NioSocketAcceptor acceptor;

    private int eventPort = 9000;

    Debugger debuger;
    
	public DebuggerServer(Debugger debuger) throws IOException {
		this.debuger = debuger;
		
		acceptor = new NioSocketAcceptor();
		acceptor.getFilterChain().addLast(
				"protocol", new ProtocolCodecFilter(new CodecFactory()));
		acceptor.setHandler(new ProtocolHandler(debuger));
		acceptor.bind(new InetSocketAddress(eventPort));

		LOG.info("Debuggee is listenig at port "+eventPort);

	}
}
