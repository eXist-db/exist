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
import org.exist.xquery.Expression;
import org.exist.xquery.PathExpr;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StackGet extends Command {

	private int stackDepth = 0;
	
	private List<Expression> stacks;
	
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
				stackToString() + 
			"</response>";
		
		return response.getBytes();

	}
	
	private String stackToString() {
		if (stacks == null || stacks.size() == 0)
			return "";
		
		Expression expr = stacks.get(0);
		
		return "<stack level=\""+String.valueOf(stackDepth)+"\" " +
					"type=\"file\" " +
					"filename=\"file://"+getFileuri(expr.getSource())+"\" " +
					"lineno=\""+expr.getLine()+"\" ";
//					+
//					"where=\"\" " +
//					"cmdbegin=\""+expr.getLine()+":"+expr.getColumn()+"\" " +
//					"cmdend=\""+(expr.getLine())+":"+(expr.getColumn()+1)+"\"/>";

	}
	
	

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		stacks = joint.stackGet();
	}

	
}
