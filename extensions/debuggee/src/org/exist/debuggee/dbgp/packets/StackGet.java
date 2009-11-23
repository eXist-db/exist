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

import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.exist.xquery.Expression;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StackGet extends Command {

	private Integer stackDepth = null;
	
	private List<Expression> stacks;
	
	public StackGet(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d"))
			stackDepth = Integer.parseInt(val);
		else
			super.setArgument(arg, val);
	}
	
	public byte[] responseBytes() {
		String response = "" +
			"<response " +
					"command=\"stack_get\" " +
					"transaction_id=\""+transactionID+"\">\n";

		if (stackDepth != null) {
			int index = stacks.size() - 1 - stackDepth;
			if (index >=0 && index < stacks.size())
				response += stackToString(index);
		} else {
			for (int index = stacks.size()-1; index >= 0; index--)
				response += stackToString(index);
		}
			
		response += "</response>";
		
		return response.getBytes();

	}
	
	private String stackToString(int index) {
		if (stacks == null || stacks.size() == 0)
			return "";
		
		Expression expr = stacks.get(index);
		
		int level = stacks.size() - index - 1;
		
		return "<stack level=\""+String.valueOf(level)+"\" " +
					"lineno=\""+expr.getLine()+"\" " +
					"type=\"file\" " +
					"filename=\""+getFileuri(expr.getSource())+"\" " +
//					+
//					"where=\"\" " +
					"cmdbegin=\""+expr.getLine()+":"+expr.getColumn()+"\"  />";
//					"cmdend=\""+(expr.getLine())+":"+(expr.getColumn()+1)+"\"/>";
	}
	
	

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		stacks = getJoint().stackGet();
	}

	public byte[] commandBytes() {
		String command = "stack_get -i "+transactionID;
		
		if (stackDepth != null)
			command += " -d "+String.valueOf(stackDepth);
		
		return command.getBytes();
	}
	
}
