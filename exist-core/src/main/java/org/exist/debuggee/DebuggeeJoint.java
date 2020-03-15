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

import java.util.List;
import java.util.Map;

import org.exist.debugger.model.Breakpoint;
import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.Variable;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
//TODO: rename DebuggeeRuntime ?
public interface DebuggeeJoint {
	
	public XQueryContext getContext();

	public void expressionStart(Expression expr) throws TerminatedException;
	public void expressionEnd(Expression expr);

	public void stackEnter(Expression expr) throws TerminatedException;
	public void stackLeave(Expression expr);

    public void prologEnter(Expression expr);
    
	public void reset();

	public boolean featureSet(String name, String value);
	public String featureGet(String name);

	public void continuation(CommandContinuation command);
	public CommandContinuation getCurrentCommand();

//	public String run();
//
//	public String stepInto();
//	public String stepOut();
//	public String stepOver();
//
//	public String stop();

	public List<Expression> stackGet();
	
	public Map<QName, Variable> getVariables();
	public Map<QName, Variable> getLocalVariables();
	public Map<QName, Variable> getGlobalVariables();
	public Variable getVariable(String name);
	
	//breakpoints methods
	public int setBreakpoint(Breakpoint breakpoint);
	
	public Breakpoint getBreakpoint(int breakpointID);
	public Map<Integer, Breakpoint> getBreakpoints();

	public Breakpoint removeBreakpoint(int breakpointID);
	
	public void sessionClosed(boolean disconnect);

	public String evalution(String script) throws Exception;
}
