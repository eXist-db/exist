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
package org.exist.security.management;

import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface GroupsManagement {

	Group addGroup(DBBroker broker, Group group) throws PermissionDeniedException, EXistException, ConfigurationException;
	
	Group getGroup(String name);

	boolean hasGroup(Group group);
	boolean hasGroup(String name);

	boolean hasGroupLocal(Group group);
	boolean hasGroupLocal(String name);

	boolean updateGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException;
	
	boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException;

}