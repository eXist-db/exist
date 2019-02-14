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

import org.exist.debugger.model.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggingSourceImpl implements DebuggingSource {

	private Debugger debugger;
	
	private String fileURI;
	
//	private Map<Integer, Breakpoint> breakpoints = new HashMap<Integer, Breakpoint>();
	
	protected DebuggingSourceImpl(Debugger debugger, String fileURI) {
		this.debugger = debugger;
		
		this.fileURI = fileURI;
	}
	
	public Debugger getDebugger() {
		return debugger;
	}

	public Breakpoint newBreakpoint() {
		BreakpointImpl breakpoint = new BreakpointImpl();
		breakpoint.setFilename(fileURI);
		breakpoint.setDebuggingSource(this);
		
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
	public List<Location> getStackFrames() throws IOException {
		return debugger.getStackFrames();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#getVariables()
	 */
	public List<Variable> getVariables() throws IOException {
		return debugger.getVariables();
	}

	public List<Variable> getLocalVariables() throws IOException {
		return debugger.getLocalVariables();
	}

	public List<Variable> getGlobalVariables() throws IOException {
		return debugger.getGlobalVariables();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#isSuspended()
	 */
	public boolean isSuspended() {
		return debugger.isSuspended();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#isTerminated()
	 */
	public boolean isTerminated() {
		return debugger.isTerminated();
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
	public void run(ResponseListener listener) {
		debugger.run(listener);
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#run()
	 */
	public void run() throws IOException {
		debugger.run();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepInto()
	 */
	public void stepInto(ResponseListener listener) {
		debugger.stepInto(listener);
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepInto()
	 */
	public void stepInto() throws IOException {
		debugger.stepInto();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepOut()
	 */
	public void stepOut(ResponseListener listener) {
		debugger.stepOut(listener);
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepOut()
	 */
	public void stepOut() throws IOException {
		debugger.stepOut();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepOver()
	 */
	public void stepOver(ResponseListener listener) {
		debugger.stepOver(listener);
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stepOver()
	 */
	public void stepOver() throws IOException {
		debugger.stepOver();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stop()
	 */
	public void stop(ResponseListener listener) {
		debugger.stop(listener);
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.DebuggingSource#stop()
	 */
	public void stop() throws IOException {
		debugger.stop();
	}

	private String code = null;

	public String getText() {
		return code;
	}

	public void setText(String text) {
		code = text;
	}

	@Override
	public String evaluate(String script) throws IOException {
		return debugger.evaluate(script);
	}

}
