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

import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.exist.dom.QName;
import org.exist.xquery.Variable;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ContextGet extends Command {

	/**
	 * stack depth (optional)
	 */
	private Integer stackDepth = null;
	
	/**
	 * context id (optional, retrieved by context-names)
	 */
	private String contextID = "";
	
	private Map<QName, Variable> variables;

	public ContextGet(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d")) {
			stackDepth = Integer.parseInt(val);
		} else if (arg.equals("c")) {
			contextID = val;
		} else {
			super.setArgument(arg, val);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		//TODO: different stack depth & context id
		variables = getJoint().getVariables();
	}

	@Override
	public byte[] responseBytes() {
		String response = "<response " +
				"command=\"context_get\" " +
				"context=\""+contextID+"\" " +
				"transaction_id=\""+transactionID+"\"> " +
			getPropertiesString() +
		"</response>";

		return response.getBytes();
	}
	
	private String getPropertiesString() {
		String properties = "";
		if (variables == null)
			return properties; //XXX: error?

        XQueryContext ctx = getJoint().getContext();
		for (Variable variable : variables.values()) {
			properties += PropertyGet.getPropertyString(variable, ctx);
		}
		return properties;
	}

    @Override
	public byte[] commandBytes() {
		String command = "context_get -i "+transactionID;
		
		if (stackDepth != null)
			command += " -d "+String.valueOf(stackDepth);

		if (contextID != null && contextID.equals(""))
			command += " -c "+String.valueOf(contextID);
		
		return command.getBytes();
	}
}
