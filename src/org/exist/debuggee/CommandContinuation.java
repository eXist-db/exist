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

import org.exist.debugger.Response;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface CommandContinuation {

	public int WAIT = 0;

	public int INIT = 1;

	public int RUN = 2;

	public int STEP_INTO = 3;
	public int STEP_OUT = 4;
	public int STEP_OVER = 5;

	public int STOP = 8;
	
	public boolean is(int type);
	public int getType();

	public boolean isStatus(String status);
	
	public String getStatus();
	public void setStatus(String status);
	
	public int getCallStackDepth();

	//debugger side methods
	public void putResponse(Response response);

	//close session
	public void disconnect();
}