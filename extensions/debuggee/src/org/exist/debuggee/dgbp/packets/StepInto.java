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
package org.exist.debuggee.dgbp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.CommandContinuation;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StepInto extends Command implements CommandContinuation {

	private String status = null;

	public StepInto(IoSession session, String args) {
		super(session, args);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public synchronized void exec() {
		joint.continuation(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#toBytes()
	 */
	@Override
	public synchronized byte[] toBytes() {
		String responce = "<response " +
				"command=\"step_into\" " +
				"status=\""+status+"\" " +
				"reason=\"ok\" " +
				"transaction_id=\""+transactionID+"\"/>";

		return responce.getBytes();
	}
	public String getStatus() {
		return status;
	}

	public int getType() {
		return STEP_INTO;
	}

	public boolean is(int type) {
		return (type == STEP_INTO);
	}

	public boolean isStatus(String status) {
		return status.equals(this.status);
	}

	public void setStatus(String status) {
		this.status = status;
		session.write(this);
	}
}
