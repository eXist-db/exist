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

import org.exist.debuggee.DebuggeeJoint;
import org.exist.debugger.model.Breakpoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BreakpointSet extends Command implements Breakpoint {

	/**
	 * breakpoint type, see above for valid values [required]
	 */
	private String type;

	/**
	 * breakpoint state [optional, defaults to "enabled"]
	 */
	private boolean state = true;

	/**
	 * the filename to which the breakpoint belongs [optional]
	 */
	private String fileName;
	
	/**
	 * the line number (lineno) of the breakpoint [optional]
	 */
	private int lineNo;

	/**
	 * function name [required for call or return breakpoint types]
	 */
	private String function;

	/**
	 * EXCEPTION exception name [required for exception breakpoint types] 
	 */
	private String exception;

	/**
	 * hit value (hit_value) used with the hit condition to determine if should break; a value of zero indicates hit count processing is disabled for this breakpoint [optional, defaults to zero (i.e. disabled)]
	 */
	private int hitValue;
	
	/**
	 * hit condition string (hit_condition); see HIT_CONDITION hit_condition documentation above; BTW 'o' stands for 'operator' [optional, defaults to '>=']
	 */
	private String hitCondition;
	
	/**
	 * Boolean value indicating if this breakpoint is temporary. [optional, defaults to false]
	 */
	private boolean temporary = false;

	/**
	 * code expression, in the language of the debugger engine. The breakpoint should activate when the evaluated code evaluates to true. [required for conditional breakpoint types]
	 */
	private String expression;
	
	private int status = -1;

	public BreakpointSet(DebuggeeJoint joint, String args) {
		super(joint, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("t")) {
			type = val;
			
		} else if (arg.equals("s")) {
			state = true; //TODO: parsing required
			
		} else if (arg.equals("f")) {
			fileName = val;
			
		} else if (arg.equals("n")) {
			lineNo = Integer.parseInt(val);
			
		} else if (arg.equals("m")) {
			function = val;
			
		} else if (arg.equals("x")) {
			exception = val;
			
		} else if (arg.equals("h")) {
			hitValue = Integer.parseInt(val);
			
		} else if (arg.equals("o")) {
			hitCondition = val;
			
		} else if (arg.equals("r")) {
			temporary = true; //TODO: parsing required
			
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
				"id=\""+String.valueOf(id)+"\" " +
				"transaction_id=\""+transactionID+"\"/>";

			return responce.getBytes();
		}
		//error
		String responce = "<error/>";
		return responce.getBytes();
	}
	
	private String getStateString() {
		if (state)
			return "enabled";
		
		return "disabled";
	}
	
	///////////////////////////////////////////////////////////////////
	// Breakpoint's methods
	///////////////////////////////////////////////////////////////////

	private int hitCount = 0;
	
	public String getException() {
		return exception;
	}

	public String getFilename() {
		return fileName;
	}

	public String getFunction() {
		return function;
	}

	public String getHitCondition() {
		return hitCondition;
	}

	public int getHitCount() {
		return hitCount;
	}

	public int getHitValue() {
		return 0;
	}

	public int getLineno() {
		return lineNo;
	}

	public boolean getState() {
		return state;
	}

	public boolean getTemporary() {
		return temporary;
	}

	public void setException(String exception) {
		this.exception = exception;
	}

	public void setFilename(String filename) {
		fileName = filename;
	}

	public void setFunction(String function) {
		this.function = function;
	}

	public void setHitCondition(String condition) {
		hitCondition = condition;
	}

	public void setHitCount(int count) {
		hitCount = count;
	}

	public void setHitValue(int value) {
		hitValue = value;
	}

	public void setLineno(int lineno) {
		lineNo = lineno;
	}

	public void setState(boolean state) {
		this.state = state;
	}

	public void setTemporary(boolean temporary) {
		this.temporary = temporary;
	}

	private int id;
	
	public int getId() {
		return id;
	}

	public void setId(int breakpointNo) {
		this.id = breakpointNo;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
