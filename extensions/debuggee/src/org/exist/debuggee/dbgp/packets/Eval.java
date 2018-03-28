/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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

import java.io.IOException;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.dbgp.Errors;
import org.exist.util.Base64Encoder;
import org.exist.util.io.FastByteArrayOutputStream;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Eval extends Command {
	
	String script;
	String result;
	
	Exception exception;
	
	public Eval(IoSession session, String args) {
		super(session, args);
	}

	protected void init() {
		script = null;
		result = null;
		
		exception = null;
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("-")) {
			script = val;
		} else {
			super.setArgument(arg, val);
		}
	}
	
	public byte[] responseBytes() {
    	byte[] response;
		if (exception != null) {
			response = errorBytes("eval", Errors.ERR_206, exception.getMessage());
    	} else { 
			String head = xml_declaration + 
				"<response " +
					namespaces +
					"command=\"eval\" " +
					"success=\""+isSuccess()+"\" "+
					"transaction_id=\""+transactionID+"\">" +
					"<property>";
			String tail = 
					"</property>" +
				"</response>";
	
			Base64Encoder enc = new Base64Encoder();
			enc.translate(result.getBytes());
	
			FastByteArrayOutputStream baos = new FastByteArrayOutputStream(head.length() + ((result.length() / 100) * 33) + tail.length());
			try {
				baos.write(head.getBytes());
				baos.write(new String(enc.getCharArray()).getBytes());
				baos.write(tail.getBytes());
			} catch (IOException e) {
			}
			response = baos.toByteArray();
    	}
		
		return response;
	}
	
	private String isSuccess() {
		if (result != null)
			return "1";
		
		return "0";
	}
	
	@Override
	public void exec() {
		try {
			result = getJoint().evalution(script);
		} catch (Exception e) {
			exception = e;
		}
	}
	
	public byte[] commandBytes() {
		String command = "eval -i "+transactionID+" -- "+script;
		
		return command.getBytes();
	}

	public void setScript(String script) {
		this.script = script;
	}

}
