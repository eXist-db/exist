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
package org.exist.commands.info;

import org.exist.Database;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.plugin.command.AbstractCommand;
import org.exist.plugin.command.CommandException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Lock extends AbstractCommand {

	public Lock() {
		names = new String[] {"lock", "l"};
	}
	
	/* (non-Javadoc)
	 * @see org.exist.plugin.command.AbstractCommand#process(org.exist.xmldb.XmldbURI, java.lang.String[])
	 */
	@Override
	public void process(XmldbURI collectionURI, String[] commandData) throws CommandException {
		try {
			final Database db = BrokerPool.getInstance();
			
			try(final DBBroker broker = db.getBroker()) {

				Collection collection = broker.getCollection(collectionURI);

				out().println("Collection lock:");
				//TODO:check where that method is
				//collection.getLock().debug(out());

				if (commandData.length == 0) return;

				DocumentImpl doc = collection.getDocument(broker, XmldbURI.create(commandData[0]));

				if (doc == null) {
					err().println("Resource '" + commandData[0] + "' not found.");
					return;
				}

				out().println("Locked by " + doc.getUserLock());
				out().println("Lock token: " + doc.getMetadata().getLockToken());

				out().println("Update lock: ");
				//TODO:check where that method is
				//doc.getUpdateLock().debug(out());
			}
		} catch (Exception e) {
			throw new CommandException(e);
		}
	}

}
