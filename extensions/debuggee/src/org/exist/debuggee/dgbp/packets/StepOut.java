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

import org.exist.debuggee.CommandContinuation;
import org.exist.debuggee.DebuggeeJoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StepOut extends Command implements CommandContinuation {

	private String status;

	public StepOut(DebuggeeJoint joint, String args) {
		super(joint, args);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		joint.continuation(this);

	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#toBytes()
	 */
	@Override
	public byte[] toBytes() {
		String responce = "<response " +
				"command=\"step_out\" " +
				"status=\""+status+"\" " +
				"reason=\"ok\" " +
				"transaction_id=\""+transactionID+"\"/>";

		return responce.getBytes();
	}

	public String getStatus() {
		return status;
	}

	public int getType() {
		return STEP_OUT;
	}

	public boolean is(int type) {
		return (type == STEP_OUT);
	}

	public boolean isStatus(String status) {
		return status.equals(this.status);
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
