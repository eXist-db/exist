/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
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
package org.exist.atom;

import java.io.IOException;
import java.net.URL;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 * 
 * @author R. Alexander Milowski
 */
public interface AtomModule {

	public interface Context {
		String getDefaultCharset();

		String getParameter(String name);

		String getContextPath();

		URL getContextURL();

		String getModuleLoadPath();
	}

	void init(Context context) throws EXistException;

	void process(DBBroker broker, IncomingMessage message, OutgoingMessage response) 
		throws BadRequestException, PermissionDeniedException, NotFoundException, 
			EXistException, IOException, TriggerException;
}
