/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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

	protected Map<String, Command> commands = new HashMap<>();

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

		for (String name : names) {
			//TODO: check for conflicts
			commands.put(name, cmd);
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
