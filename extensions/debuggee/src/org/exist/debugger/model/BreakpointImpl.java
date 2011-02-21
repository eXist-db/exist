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
package org.exist.debugger.model;

import java.io.IOException;

import org.exist.debugger.DebuggerImpl;
import org.exist.debugger.DebuggingSource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BreakpointImpl implements Breakpoint {

	/**
	 * breakpoint type [required]
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
	private Integer lineNo;

	/**
	 * function name [required for call or return breakpoint types]
	 */
	private String function;

	/**
	 * EXCEPTION exception name [required for exception breakpoint types] 
	 */
	private String exception;

	/**
	 * hit value (hit_value) used with the hit condition to determine if should break; 
	 * a value of zero indicates hit count processing is disabled for this breakpoint 
	 * [optional, defaults to zero (i.e. disabled)]
	 */
	private int hitValue = 0;
	
	/**
	 * hit condition string (hit_condition); 
	 * see HIT_CONDITION hit_condition documentation above; 
	 * BTW 'o' stands for 'operator' [optional, defaults to '>=']
	 */
	private String hitCondition = ">=";
	
	/**
	 * Boolean value indicating if this breakpoint is temporary. [optional, defaults to false]
	 */
	private boolean temporary = false;

	/**
	 * code expression, in the language of the debugger engine. The breakpoint should activate when the evaluated code evaluates to true. [required for conditional breakpoint types]
	 */
	private String expression;
	
	private int hitCount = 0;
	
	public BreakpointImpl() {
		type = Breakpoint.TYPE_LINE; //default value
	}

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

	public Integer getLineno() {
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

	//protected? -shabanovd
	public void setFilename(String filename) {
		this.fileName = filename;
	}

	public void setFunction(String function) {
		this.function = function;
	}

	public void setHitCondition(String condition) {
		this.hitCondition = condition;
	}

	public void setHitCount(int count) {
		this.hitCount = count;
	}

	public void setHitValue(int value) {
		this.hitValue = value;
	}

	public void setLineno(Integer lineno) {
		this.lineNo = lineno;
	}

	public void setState(boolean state) {
		this.state = state;
	}

	public void setTemporary(boolean temporary) {
		this.temporary = temporary;
	}

	private int id = -1;
	
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
	
	public String toXMLString() {
		return "<breakpoint " +
				"id=\""+String.valueOf(id)+"\" " +
				"type=\""+type+"\" "+
				"state=\""+state+"\" "+
				"filename=\""+fileName+"\" " +
				"lineno=\""+String.valueOf(lineNo)+"\" " +
		        "function=\""+function+"\" " +
		        "exception=\""+exception+"\" " +
		        "hit_value=\""+String.valueOf(hitValue)+"\" " +
		        "hit_condition=\""+hitCondition+"\" " +
		        "hit_count=\""+String.valueOf(hitCount)+"\" >"+
		      "<expression>"+expression+"</expression>"+
		      "</breakpoint>";
	}

	private DebuggingSource debuggingSource;
	
	public void setDebuggingSource(DebuggingSource debuggingSource) {
		this.debuggingSource = debuggingSource;
	}
	
	private DebuggerImpl getDebugger() {
		return (DebuggerImpl)debuggingSource.getDebugger();
	}

	public boolean sync() throws IOException {
		if (getId() == -1) {
			return getDebugger().setBreakpoint(this);
		} else if (getId() > 0) {
			return getDebugger().updateBreakpoint(this);
		}
		//TODO: call remove breakpoint ???
		return false;
	}

	public boolean remove() throws IOException {
		return getDebugger().removeBreakpoint(this);
	}
}
