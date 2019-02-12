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
package org.exist.debuggee;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Status {
	
	public String FIRST_RUN = "FIRST_RUN";
	
	/**
	 * State prior to execution of any code
	 */
	public String STARTING = "starting";

	/**
	 * Code is currently executing. Note that this state would only be seen with async support turned on, otherwise the typical state during IDE/debugger interaction would be 'break'
	 */
	public String RUNNING = "running";

	/**
	 * Code execution is paused, for whatever reason (see below), and the IDE/debugger can pass information back and forth.
	 */
	public String BREAK = "break";

	/**
	 * State after completion of code execution. This typically happens at the end of code execution, allowing the IDE to further interact with the debugger engine (for example, to collect performance data, or use other extended commands).
	 */
	public String STOPPING = "stopping";

	/**
	 * IDE is detached from process, no further interaction is possible.
	 */
	public String STOPPED = "stopped";
}
