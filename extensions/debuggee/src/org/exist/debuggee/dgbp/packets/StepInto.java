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

import org.exist.debuggee.DebuggeeJoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StepInto extends Command {

	private String status = null;

	public StepInto(DebuggeeJoint joint, String args) {
		super(joint, args);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public synchronized void exec() {
		System.out.println("StepInto.exec thread = "+Thread.currentThread());
		status = joint.stepInto();
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

}
