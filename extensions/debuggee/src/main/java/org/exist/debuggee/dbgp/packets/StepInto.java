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
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StepInto extends AbstractCommandContinuation {

	public StepInto(IoSession session, String args) {
		super(session, args);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public synchronized void exec() {
		getJoint().continuation(this);
	}

	public synchronized byte[] responseBytes() {
		String responce = xml_declaration + 
			"<response " +
				namespaces +
				"command=\"step_into\" " +
				"status=\""+getStatus()+"\" " +
				"reason=\"ok\" " +
				"transaction_id=\""+transactionID+"\"/>";

		return responce.getBytes();
	}

	public byte[] commandBytes() {
		String command = "step_into -i "+transactionID;
		
		return command.getBytes();
	}
	
	public int getType() {
		return STEP_INTO;
	}

	public boolean is(int type) {
		return (type == STEP_INTO);
	}
	
	public String toString() {
		return "step_into ["+transactionID+"]";
	}
}
