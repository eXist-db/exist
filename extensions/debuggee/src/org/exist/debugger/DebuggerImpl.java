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

import java.util.HashMap;
import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.dgbp.packets.ResponseImpl;
import org.exist.debuggee.dgbp.packets.Source;
import org.exist.debugger.Debugger;
import org.exist.debugger.DebuggingSource;
import org.exist.debugger.model.Breakpoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggerImpl implements Debugger {
	
	private IoSession session;
	
	//uri, source
	private Map<String, DebuggingSource> sources = new HashMap<String, DebuggingSource>();
	
	public DebuggerImpl() {
		
	}
	
	protected void setSession(IoSession session) {
		this.session = session;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.Debugger#source(java.lang.String)
	 */
	public DebuggingSource source(String fileURI) {
		if (sources.containsKey(fileURI))
			return sources.get(fileURI);

		Source command = new Source(session, "");
		command.setFileURI(fileURI);
		Response response = command.toDebuggee();
		
		if ("1".equals(response.getAttribute("success"))) {
			DebuggingSource source = new DebuggingSourceImpl(this, fileURI);
			source.setData(response.getText());
			
			sources.put(fileURI, source);
			
			return source;
		}
		
		return null;
	}

	public Breakpoint addBreakpoint(Breakpoint breakpoint) {
		// TODO Auto-generated method stub
		return null;
	}

	public void sessionClosed() {
		// TODO Auto-generated method stub
		
	}
	
	private Map<String, ResponseImpl> responses = new HashMap<String, ResponseImpl>();
	
	protected synchronized void addResponse(ResponseImpl response) {
		responses.put(response.getTransactionID(), response);
		notifyAll();
	}

	public synchronized ResponseImpl getResponse(String transactionID) {
		while (!responses.containsKey(transactionID)) {
			try {
				wait(30 * 1000); //30s
			} catch (InterruptedException e) {
			} 
		}
		
		if (responses.containsKey(transactionID)) {
			ResponseImpl response = responses.get(transactionID);
			responses.remove(transactionID);
			
			return response;
		}

		return null;
	}

}
