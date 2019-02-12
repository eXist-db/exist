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
package org.exist.commands.threads;

import java.util.Collections;
import java.util.Map;

import org.exist.plugin.command.AbstractCommand;
import org.exist.plugin.command.CommandException;
//TODO:check where that class would be
//import org.exist.storage.lock.DeadlockDetection;
import org.exist.storage.lock.LockInfo;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Deadlock extends AbstractCommand {

	public Deadlock() {
		names = new String[] {"deadlock", "dl"};
	}
	
	/* (non-Javadoc)
	 * @see org.exist.plugin.command.AbstractCommand#process(org.exist.xmldb.XmldbURI, java.lang.String[])
	 */
	@Override
	public void process(XmldbURI collection, String[] commandData) throws CommandException {
		
		//TODO:check where that method is
        Map<String, LockInfo> threads = Collections.emptyMap(); // DeadlockDetection.getWaitingThreads();

        for (Map.Entry<String, LockInfo> entry : threads.entrySet()) {
        	
        	LockInfo info = entry.getValue();

        	out().println("THREAD: " + entry.getKey());
            if (info != null) {
            	info.debug(out());
            }
        }
        
        if (threads.isEmpty())
        	out().println("No waiting threads.");
	}

}
