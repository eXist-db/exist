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
package org.exist.storage.md;

import org.exist.plugin.Jack;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Plugin implements Jack {
	
	MetaDataImpl md;
	
	public Plugin(PluginsManager manager) throws PermissionDeniedException {
		md = new MetaDataImpl(manager.getDatabase());
		
		manager.getDatabase().getDocumentTriggers().add(new DocumentEvents());
		manager.getDatabase().getCollectionTriggers().add(new CollectionEvents());
	}
	
	@Override
	public void sync() {
		md.sync();
	}

	@Override
	public void stop() {
		md.close();
	}
}
