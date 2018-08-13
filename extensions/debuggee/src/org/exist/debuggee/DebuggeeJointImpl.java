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
package org.exist.debuggee;

import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.debuggee.dbgp.packets.AbstractCommandContinuation;
import org.exist.debuggee.dbgp.packets.Command;
import org.exist.debuggee.dbgp.packets.Init;
import org.exist.debugger.model.Breakpoint;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Expression;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeJointImpl implements DebuggeeJoint, Status {
	
    private final static Logger LOG = LogManager.getLogger(DebuggeeJoint.class);

    private Map<String, String> features = new HashMap<String, String>(DebuggeeImpl.SET_GET_FEATURES);
	
	protected Expression firstExpression = null;
	
	private List<Expression> stack = new ArrayList<Expression>();
	private int stackDepth = 0;
	
	private CommandContinuation command = null;
	private Deque<CommandContinuation> commands = new ArrayDeque<CommandContinuation>();

	private int breakpointNo = 0;
	//<fileName, Map<line, breakpoint>>
	private Map<String, Map<Integer, Breakpoint>> filesBreakpoints = 
		new HashMap<String, Map<Integer, Breakpoint>>();
	
	private Set<Breakpoint> activeBreakpoints =  new CopyOnWriteArraySet<Breakpoint>();

	//id, breakpoint
	private Map<Integer, Breakpoint> breakpoints = new HashMap<Integer, Breakpoint>();

	private CompiledXQuery compiledXQuery;

    private boolean inProlog = false;
	
	protected void setCompiledScript(CompiledXQuery compiledXQuery) {
		this.compiledXQuery = compiledXQuery;
		stack.add(null);
	}

	public void stackEnter(Expression expr) {
		if (LOG.isDebugEnabled())
			LOG.debug("" + expr.getLine() + " expr = "+ expr.toString());
		
		stack.add(expr);
		stackDepth++;
		
	}
	
	public void stackLeave(Expression expr) {
		if (LOG.isDebugEnabled())
			LOG.debug("" + expr.getLine() + " expr = "+ expr.toString());
		
		stack.remove(stack.size()-1);
		stackDepth--;
		
		if (command.is(CommandContinuation.STEP_OUT) 
				&& command.getCallStackDepth() > stackDepth
				&& command.isStatus(RUNNING)) {
			command.setStatus(BREAK);
		}
	}

    public void prologEnter(Expression expr) {
        inProlog = true;
    }

    /* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionStart(org.exist.xquery.Expression)
	 */
	public void expressionStart(Expression expr) throws TerminatedException {
		if (LOG.isDebugEnabled())
			LOG.debug("" + expr.getLine() + " expr = "+ expr.toString());
		
		if (compiledXQuery == null)
			return;
		
		if (firstExpression == null)
			firstExpression = expr;
		
		stack.set(stackDepth, expr);
		
		String fileName = Command.getFileuri(expr.getSource());
		Integer lineNo = expr.getLine();

		//check breakpoint
		for (Breakpoint breakpoint : activeBreakpoints) {
			if (breakpoint.getFilename().equals(fileName)
					&& breakpoint.getType().equals(Breakpoint.TYPE_LINE) ) {
				if (breakpoint.getLineno() != lineNo) {
					activeBreakpoints.remove(breakpoint);
				}
			}
		}
		
		Map<Integer, Breakpoint> fileBreakpoints = null;

		while (true) {
			//didn't receive any command, wait for any 
			if (command == null ||
					//the status is break, wait for changes
					command.isStatus(BREAK)) {
				waitCommand();
				continue;
			}

			//wait for connection
			if (command.is(CommandContinuation.INIT) && command.isStatus(STARTING)) {
				Init init = (Init)command;
				init.getSession().setAttribute("joint", this);
				init.setFileURI(compiledXQuery.getSource());
				
				//break on first line
				command.setStatus(BREAK);
			}
			
			//disconnected
			if (compiledXQuery == null)
				return;
			
			//stop command, terminate
			if (command.is(CommandContinuation.STOP) && !command.isStatus(STOPPED)) {
				command.setStatus(STOPPED);
				
				sessionClosed(true);
	            
				throw new TerminatedException(expr.getLine(), expr.getColumn(), "Debuggee STOP command.");
			}

			//step-into is done
			if (command.is(CommandContinuation.STEP_INTO) && command.isStatus(RUNNING)) {
				command.setStatus(BREAK);

			//step-over should stop on same call's stack depth
			} else if (command.is(CommandContinuation.STEP_OVER)  
					&& command.getCallStackDepth() == stackDepth
					&& command.isStatus(RUNNING)) {
				command.setStatus(BREAK);

			}
			
			//checking breakpoints
			synchronized (breakpoints) {
				if (filesBreakpoints.containsKey(fileName)) {
					fileBreakpoints = filesBreakpoints.get(fileName);
					
					if (fileBreakpoints.containsKey(lineNo)) {
						Breakpoint breakpoint = fileBreakpoints.get(lineNo);
						
						if (!activeBreakpoints.contains(breakpoint) 
								&& breakpoint.getState() 
								&& breakpoint.getType().equals(Breakpoint.TYPE_LINE)) {
							
							activeBreakpoints.add(breakpoint);
							command.setStatus(BREAK);
							//waitCommand();
							//break;
						}
					}
				}
			}
			
			//RUN command with status RUNNING can be break only on breakpoints
			if (command.getType() >= CommandContinuation.RUN && command.isStatus(RUNNING)) {
				break;

			//any continuation command with status RUNNING
			} else if (command.getType() >= CommandContinuation.RUN && command.isStatus(STARTING)) {
				command.setStatus(RUNNING);
				break;
			}
			
			waitCommand();
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionEnd(org.exist.xquery.Expression)
	 */
	public void expressionEnd(Expression expr) {
		if (LOG.isDebugEnabled())
			LOG.debug("expr = "+expr.toString());

		if (firstExpression == expr) {
			firstExpression = null;

            if (!inProlog) {
                command.setStatus(BREAK);
                command.setStatus(STOPPED);

                sessionClosed(true);
                
                reset();
            }
            inProlog = false;
		}
		
	}
	
	private synchronized void waitCommand() {
		if (!commands.isEmpty() && command.isStatus(BREAK)) {
			command = commands.pop();
			
			((AbstractCommandContinuation)command).setCallStackDepth(stackDepth);
			
			return;
		}
			
			
		notifyAll();
		try {
			wait();
		} catch (InterruptedException e) {
			//UNDERSTAND: what to do?
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#getContext()
	 */
	public XQueryContext getContext() {
		return compiledXQuery.getContext();
	}

	public void reset() {
		firstExpression = null;
		
		stack = new ArrayList<Expression>();
		stackDepth = 0;
		
		command = null;
		
		breakpointNo = 0;
		filesBreakpoints = new HashMap<String, Map<Integer, Breakpoint>>();
		
		breakpoints = new HashMap<Integer, Breakpoint>();
	}

	public synchronized void continuation(CommandContinuation command) {
		//System.out.println("continuation command = "+command);
		if (firstExpression == null && !command.is(CommandContinuation.INIT))
			command.setStatus(STOPPED);
		
		else
			command.setStatus(STARTING);

		if (this.command == null || this.command.isStatus(STOPPED)) {
			((AbstractCommandContinuation)command).setCallStackDepth(stackDepth);
		
			this.command = command;
		} else {
			commands.push(command);
		}

		notifyAll();
	}
	
	public boolean featureSet(String name, String value) {
		if (features.containsKey(name)) {
			features.put(name, value);
			
			return true;
		}
		
		return false;
	}

	public String featureGet(String name) {
		if (DebuggeeImpl.GET_FEATURES.containsKey(name))
			return DebuggeeImpl.GET_FEATURES.get(name);
		
		return features.get(name);
	}
	
	public synchronized List<Expression> stackGet() {
		//wait, script didn't started
//		while (stack.size() == 0 && command == null)
//			try {
//				Thread.sleep(100);
//			} catch (InterruptedException e) {
//			}
		
		return stack;
	}

	public Map<QName, Variable> getVariables() {
		if (stack.size() == 0)
			return new HashMap<QName, Variable>();
			
		Expression expr = stack.get(0);
		return expr.getContext().getVariables();
	}

	public Map<QName, Variable> getLocalVariables() {
		if (stack.size() == 0)
			return new HashMap<QName, Variable>();
			
		Expression expr = stack.get(0);
		return expr.getContext().getLocalVariables();
	}
	
	public Map<QName, Variable> getGlobalVariables() {
		if (stack.size() == 0)
			return new HashMap<QName, Variable>();
			
		Expression expr = stack.get(0);
		return expr.getContext().getGlobalVariables();
	}

	public Variable getVariable(String name) {
		if (name == null) 
			return null;
		
		if (stack.size() == 0)
			return null;
			
		Expression expr = stack.get(0);
		try {
			if (name.startsWith("$"))
				return expr.getContext().resolveVariable(name.substring(1));
			
			return expr.getContext().resolveVariable(name);
		} catch (XPathException e) {
			return null;
		}
	}

	public int setBreakpoint(Breakpoint breakpoint) {
		synchronized (breakpoints) {
			breakpointNo++;
			breakpoint.setId(breakpointNo);
			
			breakpoints.put(breakpointNo, breakpoint);

			Map<Integer, Breakpoint> fileBreakpoints;
			String fileName = breakpoint.getFilename();

			if (filesBreakpoints.containsKey(fileName))
				fileBreakpoints = filesBreakpoints.get(fileName);
			else {
				fileBreakpoints = new HashMap<Integer, Breakpoint>();
				filesBreakpoints.put(fileName, fileBreakpoints);
			}
			fileBreakpoints.put(breakpoint.getLineno(), breakpoint);
		}

		return 1;//TODO: create constant
	}

	public Breakpoint getBreakpoint(int breakpointID) {
		return breakpoints.get(breakpointID);
	}

	public Breakpoint removeBreakpoint(int breakpointID) {
		Breakpoint breakpoint = breakpoints.get(breakpointID);
		if (breakpoint == null)
			return breakpoint;
		
		String fileName = breakpoint.getFilename();
		Integer lineNo = breakpoint.getLineno();

		Map<Integer, Breakpoint> fileBreakpoints = null;

		synchronized (breakpoints) {
			if (filesBreakpoints.containsKey(fileName)) {
				fileBreakpoints = filesBreakpoints.get(fileName);
				
				if (fileBreakpoints.containsKey(lineNo)) {
					fileBreakpoints.remove(lineNo);
					
					if (fileBreakpoints.isEmpty()) {
						filesBreakpoints.remove(fileName);
					}
				}
			}
			
			breakpoints.remove(breakpointID);
			
			activeBreakpoints.remove(breakpoint);
		}
		
		return breakpoint;
	}

	public Map<Integer, Breakpoint> getBreakpoints() {
		return breakpoints;
	}

	public synchronized void sessionClosed(boolean disconnect) {
		//disconnected already
		if (compiledXQuery == null)
			return;
		
		//disconnect debuggee & compiled source
		XQueryContext context = compiledXQuery.getContext();
		context.setDebuggeeJoint(null);
		compiledXQuery = null;
		
		if (command != null && disconnect)
			command.disconnect();
		
		reset();
		
		notifyAll();
	}

	public CommandContinuation getCurrentCommand() {
		return command;
	}

	@Override
	public String evalution(String script) throws Exception {
		
		Database db = compiledXQuery.getContext().getDatabase();

		//TODO: account required
		try(final DBBroker broker = db.getBroker()) {
			XQueryContext context = compiledXQuery.getContext().copyContext();
			context.setDebuggeeJoint(null);
			context.undeclareGlobalVariable(Debuggee.SESSION);
			context.undeclareGlobalVariable(Debuggee.IDEKEY);
			
			XQuery service = broker.getBrokerPool().getXQueryService();
			CompiledXQuery compiled = service.compile(broker, context, script);
			
			Sequence resultSequence = service.execute(broker, compiled, null);
	
	        SAXSerializer sax = null;
	        Serializer serializer = broker.getSerializer();
			serializer.reset();
	        try {
	            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
	            Properties outputProps = new Properties();
	            StringWriter writer = new StringWriter();
	            sax.setOutput(writer, outputProps);
	            serializer.setSAXHandlers(sax, sax);
	            for (SequenceIterator i = resultSequence.iterate(); i.hasNext();) {
	                Item next = i.nextItem();
	                if (Type.subTypeOf(next.getType(), Type.NODE))
	                    serializer.toSAX((NodeValue) next);
	                else
	                    writer.write(next.getStringValue());
	            }
	            return writer.toString();
	        } finally {
	            if (sax != null) {
	                SerializerPool.getInstance().returnObject(sax);
	            }
	        }
		}
	}
}
