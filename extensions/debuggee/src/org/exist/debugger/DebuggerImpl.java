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
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.exist.debuggee.dbgp.CodecFactory;
import org.exist.debuggee.dbgp.packets.Source;
import org.exist.debugger.Debugger;
import org.exist.debugger.DebuggingSource;
import org.exist.debugger.dbgp.ProtocolHandler;
import org.exist.debugger.model.Breakpoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggerImpl implements Debugger {
	
    protected final static Logger LOG = Logger.getLogger(DebuggerImpl.class);

    private NioSocketAcceptor acceptor;

    private int eventPort = 9000;

    private IoSession session;
	
	//uri -> source
	private Map<String, DebuggingSource> sources = new HashMap<String, DebuggingSource>();
	

	public DebuggerImpl() {
	}
	
	protected void setSession(IoSession session) {
		this.session = session;
	}

	public DebuggingSource init(String url) throws IOException, ExceptionTimeout {
		acceptor = new NioSocketAcceptor();
		acceptor.getFilterChain().addLast(
				"protocol", new ProtocolCodecFilter(new CodecFactory()));
		acceptor.setHandler(new ProtocolHandler(this));
		acceptor.bind(new InetSocketAddress(eventPort));

		LOG.info("Debugger is listening at port "+eventPort);

		Thread session = new Thread(new HttpSession(url));
		session.start();
		
		Response response = getResponse("init", 30 * 1000); // 30s timeout
		
		return getSource(response.getAttribute("fileuri")); //TODO: fileuri as constant???
	}
	
	/* (non-Javadoc)
	 * @see org.exist.debugger.Debugger#source(java.lang.String)
	 */
	public DebuggingSource getSource(String fileURI) {
		if (sources.containsKey(fileURI))
			return sources.get(fileURI);

		Source command = new Source(session, "");
		command.setFileURI(fileURI);
		Response response = command.toDebuggee();
		
		if ("1".equals(response.getAttribute("success"))) {
			DebuggingSourceImpl source = new DebuggingSourceImpl(this, fileURI);
			source.setText(response.getText());
			
			sources.put(fileURI, source);
			
			return source;
		}
		
		return null;
	}

	public Breakpoint addBreakpoint(Breakpoint breakpoint) {
		// TODO Auto-generated method stub
		return null;
	}

	public void sessionClosed() {
		// TODO Auto-generated method stub
		
	}
	
	//weak map???
	private Map<String, Response> responses = new HashMap<String, Response>();
	
	protected synchronized void addResponse(Response response) {
		responses.put(response.getTransactionID(), response);
		notifyAll();
	}

	public Response getResponse(String transactionID) {
		try {
			return getResponse(transactionID, 0);
		} catch (ExceptionTimeout e) {
			return null;
		}
	}
	public synchronized Response getResponse(String transactionID, int timeout) throws ExceptionTimeout {
		long sTime = System.currentTimeMillis();
		
		while (!responses.containsKey(transactionID)) {
			try {
				wait(timeout);
				
				if (timeout != 0 && (System.currentTimeMillis() - sTime) > timeout)
					throw new ExceptionTimeout();
			} catch (InterruptedException e) {
			} 
		}
		
		if (responses.containsKey(transactionID)) {
			Response response = responses.get(transactionID);
			responses.remove(transactionID);
			
			return response;
		}

		//throw error???
		return null;
	}

}
