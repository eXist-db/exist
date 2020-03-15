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
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ContextNames extends Command {

	public final static String LOCAL = "0";
	public final static String GLOBAL = "1";
	public final static String CLASS = "2";
	
	private Integer stackDepth = null;

	public ContextNames(IoSession session, String args) {
        super(session, args);
    }

	protected void setArgument(String arg, String val) {
		if (arg.equals("d"))
			stackDepth = Integer.parseInt(val);
		else
			super.setArgument(arg, val);
	}

	@Override
    public void exec() {
    }

	public byte[] responseBytes() {
		String response = xml_declaration + 
		"<response " +
			namespaces +
            "command=\"context_names\" " +
            "transaction_id=\""+transactionID+"\">" +
            "   <context name=\"Local\" id=\"0\"/>" +
            "   <context name=\"Global\" id=\"1\"/>" +
            "   <context name=\"Class\" id=\"2\"/>" +
            "</response>";

		return response.getBytes();
	}

	public byte[] commandBytes() {
		String command = "context_names -i "+transactionID;
		
		if (stackDepth != null)
			command += " -d "+String.valueOf(stackDepth);
		
		return command.getBytes();
	}
}
