/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class ContextNames extends Command {

    public ContextNames(IoSession session, String args) {
        super(session, args);
    }

    @Override
    public void exec() {
    }

    @Override
	public byte[] responseBytes() {
		String response = "<response " +
            "command=\"context_names\" " +
            "transaction_id=\""+transactionID+"\">" +
            "   <context name=\"Local\" id=\"0\"/>" +
            "   <context name=\"Global\" id=\"1\"/>" +
            "   <context name=\"Class\" id=\"2\"/>" +
            "</response>";

		return response.getBytes();
	}
}
