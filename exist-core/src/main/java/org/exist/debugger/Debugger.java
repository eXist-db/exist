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

import org.exist.debugger.model.Location;
import org.exist.debugger.model.Variable;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Debugger {

	public DebuggingSource init(String url) throws IOException, ExceptionTimeout;

	public DebuggingSource getSource(String fileURI) throws IOException;

	public void sessionClosed();

//	public boolean setBreakpoint(Breakpoint breakpoint);

	public void run(ResponseListener listener);
	public void run() throws IOException;

	public void stepInto(ResponseListener listener);
	public void stepInto() throws IOException;
	
	public void stepOut(ResponseListener listener);
	public void stepOut() throws IOException;
	
	public void stepOver(ResponseListener listener);
	public void stepOver() throws IOException;

	public void stop(ResponseListener listener);
	public void stop() throws IOException;

	//public Response getResponse(String transactionID);

	public List<Variable> getVariables() throws IOException;
	public List<Variable> getLocalVariables() throws IOException;
	public List<Variable> getGlobalVariables() throws IOException;

	public List<Location> getStackFrames() throws IOException;

	public String evaluate(String script) throws IOException;

	
	public boolean isSuspended();
	
	public boolean isTerminated();
}
