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

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public interface Breakpoint {

	/**
	 * break on the given lineno in the given file
	 */
	public String TYPE_LINE = "line";

	/**
	 * break on entry into new stack for function name
	 */
	public String TYPE_CALL = "call";

	/**
	 * break on exit from stack for function name
	 */
	public String TYPE_RETURN = "return";

	/**
	 * break on exception of the given name
	 */
	public String TYPE_EXCEPTION = "exception";

	/**
	 * break when the given expression is true at the given filename and line
	 * number or just in given filename
	 */
	public String TYPE_CONDITIONAL = "conditional";

	/**
	 * break on write of the variable or address defined by the expression
	 * argument
	 */
	public String TYPE_WATCH = "watch";
	
	public String getType();
	public void setType(String type);

	/**
	 * The file the breakpoint is effective in. This must be a "file://" or "dbgp:" (See 6.7 Dynamic code and virtual files) URI.
	 * @return the source URI
	 */
	public String getFilename();
//	public void setFilename(String filename);
	
	/**
	 * Line number on which breakpoint is effective. Line numbers are 1-based. If an implementation requires a numeric value to indicate that lineno is not set, it is suggested that -1 be used, although this is not enforced.
	 * @return line number
	 */
	public Integer getLineno();
	public void setLineno(Integer lineno);
	
	/**
	 * Current state of the breakpoint. This must be one of enabled, disabled.
	 * @return current state: true = enabled, false = disabled
	 */
	public boolean getState();
	public void setState(boolean state);
	
	/**
	 * Function name for call or return type breakpoints.
	 * @return function name
	 */
	public String getFunction();
	public void setFunction(String function);
	
	/**
	 * Flag to define if breakpoint is temporary. A temporary breakpoint is one that is deleted after its first use. This is useful for features like "Run to Cursor". Once the debugger engine uses a temporary breakpoint, it should automatically remove the breakpoint from it's list of valid breakpoints.
	 * @return true if it's temporary
	 */
	public boolean getTemporary();
	public void setTemporary(boolean temporary);
	
	/**
	 * Number of effective hits for the breakpoint in the current session. This value is maintained by the debugger engine (a.k.a. DBGP client). A breakpoint's hit count should be increment whenever it is considered to break execution (i.e. whenever debugging comes to this line). If the breakpoint is disabled then the hit count should NOT be incremented.
	 * @return number of effective hits
	 */
	public int getHitCount();
	public void setHitCount(int count);

	/**
	 * A numeric value used together with the hit_condition to determine if the breakpoint should pause execution or be skipped.
	 * @return numeric of hit to pause execution
	 */
	public int getHitValue();
	public void setHitValue(int value);

	/**
	 * A string indicating a condition to use to compare hit_count and hit_value. The following values are legal: 
	 * &gt;= break if hit_count is greater than or equal to hit_value [default]
	 * == break if hit_count is equal to hit_value 
	 * %  break if hit_count is a multiple of hit_value
	 *  
	 * @return hit condition string
	 */
	public String getHitCondition();
	public void setHitCondition(String condition);
	
	/**
	 * Exception name for exception type breakpoints.
	 * @return exception name
	 */
	public String getException();
	public void setException(String exception);

	public int getId();
	public void setId(int breakpointNo);
	
	public String toXMLString();

	//Synchronize changes
	public boolean sync() throws IOException;
	public boolean remove() throws IOException;
}
