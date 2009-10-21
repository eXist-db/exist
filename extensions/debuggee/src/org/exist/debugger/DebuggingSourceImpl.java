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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.exist.debuggee.dbgp.packets.BreakpointSet;
import org.exist.debugger.model.Breakpoint;
import org.exist.debugger.model.BreakpointImpl;
import org.exist.debugger.model.Location;
import org.exist.debugger.model.Variable;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggingSourceImpl implements DebuggingSource {

	private Debugger debugger;
	
	private String fileURI;
	
	private Map<Integer, Breakpoint> breakpoints = new HashMap<Integer, Breakpoint>();
	
	protected DebuggingSourceImpl(Debugger debugger, String fileURI) {
		this.debugger = debugger;
		
		this.fileURI = fileURI;
	}
	
	public Breakpoint getBreakpoint() {
		BreakpointImpl breakpoint = new BreakpointImpl();
		breakpoint.setFilename(fileURI);
		breakpoint.setDebuggingSource(debugger);
		
		return breakpoint;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#detach()
	 */
	public void detach() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#getStackFrames()
	 */
	public Location[] getStackFrames() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#getVariables()
	 */
	public Variable[] getVariables() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#isSuspended()
	 */
	public boolean isSuspended() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#isTerminated()
	 */
	public boolean isTerminated() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#removeBreakpoint(org.exist.debugger.model.Breakpoint)
	 */
	public void removeBreakpoint(Breakpoint breakpoint) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#removeBreakpoints()
	 */
	public void removeBreakpoints() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#run()
	 */
	public void run() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepInto()
	 */
	public void stepInto() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepOut()
	 */
	public void stepOut() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepOver()
	 */
	public void stepOver() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stop()
	 */
	public void stop() {
		// TODO Auto-generated method stub

	}

	public Breakpoint newBreakpoint() {
		// TODO Auto-generated method stub
		return null;
	}
	
	private String code = null;

	public String getText() {
		return code;
	}

	public void setText(String text) {
		code = text;
	}

}
