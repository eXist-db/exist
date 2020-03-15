/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class FeatureGet extends Command {

	String name;
	String value;
	
	boolean success = false; 
	
	public FeatureGet(IoSession session, String args) {
		super(session, args);
	}

	protected void init() {
		name = null;
		value = null;
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("n")) {
			name = val;
		} else {
			super.setArgument(arg, val);
		}
	}
	
	public byte[] responseBytes() {
		String response = xml_declaration +
			"<response " +
				namespaces +
				"command=\"feature_get\" " +
				"feature=\""+name+"\" " +
				"supported=\""+getSupportedString()+"\" " +
				"transaction_id=\""+transactionID+"\">" +
				value +
			"</response>";

		return response.getBytes();
	}
	
	public String getSupportedString() {
		if (value != null)
			return "1";
		else
			return "0";
	}

	@Override
	public void exec() {
		value = getJoint().featureGet(name);
	}
	
	public byte[] commandBytes() {
		String command = "feature_get -i "+transactionID+" -n "+name;
		
		return command.getBytes();
	}

	public String toString() {
		return "feature_get name = '"+name+"' value = '"+value+"' ["+transactionID+"]";
	}
}
