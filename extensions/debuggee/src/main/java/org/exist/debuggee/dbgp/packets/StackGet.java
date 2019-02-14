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
		StringBuilder response = new StringBuilder();
		response.append(xml_declaration);
		response.append("<response " +
				namespaces +
				"command=\"stack_get\" transaction_id=\"");
		response.append(transactionID);
		response.append("\">\n");

		if (stackDepth != null) {
			int index = stacks.size() - 1 - stackDepth;
			if (index >=0 && index < stacks.size())
				response.append(stackToString(index));
		} else {
			for (int index = stacks.size()-1; index >= 0; index--)
				response.append(stackToString(index));
		}
			
		response.append("</response>");
		
		return response.toString().getBytes();

	}
	
	private StringBuilder stackToString(int index) {
		StringBuilder result = new StringBuilder();
		if (stacks == null || stacks.size() == 0)
			return result;
		
		Expression expr = stacks.get(index);
		if (expr == null) 
			return result;
		
		int level = stacks.size() - index - 1;
		
		result.append("<stack level=\"");
		result.append(String.valueOf(level));
		result.append("\" lineno=\"");
		result.append(expr.getLine());
		result.append("\" type=\"file\" filename=\"");
		result.append(getFileuri(expr.getSource()));
		result.append("\" ");
//					+
//					"where=\"\" " +
		result.append("cmdbegin=\"");
		result.append(expr.getLine());
		result.append(":");
		result.append(expr.getColumn());
		result.append("\"  />");
//					"cmdend=\""+(expr.getLine())+":"+(expr.getColumn()+1)+"\"/>";
		return result; 
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
	
	public String toString() {
		
		StringBuilder response = new StringBuilder();
		response.append("stack_get ");

		if (stackDepth != null) {
			int index = stacks.size() - 1 - stackDepth;
			if (index >=0 && index < stacks.size())
				response.append(stackToString(index));
		} else {
			for (int index = stacks.size()-1; index >= 0; index--)
				response.append(stackToString(index));
		}
		
		response.append("["+transactionID+"]");

		return response.toString();
	}
}
