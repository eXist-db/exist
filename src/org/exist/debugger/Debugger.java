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
package org.exist.debugger;

import java.io.IOException;

import org.exist.debugger.model.Breakpoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Debugger {

	public DebuggingSource init(String url) throws IOException, ExceptionTimeout;

	public DebuggingSource getSource(String fileURI);

	public void sessionClosed();

	public Breakpoint addBreakpoint(Breakpoint breakpoint);

	public void run(ResponseListener listener);

	public void stepInto(ResponseListener listener);
	public void stepOut(ResponseListener listener);
	public void stepOver(ResponseListener listener);

	public void stop(ResponseListener listener);

	//public Response getResponse(String transactionID);

}
