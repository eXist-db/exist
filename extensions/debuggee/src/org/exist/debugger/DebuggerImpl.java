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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.exist.debuggee.dbgp.packets.*;
import org.exist.debugger.Debugger;
import org.exist.debugger.DebuggingSource;
import org.exist.debugger.dbgp.CodecFactory;
import org.exist.debugger.dbgp.ProtocolHandler;
import org.exist.debugger.dbgp.ResponseImpl;
import org.exist.debugger.model.Breakpoint;
import org.exist.debugger.model.BreakpointImpl;
import org.exist.debugger.model.Location;
import org.exist.debugger.model.LocationImpl;
import org.exist.debugger.model.Variable;
import org.exist.debugger.model.VariableImpl;
import org.exist.util.Base64Decoder;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class DebuggerImpl implements Debugger, org.exist.debuggee.Status {

	protected final static Logger LOG = Logger.getLogger(DebuggerImpl.class);

	private NioSocketAcceptor acceptor;

	private int eventPort = 9000;

	private IoSession session;

	// uri -> source
	private Map<String, DebuggingSource> sources = new HashMap<String, DebuggingSource>();

	int currentTransactionId = 1;
	
//	private String lastStatus = FIRST_RUN;
	
	protected int responseCode = 0;

	public DebuggerImpl() throws IOException {
		acceptor = new NioSocketAcceptor();
		acceptor.getFilterChain().addLast("protocol",
				new ProtocolCodecFilter(new CodecFactory()));
		acceptor.setHandler(new ProtocolHandler(this));
		acceptor.bind(new InetSocketAddress(eventPort));
	}

	private int getNextTransaction() {
		return currentTransactionId++;
	}

	protected void setSession(IoSession session) {
		this.session = session;
	}

	public DebuggingSource init(String url) throws IOException,
			ExceptionTimeout {
		LOG.info("Debugger is listening at port " + eventPort);

		Thread session = new Thread(new HttpSession(this, url));
		session.start();

		// 30s timeout
		ResponseImpl response = (ResponseImpl) getResponse("init", 30 * 1000); 
		this.session = response.getSession();

		// TODO: fileuri as constant???
		return getSource(response.getAttribute("fileuri"));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.debugger.Debugger#source(java.lang.String)
	 */
	public DebuggingSource getSource(String fileURI) {
		if (fileURI == null)
			return null;
		
		if (sources.containsKey(fileURI))
			return sources.get(fileURI);

		Source command = new Source(session, " -i " + getNextTransaction());
		command.setFileURI(fileURI);
		command.toDebuggee();

		Response response = getResponse(command.getTransactionId());

		if ("1".equals(response.getAttribute("success"))) {
			DebuggingSourceImpl source = new DebuggingSourceImpl(this, fileURI);

			Base64Decoder dec = new Base64Decoder();
			dec.translate(response.getText());
			byte[] c = dec.getByteArray();
			String s = new String(c);

			source.setText(s);

			sources.put(fileURI, source);

			return source;
		}

		return null;
	}
	
	public List<Variable> getVariables(int contextID) {
		ContextGet command = new ContextGet(session, 
				" -i " + getNextTransaction() + 
				" -c " + String.valueOf(contextID));
		command.toDebuggee();

		Response response = getResponse(command.getTransactionId());

		//XXX: handle errors
		List<Variable> variables = new ArrayList<Variable>();
		
		NodeList children = response.getElemetsByName("property");
		for (int i = 0; i < children.getLength(); i++) {
			variables.add(new VariableImpl(children.item(i)));
		}


		return variables;
	}
	

	public List<Location> getStackFrames() {
		StackGet command = new StackGet(session, " -i " + getNextTransaction());
		command.toDebuggee();

		Response response = getResponse(command.getTransactionId());

		//XXX: handle errors
		List<Location> variables = new ArrayList<Location>();
		
		NodeList children = response.getElemetsByName("stack");
		for (int i = 0; i < children.getLength(); i++) {
			variables.add(new LocationImpl(children.item(i)));
		}


		return variables;
	}

	public void sessionClosed() {
		// TODO Auto-generated method stub
	}

	// weak map???
	private Map<String, Response> responses = new HashMap<String, Response>();

	public synchronized void addResponse(Response response) {
		if (currentCommand != null
				&& currentCommand.getTransactionId().equals(
						response.getTransactionID()))
			currentCommand.putResponse(response);

//		if (response.hasAttribute("status"))
//			lastStatus = response.getAttribute("status");
		
		//it should be commands map, this implementation is dangerous
		//rethink!!!
		responses.put(response.getTransactionID(), response);

		notifyAll();
	}

	public Response getResponse(String transactionID) {
		try {
			return getResponse(transactionID, 0);
		} catch (ExceptionTimeout e) {
			return null;
		} catch (IOException e) {
			return null; //UNDERSTAND: throw error?
		}
	}

	public synchronized Response getResponse(String transactionID, int timeout)
			throws ExceptionTimeout, IOException {
		long sTime = System.currentTimeMillis();

		while (!responses.containsKey(transactionID)) {
			try {
				if (responseCode != 0)
					throw new IOException("Got responce code "+responseCode+" on debugging request");
				
				else if (timeout == 0)
					wait(10); //slow down next check
				else
					wait(timeout);

				if (timeout != 0
						&& (System.currentTimeMillis() - sTime) > timeout)
					throw new ExceptionTimeout();
			} catch (InterruptedException e) {
			}
		}

		if (responses.containsKey(transactionID)) {
			Response response = responses.get(transactionID);
			responses.remove(transactionID);

			return response;
		}

		//UNDERSTAND: throw error???
		return null;
	}

	private AbstractCommandContinuation currentCommand = null;

	private void waitFor(String transactionId, String status) {
		Response response = null;
		while (true) {
			response = getResponse(transactionId);
			
			if (response != null && response.getAttribute("status").equals(status)) {
				break;
			}
			
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
	}

	public void run(ResponseListener listener) {
		Run command = new Run(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void run() {
		Run command = new Run(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stepInto(ResponseListener listener) {
		StepInto command = new StepInto(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}
	
	public void stepInto() {
		StepInto command = new StepInto(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stepOut(ResponseListener listener) {
		StepOut command = new StepOut(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void stepOut() {
		StepOut command = new StepOut(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stepOver(ResponseListener listener) {
		StepOver command = new StepOver(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void stepOver() {
		StepOver command = new StepOver(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stop(ResponseListener listener) {
		Stop command = new Stop(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void stop() {
		Stop command = new Stop(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public boolean setBreakpoint(Breakpoint breakpoint) {
		BreakpointSet command = new BreakpointSet(session, " -i " + getNextTransaction());
		command.setBreakpoint((BreakpointImpl) breakpoint);
		
		command.toDebuggee();
		
		Response response = getResponse(command.getTransactionId());
		
		//XXX: handle error

		breakpoint.setState("enabled".equals(response.getAttribute("state")));
		breakpoint.setId(Integer.parseInt(response.getAttribute("id")));
		
		return true;
	}

	public boolean updateBreakpoint(Breakpoint breakpoint) {
		BreakpointUpdate command = new BreakpointUpdate(session, " -i " + getNextTransaction());
		command.setBreakpoint(breakpoint);
		
		command.toDebuggee();

//		Response response = 
			getResponse(command.getTransactionId());
		
		//XXX: handle error

		return true;
	}

	public boolean removeBreakpoint(BreakpointImpl breakpoint) {
		BreakpointRemove command = new BreakpointRemove(session, " -i " + getNextTransaction());
		command.setBreakpoint(breakpoint);
		
		command.toDebuggee();

//		Response response = 
			getResponse(command.getTransactionId());
		
		//XXX: handle error

		return true;
	}

	public synchronized void setResponseCode(int code) {
		responseCode = code;
		notifyAll();
	}
}
