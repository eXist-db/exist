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

import org.apache.mina.core.session.IoSession;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class FeatureSet extends Command {

	String name;
	String value;
	
	boolean success = false; 
	
	public FeatureSet(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("n")) {
			name = val;
		} else if (arg.equals("v")) {
			value = val;
		} else {
			super.setArgument(arg, val);
		}
	}
	
	public byte[] responseBytes() {
		String response = xml_declaration +
			"<response " +
				namespaces +
				"command=\"feature_set\" " +
				"feature=\""+name+"\" " +
				"success=\""+getStringStatus()+"\" " +
				"transaction_id=\""+transactionID+"\"/>";

		return response.getBytes();
	}
	
	public String getStringStatus() {
		if (success)
			return "1";
		else
			return "0";
	}

	@Override
	public void exec() {
		success = getJoint().featureSet(name, value);
	}

	public byte[] commandBytes() {
		String command = "feature_set -i "+transactionID+" -n "+name+" -v "+value;
		
		return command.getBytes();
	}
	
	public String toString() {
		return "feature_set name = '"+name+"' value = '"+value+"' ["+transactionID+"]";
	}
}
