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
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.debugger.model.Breakpoint;
import org.exist.debugger.model.BreakpointImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BreakpointRemove extends Command {

	/**
	 * is the unique session breakpoint id returned by breakpoint_set.
	 */
	private Integer breakpointID;
	
	private Breakpoint breakpoint;
	
	public BreakpointRemove(IoSession session, String args) {
		super(session, args);
	}

	protected void init() {
		breakpointID = null;
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d")) {
			breakpointID = Integer.valueOf(val);
			
		} else {
			super.setArgument(arg, val);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		if (breakpointID != null)
			breakpoint = getJoint().removeBreakpoint(breakpointID);
	}

	public byte[] responseBytes() {
		if (breakpoint != null) {
			String responce = xml_declaration + 
			"<response " +
				namespaces +
				"command=\"breakpoint_remove\" " +
				"transaction_id=\""+transactionID+"\"/>"; 

			return responce.getBytes();
		}
		return errorBytes("breakpoint_remove");
	}
	
	public byte[] commandBytes() {
		if (breakpoint != null) {
			String command = "breakpoint_remove -i "+transactionID+" -d "+breakpoint.getId();

			return command.getBytes();
		}
		return null;
	}

	public void setBreakpoint(BreakpointImpl breakpoint) {
		this.breakpoint= breakpoint;
	}
}
