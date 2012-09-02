/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  $Id$
 */
package org.exist.scheduler;

import org.exist.EXistException;
import org.exist.config.Startable;
import org.exist.plugin.Jack;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Plugin implements Jack, Startable {
	
	SchedulerManager manager;
	
	public Plugin(PluginsManager pm) throws PermissionDeniedException {
		manager = new SchedulerManager(pm.getDatabase());
		System.out.println("run scheduler");
	}

	public void startUp(DBBroker broker) throws EXistException {
		manager.startUp(broker);
	}

	public void sync() {
	}

	public void stop() {
		manager.stop();
	}
}