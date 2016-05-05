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

import org.apache.mina.core.session.IoSession;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Init extends AbstractCommandContinuation {

	private org.exist.source.Source fileuri;
	private String idekey = "1";
	private String idesession = "1";
	
	public Init(IoSession session) {
		super(session, "");
	}

	public Init(IoSession session, String idesession, String idekey) {
		super(session, "");
		this.idekey = idekey;
		this.idesession = idesession;
	}

	public void setFileURI(org.exist.source.Source source){
		fileuri = source;

		if (!session.isClosing())
			session.write(this);
	}

	public byte[] responseBytes() {
		String init_message = xml_declaration +
			"<init " +
			namespaces +
			"appid=\"eXist050705\" " + //keep this as memory of creation
			"idekey=\""+idekey+"\" " +
			"session=\""+idesession+"\" " +
			//"thread=\"1\" " +
			//"parent=\"1\" " +
			"language=\"XQuery\" " +
			"protocol_version=\"1.0\" " +
			"fileuri=\""+Command.getFileuri(fileuri)+"\">" +
//			"<engine version=\"1.0.1\"><![CDATA[eXist Xdebug]]></engine>" +
//			"<author><![CDATA[Dmitriy Shabanov]]></author>" +
//			"<url><![CDATA[http://exist-db.org]]></url>" +
//			"<copyright><![CDATA[Copyright (c) 2009-2011 by eXist-db]]></copyright>" +
			"</init>";

		return init_message.getBytes();
	}

	@Override
	public void exec() {
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getType() {
		return INIT;
	}

	public boolean is(int type) {
		return (type == INIT);
	}

	public byte[] commandBytes() {
		return new byte[0];
	}
}
