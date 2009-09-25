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
package org.exist.debuggee.dgbp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.debugger.model.Breakpoint;
import org.exist.debugger.model.BreakpointImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BreakpointSet extends Command implements Breakpoint {

	BreakpointImpl breakpoint = new BreakpointImpl();
	
	private int status = -1;

	public BreakpointSet(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("t")) {
			breakpoint.setType(val);
			
		} else if (arg.equals("s")) {
			breakpoint.setState(true); //TODO: parsing required
			
		} else if (arg.equals("f")) {
			breakpoint.setFilename(val);
			
		} else if (arg.equals("n")) {
			breakpoint.setLineno(Integer.parseInt(val));
			
		} else if (arg.equals("m")) {
			breakpoint.setFunction(val);
			
		} else if (arg.equals("x")) {
			breakpoint.setException(val);
			
		} else if (arg.equals("h")) {
			breakpoint.setHitValue(Integer.parseInt(val));
			
		} else if (arg.equals("o")) {
			breakpoint.setHitCondition(val);
			
		} else if (arg.equals("r")) {
			breakpoint.setTemporary(true); //TODO: parsing required
			
		} else {
			super.setArgument(arg, val);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		status = joint.setBreakpoint(this);

	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#toBytes()
	 */
	@Override
	public byte[] toBytes() {
		if (status == 1) {
			String responce = "<response " +
				"command=\"breakpoint_set\" " +
				"state=\""+getStateString()+"\" " +
				"id=\""+String.valueOf(getId())+"\" " +
				"transaction_id=\""+transactionID+"\"/>";

			return responce.getBytes();
		}
		//error
		String responce = "<error/>";
		return responce.getBytes();
	}
	
	private String getStateString() {
		if (breakpoint.getState())
			return "enabled";
		
		return "disabled";
	}
	
	///////////////////////////////////////////////////////////////////
	// Breakpoint's methods
	///////////////////////////////////////////////////////////////////

	public String getException() {
		return breakpoint.getException();
	}

	public String getFilename() {
		return breakpoint.getFilename();
	}

	public String getFunction() {
		return breakpoint.getFunction();
	}

	public String getHitCondition() {
		return breakpoint.getHitCondition();
	}

	public int getHitCount() {
		return breakpoint.getHitCount();
	}

	public int getHitValue() {
		return breakpoint.getHitValue();
	}

	public int getLineno() {
		return breakpoint.getLineno();
	}

	public boolean getState() {
		return breakpoint.getState();
	}

	public boolean getTemporary() {
		return breakpoint.getTemporary();
	}

	public void setException(String exception) {
		breakpoint.setException(exception);
	}

	public void setFilename(String filename) {
		breakpoint.setFilename(filename);
	}

	public void setFunction(String function) {
		breakpoint.setFunction(function);
	}

	public void setHitCondition(String condition) {
		breakpoint.setHitCondition(condition);
	}

	public void setHitCount(int count) {
		breakpoint.setHitCount(count);
	}

	public void setHitValue(int value) {
		breakpoint.setHitValue(value);
	}

	public void setLineno(int lineno) {
		breakpoint.setLineno(lineno);
	}

	public void setState(boolean state) {
		breakpoint.setState(state);
	}

	public void setTemporary(boolean temporary) {
		breakpoint.setTemporary(temporary);
	}

	public int getId() {
		return breakpoint.getId();
	}

	public void setId(int breakpointNo) {
		breakpoint.setId(breakpointNo);
	}

	public String getType() {
		return breakpoint.getType();
	}

	public void setType(String type) {
		breakpoint.setType(type);
	}
	
	public String getExpression() {
		//TODO: implement
		return "";
	}
	
	public void setExpression(String expression) {
		//TODO: implement
		;
	}

	public String toXMLString() {
		return "<breakpoint " +
				"id=\""+String.valueOf(getId())+"\" " +
				"type=\""+getType()+"\" "+
				"state=\""+getStateString()+"\" "+
				"filename=\""+getFilename()+"\" " +
				"lineno=\""+String.valueOf(getLineno())+"\" " +
		        "function=\""+getFunction()+"\" " +
		        "exception=\""+getException()+"\" " +
		        "hit_value=\""+String.valueOf(getHitValue())+"\" " +
		        "hit_condition=\""+getHitCondition()+"\" " +
		        "hit_count=\""+String.valueOf(getHitCount())+"\" >"+
		      "<expression>"+getExpression()+"</expression>"+
		      "</breakpoint>";
	}
}
