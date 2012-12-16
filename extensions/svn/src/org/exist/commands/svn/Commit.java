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
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Commit extends AbstractCommand {
	
	public Commit() {
		names = new String[] {"commit", "ci"};
	}
	
	public void process(XmldbURI collection, String[] params) throws CommandException {
        String user = params[0];
        String password = params[1];
        String comment = params[2];
        
        Resource wcDir = new Resource(collection);

        try {
        	WorkingCopy wc = new WorkingCopy(user, password);

        	out().println( wc.commit(wcDir, false, comment) );
		} catch (SVNException svne) {
			svne.printStackTrace();
			throw new CommandException(
					"error while commiting a working copy to the repository '"
                    + wcDir + "'", svne);
		}
	}

}
