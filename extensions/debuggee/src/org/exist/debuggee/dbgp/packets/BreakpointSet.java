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
public class BreakpointSet extends Command implements Breakpoint {

	private BreakpointImpl breakpoint;
	
	private int status;

	public BreakpointSet(IoSession session, String args) {
		super(session, args);
	}

	protected void init() {
		breakpoint = new BreakpointImpl();
		status = -1;
	}
	
	protected void setArgument(String arg, String val) {
		if (arg.equals("t")) {
			setType(val);
			
		} else if (arg.equals("s")) {
			if (val.equals("enabled"))
				setState(true);
			else if (val.equals("disabled"))
				setState(false);

			//TODO: exception???
			
		} else if (arg.equals("f")) {
			setFilename(val);
			
		} else if (arg.equals("n")) {
			setLineno(Integer.parseInt(val));
			
		} else if (arg.equals("m")) {
			setFunction(val);
			
		} else if (arg.equals("x")) {
			setException(val);
			
		} else if (arg.equals("h")) {
			setHitValue(Integer.parseInt(val));
			
		} else if (arg.equals("o")) {
			setHitCondition(val);
			
		} else if (arg.equals("r")) {
			if (val.equals("1"))
				setTemporary(true);
			else if (val.equals("0"))
				setTemporary(false);

			//TODO: exception???
		
		} else {
			super.setArgument(arg, val);
		}
	}

	private BreakpointImpl getBreakpoint() {
		return breakpoint;
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		status = getJoint().setBreakpoint(this);
	}

	public byte[] responseBytes() {
		if (status == 1) {
			String responce = xml_declaration + 
			"<response " +
				namespaces +
				"command=\"breakpoint_set\" " +
				"state=\""+getStateString()+"\" " +
				"id=\""+String.valueOf(getId())+"\" " +
				"transaction_id=\""+transactionID+"\"/>";

			return responce.getBytes();
		}
		return errorBytes("breakpoint_set");
	}
	
	private String getStateString() {
		if (getBreakpoint().getState())
			return "enabled";
		
		return "disabled";
	}
	
	private String getTemporaryString() {
		if (getTemporary())
			return "1";
		
		return "0";
	}

	public byte[] commandBytes() {
		if (breakpoint != null) {
			String command = "breakpoint_set" +
					" -i " + transactionID +
					" -t " + getType() +
					" -s " + getStateString() + 
					" -f " + getFilename() + 
					" -h " + getHitValue() + 
					" -o " + getHitCondition() +
					" -r " + getTemporaryString(); 

			if (getLineno() != null)
				command += " -n " + getLineno(); 
					
			if (getFunction() != null)
				command += " -m " + getFunction(); 

			if (getException() != null)
				command += " -x " + getException(); 

			//TODO: EXPRESSION

			return command.getBytes();
		}
		return null;
	}

	///////////////////////////////////////////////////////////////////
	// Breakpoint's methods
	///////////////////////////////////////////////////////////////////

	public String getException() {
		return getBreakpoint().getException();
	}

	public String getFilename() {
		return getBreakpoint().getFilename();
	}

	public String getFunction() {
		return getBreakpoint().getFunction();
	}

	public String getHitCondition() {
		return getBreakpoint().getHitCondition();
	}

	public int getHitCount() {
		return getBreakpoint().getHitCount();
	}

	public int getHitValue() {
		return getBreakpoint().getHitValue();
	}

	public Integer getLineno() {
		return getBreakpoint().getLineno();
	}

	public boolean getState() {
		return getBreakpoint().getState();
	}

	public boolean getTemporary() {
		return getBreakpoint().getTemporary();
	}

	public void setException(String exception) {
		getBreakpoint().setException(exception);
	}

	public void setFilename(String filename) {
		getBreakpoint().setFilename(filename);
	}

	public void setFunction(String function) {
		getBreakpoint().setFunction(function);
	}

	public void setHitCondition(String condition) {
		getBreakpoint().setHitCondition(condition);
	}

	public void setHitCount(int count) {
		getBreakpoint().setHitCount(count);
	}

	public void setHitValue(int value) {
		getBreakpoint().setHitValue(value);
	}

	public void setLineno(Integer lineno) {
		getBreakpoint().setLineno(lineno);
	}

	public void setState(boolean state) {
		getBreakpoint().setState(state);
	}

	public void setTemporary(boolean temporary) {
		getBreakpoint().setTemporary(temporary);
	}

	public int getId() {
		return getBreakpoint().getId();
	}

	public void setId(int breakpointNo) {
		getBreakpoint().setId(breakpointNo);
	}

	public String getType() {
		return getBreakpoint().getType();
	}

	public void setType(String type) {
		getBreakpoint().setType(type);
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

	public boolean sync() {
		return false;
	}

	public boolean remove() {
		return false;
	}

	public void setBreakpoint(BreakpointImpl breakpoint) {
		this.breakpoint = breakpoint;
	}
}
