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
import org.exist.xquery.Variable;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class PropertyGet extends Command {

	/**
	 * -d stack depth (optional, debugger engine should assume zero if not provided)
	 */
	private int stackDepth = 0;
			   
	/**
	 * -c context id (optional, retrieved by context-names, debugger engine should assume zero if not provided)
	 */
	private int contextID = 0;
			   
	/**
	 * -n property long name (required)
	 */
	private String nameLong;
			
	/**
	 * -m max data size to retrieve (optional)
	 */
	private int maxDataSize;
			
	/**
	 * -p data page (property_get, property_value: optional for arrays, hashes, objects, etc.; property_set: not required; debugger engine should assume zero if not provided)
	 */
	private String dataPage;
			   
	/**
	 * -k property key as retrieved in a property element, optional, used for property_get of children and property_value, required if it was provided by the debugger engine.
	 */
	private String propertyKey;
	
	/**
	 * -a property address as retrieved in a property element, optional, used for property_set/value
	 */
	private String propertyAddress;
	
	private Variable variable;
	
	public PropertyGet(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d"))
			stackDepth = Integer.parseInt(val);
		
		else if (arg.equals("c"))
			contextID = Integer.parseInt(val);
		
		else if (arg.equals("n"))
			nameLong = val;
		
		else if (arg.equals("m"))
			maxDataSize = Integer.parseInt(val);
		
		else if (arg.equals("p"))
			dataPage = val;
		
		else if (arg.equals("k"))
			propertyKey = val;
		
		else if (arg.equals("a"))
			propertyAddress = val;
		
		else
			super.setArgument(arg, val);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		variable = joint.getVariable(nameLong);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#toBytes()
	 */
	@Override
	public byte[] toBytes() {
		String responce = "" +
			"<response " +
					"command=\"property_get\" " +
					"transaction_id=\""+transactionID+"\">" +
				getPropertyString()+
			"</response>";

		return responce.getBytes();
	}

	private String getPropertyString() {
		String property = "";
		if (variable == null)
			return property; //XXX: error?
		
		String value = variable.getValue().toString();
			
		property += "<property " +
				"name=\""+variable.getQName().toString()+"\" " +
				"size=\""+value.length()+"\" " +
				"encoding=\"none\">" +
			value+
		"</property>";

		return property;
	}

}
