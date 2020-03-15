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
package org.exist.debuggee.dbgp.packets;

import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.exist.debugger.model.Breakpoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BreakpointList extends Command {

	private Map<Integer, Breakpoint> breakpoints;
	
	public BreakpointList(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		super.setArgument(arg, val);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		breakpoints = getJoint().getBreakpoints();
	}

	public byte[] responseBytes() {
		if (breakpoints != null) {
			StringBuilder responce = new StringBuilder();
			responce.append(xml_declaration);
			responce.append("<response " +
					namespaces +
					"command=\"breakpoint_list\" transaction_id=\"");
			responce.append(transactionID);
			responce.append("\">");
			for (Breakpoint breakpoint : breakpoints.values()) 
				responce.append(breakpoint.toXMLString());
				
			responce.append("</response>");

			return responce.toString().getBytes();
		}
		return errorBytes("breakpoint_list");
	}
	
	public byte[] commandBytes() {
		String command = "breakpoint_list -i "+transactionID;
		return command.getBytes();
	}
}
