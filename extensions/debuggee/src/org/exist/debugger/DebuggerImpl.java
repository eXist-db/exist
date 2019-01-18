/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import static org.exist.util.ThreadUtils.newGlobalThread;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class DebuggerImpl implements Debugger, org.exist.debuggee.Status {

	protected final static Logger LOG = LogManager.getLogger(DebuggerImpl.class);

	private static DebuggerImpl instance = null;
	
	public static Debugger getDebugger() throws IOException {
		if (instance == null)
			instance = new DebuggerImpl();
		
		return instance;
	}

	public static void shutdownDebugger() {
		if (instance == null)
			return;
		
		instance.acceptor.unbind();
		instance = null;
	}

	private NioSocketAcceptor acceptor;

	private int eventPort = 9000;

	private IoSession session;

	// uri -> source
	private Map<String, DebuggingSource> sources = new HashMap<String, DebuggingSource>();

	int currentTransactionId = 1;
	
//	private String lastStatus = FIRST_RUN;
	
	protected int responseCode = 0;
	
	private DebuggerImpl() throws IOException {
		acceptor = new NioSocketAcceptor();
		acceptor.setCloseOnDeactivation(true);
		acceptor.getFilterChain().addLast("protocol", new ProtocolCodecFilter(new CodecFactory()));
		acceptor.setHandler(new ProtocolHandler(this));
		acceptor.bind(new InetSocketAddress(eventPort));
	}

	private int getNextTransaction() {
		return currentTransactionId++;
	}

	private void setSession(IoSession session) {
		this.session = session;
	}

	public DebuggingSource init(String url) throws IOException,
			ExceptionTimeout {
		LOG.info("Debugger is listening at port " + eventPort);

		if (this.session != null) new IOException("Another debugging session is active.");

		responseCode = 0;
		responses = new HashMap<String, Response>();
		sources = new HashMap<String, DebuggingSource>();
		currentTransactionId = 1;
		
		Thread session = newGlobalThread("debuggerHttpSession", new HttpSession(this, url));
		session.start();

		// 30s timeout
		ResponseImpl response = (ResponseImpl) getResponse("init", 30 * 1000); 
		setSession(response.getSession());

		// TODO: fileuri as constant???
		return getSource(response.getAttribute("fileuri"));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.debugger.Debugger#source(java.lang.String)
	 */
	public DebuggingSource getSource(String fileURI) throws IOException {
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
	
	public List<Variable> getVariables() throws IOException {
		return getVariables(null);
	}

	public List<Variable> getLocalVariables() throws IOException {
		return getVariables(ContextNames.LOCAL);
	}

	public List<Variable> getGlobalVariables() throws IOException {
		return getVariables(ContextNames.GLOBAL);
	}

	private List<Variable> getVariables(String contextID) throws IOException {
		
		ContextGet command = new ContextGet(session, 
				" -i " + getNextTransaction()); 
		if (contextID != null)
			command.setContextID(contextID);
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
	
	public List<Location> getStackFrames() throws IOException {
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
		if (session != null && !session.isClosing())
			session.close(true);
		
		session = null;
	}

	// weak map???
	private Map<String, Response> responses = new HashMap<String, Response>();

	public synchronized void addResponse(Response response) {
		if (currentCommand != null
				&& currentCommand.getTransactionId().equals(
						response.getTransactionID()))
			currentCommand.putResponse(response);

		//it should be commands map, this implementation is dangerous
		//rethink!!!
		responses.put(response.getTransactionID(), response);

		notifyAll();
	}

	public Response getResponse(String transactionID) throws IOException {
		try {
			return getResponse(transactionID, 0);
		} catch (ExceptionTimeout e) {
			return null;
		}
	}

	public synchronized Response getResponse(String transactionID, int timeout)
			throws ExceptionTimeout, IOException {
		long sTime = System.currentTimeMillis();

		while (!responses.containsKey(transactionID)) {
			try {
				if (responseCode != 0) {
					if (responses.containsKey(transactionID))
						break;
					throw new IOException("Got responce code "+responseCode+" on debugging request");
				}
				
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

	private void waitFor(String transactionId, String status) throws IOException {
		Response response = null;
		while (true) {
			response = getResponse(transactionId);
			
			if (response != null) {
				if (response.getElemetsByName("error").getLength() != 0)
					break;

				String getStatus = response.getAttribute("status");
				
				if (getStatus.equals(status)) {
					break;
				} else if (getStatus.equals(STOPPED)) {
					break;
				}
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

	public void run() throws IOException {
		Run command = new Run(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stepInto(ResponseListener listener) {
		StepInto command = new StepInto(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}
	
	public void stepInto() throws IOException {
		StepInto command = new StepInto(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stepOut(ResponseListener listener) {
		StepOut command = new StepOut(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void stepOut() throws IOException {
		StepOut command = new StepOut(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stepOver(ResponseListener listener) {
		StepOver command = new StepOver(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void stepOver() throws IOException {
		StepOver command = new StepOver(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		waitFor(command.getTransactionId(), BREAK);
	}

	public void stop(ResponseListener listener) {
		Stop command = new Stop(session, " -i " + getNextTransaction());
		command.addResponseListener(listener);

		command.toDebuggee();
	}

	public void stop() throws IOException {
		Stop command = new Stop(session, " -i " + getNextTransaction());

		command.toDebuggee();
		
		try {
			waitFor(command.getTransactionId(), BREAK);
		} catch (IOException e) {
			//closed?
		}
	}

	public boolean setBreakpoint(Breakpoint breakpoint) throws IOException {
		BreakpointSet command = new BreakpointSet(session, " -i " + getNextTransaction());
		command.setBreakpoint((BreakpointImpl) breakpoint);
		
		command.toDebuggee();
		
		Response response = getResponse(command.getTransactionId());
		
		//XXX: handle error

		breakpoint.setState("enabled".equals(response.getAttribute("state")));
		breakpoint.setId(Integer.parseInt(response.getAttribute("id")));
		
		return true;
	}

	public boolean updateBreakpoint(Breakpoint breakpoint) throws IOException {
		BreakpointUpdate command = new BreakpointUpdate(session, " -i " + getNextTransaction());
		command.setBreakpoint(breakpoint);
		
		command.toDebuggee();

//		Response response = 
			getResponse(command.getTransactionId());
		
		//XXX: handle error

		return true;
	}

	public boolean removeBreakpoint(BreakpointImpl breakpoint) throws IOException {
		BreakpointRemove command = new BreakpointRemove(session, " -i " + getNextTransaction());
		command.setBreakpoint(breakpoint);
		
		command.toDebuggee();

//		Response response = 
			getResponse(command.getTransactionId());
		
		//XXX: handle error

		return true;
	}

	protected synchronized void terminate(String url, int code) {
		responseCode = code;
		notifyAll();
		
		System.out.println("setResponseCode responseCode = "+responseCode);
	}

	private String getText(NodeList nodes) {
		if ((nodes.getLength() == 1) && (nodes.item(0).getNodeType() == Node.TEXT_NODE))
			return ((Text)nodes.item(0)).getData();
		
		return "";
	}
	
	@Override
	public String evaluate(String script) throws IOException {
		Eval command = new Eval(session, " -i " + getNextTransaction());
		command.setScript(script);
		command.toDebuggee();

		Response response = getResponse(command.getTransactionId());

		if ("1".equals(response.getAttribute("success"))) {
			Node property = response.getElemetsByName("property").item(0);
			Base64Decoder dec = new Base64Decoder();
			dec.translate(getText(property.getChildNodes()));
			byte[] c = dec.getByteArray();

			return new String(c);
		}

		return null;
	}

	public boolean isSuspended() {
		if (currentCommand == null) return false;
		if (currentCommand.getStatus() == null) return false;
		return (currentCommand.getStatus().equals(BREAK));
	}

	public boolean isTerminated() {
		if (currentCommand == null) return false;
		if (currentCommand.getStatus() == null) return false;
		return (currentCommand.equals(STOPPED));
	}
}
