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
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.security.xacml.XACMLSource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Init extends AbstractCommandContinuation {

	private XACMLSource fileuri;
	
	public Init(IoSession session) {
		super(session, "");
	}

	public void setFileURI(XACMLSource source){
		fileuri = source;
	}

	public int getLength() {
		return responseBytes().length;
	}

	public byte[] responseBytes() {
		String init_message = "<init " +
			"appid=\"7035\" " +
			"idekey=\"1\" " +
			"session=\"1\" " +
			"thread=\"1\" " +
			"parent=\"1\" " +
			"language=\"XQuery\" " +
			"protocol_version=\"1.0\" " +
			"fileuri=\""+Command.getFileuri(fileuri)+"\"></init>";

		return init_message.getBytes();
	}

	@Override
	public void exec() {
	}

	public int getType() {
		return INIT;
	}

	public boolean is(int type) {
		return (type == INIT);
	}
}
