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

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BreakpointUpdate extends Command {

	/**
	 * is the unique session breakpoint id returned by breakpoint_set.
	 */
	private Integer breakpointID;
	
	/**
	 * breakpoint state [optional, defaults to "enabled"]
	 */
	private Boolean state;
	
	/**
	 * the line number (lineno) of the breakpoint [optional]
	 */
	private Integer lineNo;

	/**
	 * hit value (hit_value) used with the hit condition to determine if should break; a value of zero indicates hit count processing is disabled for this breakpoint [optional, defaults to zero (i.e. disabled)]
	 */
	private Integer hitValue;
	
	/**
	 * hit condition string (hit_condition); see HIT_CONDITION hit_condition documentation above; BTW 'o' stands for 'operator' [optional, defaults to '>=']
	 */
	private String hitCondition;

	private Breakpoint breakpoint;
	
	public BreakpointUpdate(IoSession session, String args) {
		super(session, args);
	}

	protected void init() {
		breakpointID = null;
		state = null;
		lineNo = null;
		hitValue = null;
		hitCondition = null;
		breakpoint = null;
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d")) {
			breakpointID = Integer.valueOf(val);
			
		} else if (arg.equals("s")) {
			state = true; //TODO: parsing required

		} else if (arg.equals("n")) {
			lineNo = Integer.parseInt(val);
			
		} else if (arg.equals("h")) {
			hitValue = Integer.parseInt(val);
			
		} else if (arg.equals("o")) {
			hitCondition = val;
			
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
			breakpoint = getJoint().getBreakpoint(breakpointID);
		
		if (breakpoint == null)
			return;
		
		if (state != null)
			breakpoint.setState(state);
		
		if (lineNo != null)
			breakpoint.setLineno(lineNo);
		
		if (hitValue != null)
			breakpoint.setHitValue(hitValue);
		
		if (hitCondition != null)
			breakpoint.setHitCondition(hitCondition);

	}

	public byte[] responseBytes() {
		if (breakpoint != null) {
			String responce = xml_declaration + 
			"<response " +
				namespaces +
				"command=\"breakpoint_update\" " +
				"transaction_id=\""+transactionID+"\"/>"; 

			return responce.getBytes();
		}
		return errorBytes("breakpoint_update");
	}

	public byte[] commandBytes() {
		if (breakpoint != null) {
			String command = "breakpoint_update" +
					" -i " + transactionID +
//					" -t " + getType() +
//					" -s " + getStateString() + 
//					" -f " + getFilename() + 
					" -h " + breakpoint.getHitValue() + 
					" -o " + breakpoint.getHitCondition();
//					" -r " + getTemporaryString(); 

			if (breakpoint.getLineno() != null)
				command += " -s " + breakpoint.getLineno(); 
					
//			if (getFunction() != null)
//				responce += " -m " + getFunction(); 
//
//			if (getException() != null)
//				responce += " -x " + getException(); 

			//TODO: EXPRESSION

			return command.getBytes();
		}
		return null;
	}

	public void setBreakpoint(Breakpoint breakpoint) {
		this.breakpoint = breakpoint; 
	}
}
