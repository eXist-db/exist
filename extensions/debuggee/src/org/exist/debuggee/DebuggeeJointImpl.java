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

import org.exist.debugger.model.Breakpoint;
import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.PathExpr;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeJointImpl implements DebuggeeJoint, Commands, Status {
	
	private List<Expression> stack = new ArrayList<Expression>();
	private int stackDepth = 1;
	
	private int command = STOP_ON_FIRST_LINE;
	private String status = "";
	
	private int breakpointNo = 0;
	//<fileName, Map<line, breakpoint>>
	private Map<String, Map<Integer, Breakpoint>> breakpoints = 
		new HashMap<String, Map<Integer, Breakpoint>>();
	
	
	
	public DebuggeeJointImpl() {
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionEnd(org.exist.xquery.Expression)
	 */
	public void expressionEnd(Expression expr) {
		System.out.println("expressionEnd expr = "+expr.toString());
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionStart(org.exist.xquery.Expression)
	 */
	public void expressionStart(Expression expr) {
		System.out.println("expressionStart expr = "+expr.toString());
		
		if (stack.size() == stackDepth)
			stack.set(stackDepth-1, expr);
		else
			stack.add(expr);
		
		while (true) {
			if (command == STEP_INTO && status == FIRST_RUN)
				waitCommand();
			
			if (command == STEP_INTO) {
				status = STARTING;
				command = WAIT;
				break;
			}
			
			waitCommand();
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

	public synchronized String run() {
		command = RUN;
		status = STARTING;

		notifyAll();
		
		return "starting";
	}
	
	public synchronized String stepInto() {
		command = STEP_INTO;
		if (status == "")
			status = FIRST_RUN;
		else
			status = STARTING;

		notifyAll();

		return status;
	}

	public synchronized String stepOut() {
		command = STEP_OUT;
		status = STARTING;

		notifyAll();

		return status;
	}

	public synchronized String stepOver() {
		command = STEP_OVER;
		status = STARTING;

		notifyAll();

		return status;
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

		Map<Integer, Breakpoint> fileBreakpoints;
		String fileName = breakpoint.getFilename();
		if (breakpoints.containsKey(fileName))
			fileBreakpoints = breakpoints.get(fileName);
		else {
			fileBreakpoints = new HashMap<Integer, Breakpoint>();
			breakpoints.put(fileName, fileBreakpoints);
		}
		fileBreakpoints.put(breakpoint.getLineno(), breakpoint);

		return 1;//TODO: do throw constant
	}
}
