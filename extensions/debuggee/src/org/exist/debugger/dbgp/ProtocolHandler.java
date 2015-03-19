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
package org.exist.debugger.dbgp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.exist.debugger.Debugger;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ProtocolHandler extends IoHandlerAdapter {

    private final static Logger LOG = LogManager.getLogger(ProtocolHandler.class);
	
	private Debugger debugger;
	
	public ProtocolHandler(Debugger debugger) {
		super();
		
		this.debugger = debugger;
	}

	@Override
	public void sessionOpened(IoSession session) {
		session.setAttribute("debugger", debugger);
	}

	@Override
	public void sessionClosed(IoSession session) {
		Debugger debugger = (Debugger) session.getAttribute("debugger");
		debugger.sessionClosed();

		if (LOG.isDebugEnabled())
			LOG.debug("Total " + session.getReadBytes() + " byte(s) readed.");
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
	}

	@Override
	public void messageReceived(IoSession session, Object message) {
//		session.write(message);
		System.out.println("messageReceived message = "+message);
	}
	
	public void exceptionCaught(IoSession session, Throwable cause) {
		System.out.println(cause);
	}
}
