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

import java.util.ArrayList;
import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.CommandContinuation;
import org.exist.debugger.Response;
import org.exist.debugger.ResponseListener;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractCommandContinuation extends Command implements CommandContinuation {

	protected String status = null;
	
	private int callStackDepth = 0;

	public AbstractCommandContinuation(IoSession session, String args) {
		super(session, args);
	}

	public boolean isStatus(String status) {
		return status.equals(getStatus());
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
		
		if (!session.isClosing())
			session.write(this);
	}

	public int getCallStackDepth() {
		return callStackDepth;
	}

	public void setCallStackDepth(int callStackDepth) {
		this.callStackDepth = callStackDepth;
	}
	
	public void disconnect() {
		if (!session.isClosing())
			session.close(true);
	}

	List<ResponseListener> listeners = new ArrayList<ResponseListener>();
	
	public void addResponseListener(ResponseListener listener) {
		if (listener != null)
			listeners.add(listener);
	}

	public void removeResponseListener(ResponseListener listener) {
		if (listener != null)
			listeners.remove(listener);
	}

	public void putResponse(Response response) {
		status = response.getAttribute("status");
		
		for (ResponseListener listener : listeners) {
			listener.responseEvent(this, response);
		}
	}
}
