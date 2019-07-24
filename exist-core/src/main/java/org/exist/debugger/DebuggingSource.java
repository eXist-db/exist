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
import java.util.List;

import org.exist.debugger.model.Breakpoint;
import org.exist.debugger.model.Location;
import org.exist.debugger.model.Variable;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public interface DebuggingSource {

	public Debugger getDebugger();

	/**
	 * Starts or resumes the script until a new breakpoint is reached, or the end of the script is reached.
	 *
	 * @param listener the response listener
	 */
	public void run(ResponseListener listener);

	/**
	 * Steps to the next statement, if there is a function call involved it will break on the first statement in that function.
	 *
	 * @param listener the response listener
	 */
	public void stepInto(ResponseListener listener);

	/**
	 * Steps to the next statement, if there is a function call on the line from which the step_over is issued then the debugger engine will stop at the statement after the function call in the same scope as from where the command was issued.
	 *
	 * @param listener the response listener
	 */
	public void stepOver(ResponseListener listener);

	/**
	 * Steps out of the current scope and breaks on the statement after returning from the current function. (Also called 'finish' in GDB)
	 *
	 * @param listener the response listener
	 */
	public void stepOut(ResponseListener listener);

	/**
	 * Ends execution of the script immediately, the debugger engine may not respond, though if possible should be designed to do so. The script will be terminated right away and be followed by a disconnection of the network connection from the IDE (and debugger engine if required in multi request apache processes).
	 *
	 * @param listener the response listener
	 */
	public void stop(ResponseListener listener);


	public void run() throws IOException;
	public void stepInto() throws IOException;
	public void stepOver() throws IOException;
	public void stepOut() throws IOException;
	public void stop() throws IOException;

	/**
	 * Stops interaction with the debugger engine. Once this command is executed, the IDE will no longer be able to communicate with the debugger engine. This does not end execution of the script as does the stop command above, but rather detaches from debugging. Support of this continuation command is optional, and the IDE should verify support for it via the feature_get command. If the IDE has created stdin/stdout/stderr pipes for execution of the script (eg. an interactive shell or other console to catch script output), it should keep those open and usable by the process until the process has terminated normally.
	 */
	public void detach();

	public boolean isSuspended();
	public boolean isTerminated();

	public List<Variable> getVariables() throws IOException;
	public List<Variable> getLocalVariables() throws IOException;
	public List<Variable> getGlobalVariables() throws IOException;

	public List<Location> getStackFrames() throws IOException;

	public Breakpoint newBreakpoint();

	public String getText();

	public String evaluate(String script) throws IOException;
}
