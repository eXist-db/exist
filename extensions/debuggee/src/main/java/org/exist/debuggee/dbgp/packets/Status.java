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
import org.exist.debuggee.CommandContinuation;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class Status extends Command {

	private CommandContinuation command = null;
	
    public Status(IoSession session, String args) {
        super(session, args);
    }

    @Override
    public void exec() {
    	//XXX: get values @responseBytes time?
		command = getJoint().getCurrentCommand();
    }

	public byte[] responseBytes() {
    	if (command != null) {
    		String response = xml_declaration + 
			"<response " +
    			namespaces +
            	"command=\"status\" " +
            	"status=\""+command.getStatus()+"\" " +
            	"reason=\"ok\" " +
            	"transaction_id=\""+transactionID+"\"/>";

    		return response.getBytes();
    	}
    	return errorBytes("status");
	}

	public byte[] commandBytes() {
		String command = "status -i "+transactionID;
		
		return command.getBytes();
	}
}
