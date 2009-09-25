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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.dgbp.packets.Stop;
import org.exist.debugger.model.Breakpoint;
import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeJointImpl implements DebuggeeJoint, Status {
	
	private Expression firstExpression = null;
	
	private List<Expression> stack = new ArrayList<Expression>();
	private int stackDepth = 1;
	
	private CommandContinuation command = null;
	
	private int breakpointNo = 0;
	//<fileName, Map<line, breakpoint>>
	private Map<String, Map<Integer, Breakpoint>> filesBreakpoints = 
		new HashMap<String, Map<Integer, Breakpoint>>();
	
	//id, breakpoint
	private Map<Integer, Breakpoint> breakpoints = new HashMap<Integer, Breakpoint>();

	public DebuggeeJointImpl() {
	}
	
	public void stackEnter(Expression expr) {
		stack.add(expr);
		
	}
	
	public void stackLeave(Expression expr) {
		stack.remove(stack.size()-1);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionStart(org.exist.xquery.Expression)
	 */
	public void expressionStart(Expression expr) throws TerminatedException {
		System.out.println("expressionStart expr = "+expr.toString());
		
		if (firstExpression == null)
			firstExpression = expr;
		
		if (stack.size() == stackDepth)
			stack.set(stackDepth-1, expr);
		else
			stack.add(expr);
		
		String fileName = expr.getSource().getKey();

		Map<Integer, Breakpoint> fileBreakpoints = null;
		Integer lineNo = expr.getLine();

		while (true) {
			//didn't receive any command, wait for any 
			if (command == null) {
				waitCommand();
				continue;
			}
			
			//the status is break, wait for changes
			if (command.isStatus(BREAK)) {
				waitCommand();
				continue;
			}

			//stop command, terminate
			if (command.is(command.STOP) && !command.isStatus(STOPPED)) {
				command.setStatus(STOPPED);
	            throw new TerminatedException(expr.getLine(), expr.getColumn(), "Debuggee STOP command.");
			}
				

			//checking breakpoints
			if (filesBreakpoints.containsKey(fileName)) {
				fileBreakpoints = filesBreakpoints.get(fileName);
				
				if (fileBreakpoints.containsKey(lineNo)) {
					Breakpoint breakpoint = fileBreakpoints.get(lineNo);
					
					if (breakpoint.getState() && breakpoint.getType().equals(breakpoint.TYPE_LINE)) {
						command.setStatus(BREAK);
					}
				}
			}
			
			//break on first line
			if (command.is(CommandContinuation.STEP_INTO) && command.isStatus(FIRST_RUN))
				;

			//step-into is done
			else if (command.is(CommandContinuation.STEP_INTO) && command.isStatus(RUNNING))
				command.setStatus(BREAK);

			//RUS command with status RUNNING can be break only on breakpoints
			else if (command.is(CommandContinuation.RUN) && command.isStatus(RUNNING))
				break;

			//any continuation command with status RUNNING
			else if (command.getType() >= CommandContinuation.RUN && command.isStatus(STARTING)) {
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
		System.out.println("expressionEnd expr = "+expr.toString());

		if (firstExpression == expr) {
			firstExpression = null;
			command.setStatus(STOPPED);
		}
		
	}
	
	private synchronized void waitCommand() {
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
		// TODO Auto-generated method stub
		return null;
	}

	public void reset() {
		// TODO Auto-generated method stub
		
	}

	public synchronized void continuation(CommandContinuation command) {
		if (this.command == null)
			command.setStatus(FIRST_RUN);
		else
			command.setStatus(STARTING);

		if (firstExpression == null)
			command.setStatus(STOPPED);
		else
			command.setStatus(STARTING);
		this.command = command;

		notifyAll();
	}
	
	public boolean featureSet(String name, String value) {
		// TODO Auto-generated method stub
		return false;
	}

	public List<Expression> stackGet() {
		return stack;
	}

	public Map<QName, Variable> getVariables() {
		if (stack.size() == 0)
			return new HashMap<QName, Variable>();
			
		Expression expr = stack.get(0);
		return expr.getContext().getVariables();
	}

	public Variable getVariable(String name) {
		if (stack.size() == 0)
			return null;
			
		Expression expr = stack.get(0);
		try {
			return expr.getContext().resolveVariable(name);
		} catch (XPathException e) {
			return null;
		}
	}

	public int setBreakpoint(Breakpoint breakpoint) {
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

		return 1;//TODO: do throw constant
	}

	public Breakpoint getBreakpoint(int breakpointID) {
		return breakpoints.get(breakpointID);
	}
}
