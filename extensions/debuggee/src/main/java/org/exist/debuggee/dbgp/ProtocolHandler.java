/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.debuggee.dbgp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.debuggee.dbgp.packets.Command;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ProtocolHandler extends IoHandlerAdapter {

    private final static Logger LOG = LogManager.getLogger(ProtocolHandler.class);
	
	public ProtocolHandler() {
		super();
	}

	@Override
	public void sessionOpened(IoSession session) {
		// Set reader idle time to 10 minutes.
		session.getConfig().setIdleTime(IdleStatus.READER_IDLE, 10 * 60 * 1000);
	}

	@Override
	public void sessionClosed(IoSession session) {
		DebuggeeJoint joint = (DebuggeeJoint) session.getAttribute("joint");
		if (joint != null)
			joint.sessionClosed(false);

		if (LOG.isDebugEnabled())
			LOG.debug("Total " + session.getReadBytes() + " byte(s) readed, " + session.getWrittenBytes() + " byte(s) writed.");
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
		// Close the connection if reader is idle.
		if (status == IdleStatus.READER_IDLE) {
			session.close(true);
		}
	}

	@Override
	public void messageReceived(IoSession session, Object message) {
		Command command = (Command) message;
		
//		command.exec();
		
		if (LOG.isDebugEnabled())
			LOG.debug("" + command.toString());

		session.write(command);
	}
	
	public void exceptionCaught(IoSession session, Throwable cause) {
		System.out.println(cause);
	}
}
