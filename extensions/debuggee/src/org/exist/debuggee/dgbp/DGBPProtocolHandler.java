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
package org.exist.debuggee.dgbp;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.debuggee.dgbp.packets.Command;
import org.exist.debuggee.dgbp.packets.Init;
import org.exist.debuggee.dgbp.packets.Stop;
import org.exist.security.xacml.XACMLSource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DGBPProtocolHandler extends IoHandlerAdapter {

	private DebuggeeJoint joint;
	private XACMLSource source;
	
	public DGBPProtocolHandler(DebuggeeJoint joint, XACMLSource source) {
		super();
		
		this.joint = joint;
		this.source = source;
	}

	@Override
	public void sessionOpened(IoSession session) {
		// Set reader idle time to 30 seconds.
		// sessionIdle(...) method will be invoked when no data is read
		// for 30 seconds.
		//XXX: fix -> 30 ???
		//session.getConfig().setIdleTime(IdleStatus.READER_IDLE, 3000);
		 
		session.setAttribute("joint", joint);
		
		session.write(new Init(source));
	}

	@Override
	public void sessionClosed(IoSession session) {
		DebuggeeJoint joint = (DebuggeeJoint) session.getAttribute("joint");
		joint.continuation(new Stop(session, ""));

		// Print out total number of bytes read from the remote peer.
		System.err.println("Total " + session.getReadBytes() + " byte(s)");
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
//		// Close the connection if reader is idle.
//		if (status == IdleStatus.READER_IDLE) {
//			session.close(true);
//		}
	}

	@Override
	public void messageReceived(IoSession session, Object message) {
		Command command = (Command) message;
		
//		command.exec();
		
		session.write(command);
	}
	
	public void exceptionCaught(IoSession session, Throwable cause) {
		System.out.println(cause);
	}
}
