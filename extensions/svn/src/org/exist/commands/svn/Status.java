/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.commands.svn;

import org.exist.plugin.command.*;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.WorkingCopy;
import org.exist.versioning.svn.wc.ISVNStatusHandler;
import org.exist.versioning.svn.wc.SVNClientManager;
import org.exist.versioning.svn.wc.SVNStatusClient;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Status extends AbstractCommand {
	
	public Status() {
		names = new String[] {"status", "st"};
	}

	public void process(XmldbURI collection, String[] params) throws CommandException {

		String userName = "";
		String password = "";
		if (params.length == 2) {
			userName = params[0];
			password = params[1];
		}
    	new WorkingCopy(userName, password);

    	SVNRepositoryFactoryImpl.setup();
		SVNClientManager manager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(false), userName, password);
		SVNStatusClient statusClient = manager.getStatusClient();
//		SVNWCClient wcClient = manager.getWCClient();
		try {
			statusClient.doStatus(new Resource(collection), SVNRevision.HEAD, SVNDepth.getInfinityOrFilesDepth(true), true, true, false, false,  new AddStatusHandler(), null);
		} catch (SVNException e) {
			e.printStackTrace();
			throw new CommandException(e);
		}
	}

    private class AddStatusHandler implements ISVNStatusHandler {

		@Override
        public void handleStatus(org.exist.versioning.svn.wc.SVNStatus status) throws SVNException {
        	out().println(status.getContentsStatus().getCode()+" "+status.getFile());
        }
    }

}
