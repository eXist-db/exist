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

import org.exist.EXistException;
import org.exist.plugin.command.AbstractCommand;
import org.exist.plugin.command.CommandException;
import org.exist.storage.BrokerPool;
import org.exist.storage.txn.TransactionManager;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Transactions extends AbstractCommand {

	public Transactions() {
		names = new String[] {"transactions", "t"};
	}
	
	/* (non-Javadoc)
	 * @see org.exist.plugin.command.AbstractCommand#process(org.exist.xmldb.XmldbURI, java.lang.String[])
	 */
	@Override
	public void process(XmldbURI collection, String[] commandData) throws CommandException {
		
		try {
			TransactionManager tm = BrokerPool.getInstance().getTransactionManager();
			
			//TODO:check where that method is
			//tm.debug(out());
			
		} catch (EXistException e) {
			throw new CommandException(e);
		}
	}

}
