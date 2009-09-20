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

import java.util.List;

import org.exist.debuggee.DebuggeeJoint;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StackGet extends Command {

	private int stackDepth = 0;
	
	private List stacks;
	
	public StackGet(DebuggeeJoint joint, String args) {
		super(joint, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d"))
			stackDepth = Integer.parseInt(val);
		else
			super.setArgument(arg, val);
	}
	
	public byte[] toBytes() {
		String response = "" +
			"<response " +
					"command=\"stack_get\" " +
					"transaction_id=\""+transactionID+"\">" +
				"<stack level=\""+String.valueOf(stackDepth)+"\" " +
						"type=\"file\" " +
						"filename=\"file:///home/dmitriy/projects/eXist-svn/trunk/eXist/webapp/admin/admin.xql\" " +
						"lineno=\"5\" " +
						"where=\"\" " +
						"cmdbegin=\"5:5\" " +
						"cmdend=\"5:10\"/>" +
			"</response>";
		
		return response.getBytes();

	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		stacks = joint.stackGet();
	}

	
}
