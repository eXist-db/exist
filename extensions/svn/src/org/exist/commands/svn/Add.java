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
 * Puts directories and files under version control scheduling them for addition to a repository.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Add extends AbstractCommand {
	
	public Add() {
		names = new String[] {"add"};
	}
	
    public void process(XmldbURI collection, String[] params) throws CommandException {

    	WorkingCopy wc = new WorkingCopy("", "");
    	
    	String destPath = params[0];
    	
        Resource wcDir = new Resource(collection.append(destPath));

    	try {
			wc.addEntry(wcDir);
		} catch (SVNException e) {
			throw new CommandException(e);
		}
	}
}
