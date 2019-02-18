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
 * Commands hub.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Commands {
	
	private static Map<String, CommandResolver> commands = new HashMap<String, CommandResolver>();
	
	public static void plug(String command, CommandResolver resolver) {
		commands.put(command, resolver);
	}
	
	//XXX: plug-in activator
	static {
		plugin("org.exist.commands.info.InfoCommandResolver");
		plugin("org.exist.commands.threads.ThreadsCommandResolver");
		plugin("org.exist.commands.svn.SvnCommandResolver");
	}
	
	private static void plugin(String name) {
		try {
			Class.forName(name);
		} catch (final ClassNotFoundException e) {
		}
	}
	//*************************************************************************

	public static void command(XmldbURI collection, String[] params) throws CommandException {
		
		final CommandResolver runner = commands.get(params[0]);
		
		if (runner == null) {throw new CommandNotFoundException("Command '"+params[0]+"' not found.");}
		
    	final String[] commandData = new String[params.length-1];
    	System.arraycopy(params, 1, commandData, 0, params.length-1);
    	
    	runner.execute(collection, commandData);
	}

}
