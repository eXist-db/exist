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
package org.exist.debuggee.dbgp;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public interface Errors {

	// 000 Command parsing errors
	/**
	 * no error
	 */
	public int ERR_0 = 0;

	/**
	 * parse error in command
	 */
	public int ERR_1 = 1;

	/**
	 * duplicate arguments in command
	 */
	public int ERR_2 = 2;

	/**
	 * invalid options (ie, missing a required option, invalid value for a
	 * passed option)
	 */
	public int ERR_3 = 3;

	/**
	 * Unimplemented command
	 */
	public int ERR_4 = 4;

	/**
	 * Command not available (Is used for async commands. For instance if the
	 * engine is in state "run" then only "break" and "status" are available).
	 */
	public int ERR_5 = 5;

	// 100 File related errors
	/**
	 * can not open file (as a reply to a "source" command if the requested
	 * source file can't be opened)
	 */
	public int ERR_100 = 100;
	public String ERR_100_STR = "can not open file (as a reply to a \"source\" command if the requested source file can't be opened)";

	/**
	 * stream redirect failed
	 */
	public int ERR_101 = 101;

	// 200 Breakpoint, or code flow errors
	/**
	 * breakpoint could not be set (for some reason the breakpoint could not be
	 * set due to problems registering it)
	 */
	public int ERR_200 = 200;

	/**
	 * breakpoint type not supported (for example I don't support 'watch' yet
	 * and thus return this error)
	 */
	public int ERR_201 = 201;

	/**
	 * invalid breakpoint (the IDE tried to set a breakpoint on a line that does
	 * not exist in the file (ie "line 0" or lines past the end of the file)
	 */
	public int ERR_202 = 202;

	/**
	 * no code on breakpoint line (the IDE tried to set a breakpoint on a line
	 * which does not have any executable code. The debugger engine is NOT
	 * required to return this type if it is impossible to determine if there is
	 * code on a given location. (For example, in the PHP debugger backend this
	 * will only be returned in some special cases where the current scope falls
	 * into the scope of the breakpoint to be set)).
	 */
	public int ERR_203 = 203;

	/**
	 * Invalid breakpoint state (using an unsupported breakpoint state was
	 * attempted)
	 */
	public int ERR_204 = 204;

	/**
	 * No such breakpoint (used in breakpoint_get etc. to show that there is no
	 * breakpoint with the given ID)
	 */
	public int ERR_205 = 205;

	/**
	 * Error evaluating code (use from eval() (or perhaps property_get for a
	 * full name get))
	 */
	public int ERR_206 = 206;

	/**
	 * Invalid expression (the expression used for a non-eval() was invalid)
	 */
	public int ERR_207 = 207;

	// 300 Data errors
	/**
	 * Can not get property (when the requested property to get did not exist,
	 * this is NOT used for an existing but uninitialized property, which just
	 * gets the type "uninitialised" (See: PreferredTypeNames)).
	 */
	public int ERR_300 = 300;

	/**
	 * Stack depth invalid (the -d stack depth parameter did not exist (ie,
	 * there were less stack elements than the number requested) or the
	 * parameter was < 0)
	 */
	public int ERR_301 = 301;

	/**
	 * Stack depth invalid (the -d stack depth parameter did not exist (ie,
	 * there were less stack elements than the number requested) or the
	 * parameter was < 0)
	 */
	public int ERR_302 = 302;

	// 900 Protocol errors
	/**
	 * Encoding not supported
	 */
	public int ERR_900 = 900;
	public String ERR_900_STR = "Encoding not supported";

	/**
	 * An internal exception in the debugger occurred
	 */
	public int ERR_998 = 998;
	public String ERR_998_STR = "An internal exception in the debugger occurred";

	/**
	 * Unknown error
	 */
	public int ERR_999 = 999;
	public String ERR_999_STR = "Unknown error";

}
