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
import java.util.List;

import org.exist.xquery.Expression;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeJointImpl implements DebuggeeJoint, Commands, Status {
	
	private String status = FIRST_RUN;
	
	private List<Expression> stack = new ArrayList<Expression>();
	private int stackDepth = 1;
	
	private int command = STOP_ON_FIRST_LINE;
	
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
		
		if (expr instanceof PathExpr) {
			PathExpr pathExpr = (PathExpr) expr;
			if (stack.size() == stackDepth)
				stack.set(stackDepth-1, pathExpr);
			else
				stack.add(pathExpr);
		}
		
		while (true) {
			if (command == STOP_ON_FIRST_LINE && status == FIRST_RUN)
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
		System.out.println("DebuggeeJoint.waitCommand notifyAll thread = "+Thread.currentThread());
		notifyAll();
		try {
			System.out.println("DebuggeeJoint.waitCommand wait thread = "+Thread.currentThread());
			wait();
			System.out.println("DebuggeeJoint.waitCommand wait passed thread = "+Thread.currentThread());
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
	
	public synchronized String stepInto() {
		command = STEP_INTO;

		System.out.println("DebuggeeJoint.stepInto notifyAll thread = "+Thread.currentThread());
		notifyAll();
		
		try {
			System.out.println("DebuggeeJoint.stepInto wait thread = "+Thread.currentThread());
			wait();
			System.out.println("DebuggeeJoint.stepInto wait passed thread = "+Thread.currentThread());
		} catch (InterruptedException e) {
			return "error";
		}
		
		return status;
	}

	public String stepOut() {
		// TODO Auto-generated method stub
		return null;
	}

	public String stepOver() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean featureSet(String name, String value) {
		// TODO Auto-generated method stub
		return false;
	}

	public List<Expression> stackGet() {
		return stack;
	}
}
