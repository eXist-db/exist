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
package org.exist.plugin.command;

import java.util.HashMap;
import java.util.Map;

import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractCommandResolver implements CommandResolver {

	protected Map<String, Command> commands = new HashMap<String, Command>();

	public void plug(Class<? extends Command> commandClass) {
		Command cmd;
		try {
			cmd = commandClass.newInstance();
		} catch (final Exception e) {
			e.printStackTrace();
			return;
		}
		
		final String[] names = cmd.getNames();
		
		if (names == null) {return;} //TODO: report for debug
		
		for (int i = 0; i < names.length; i++ ) {
			//TODO: check for conflicts
			commands.put(names[i], cmd);
		}
	}
	
	public Command getCommand(String name) throws CommandNotFoundException {
		final Command cmd = commands.get(name);
		
		if (cmd == null)
			{throw new CommandNotFoundException("Command '"+name+"' not found.");}
		
		return cmd;
	}
	
	public void execute(XmldbURI collection, String[] params) throws CommandException {
		final Command cmd = getCommand(params[0]);
		
    	final String[] commandData = new String[params.length-1];
    	System.arraycopy(params, 1, commandData, 0, params.length-1);

    	cmd.process(collection, commandData);
	}
}
