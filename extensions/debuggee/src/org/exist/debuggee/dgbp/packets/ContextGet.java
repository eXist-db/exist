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

import java.util.Map;

import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.QName;
import org.exist.xquery.Variable;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ContextGet extends Command {

	/**
	 * stack depth (optional)
	 */
	private int stackDepth = 0;
	
	/**
	 * context id (optional, retrieved by context-names)
	 */
	private String contextID = "";
	
	private Map<QName, Variable> variables;

	public ContextGet(DebuggeeJoint joint, String args) {
		super(joint, args);
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
		variables = joint.getVariables();
	}

	@Override
	public byte[] toBytes() {
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
		
		for (Variable variable : variables.values()) {
			String value = variable.getValue().toString();
			
			properties += "<property " +
					"name=\""+variable.getQName().toString()+"\" " +
					"size=\""+value.length()+"\" " +
					"encoding=\"none\">" +
				value+
			"</property>";
		}
		return properties;
	}
}
