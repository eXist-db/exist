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
package org.exist.xmldb.concurrent.action;


import org.xmldb.api.base.XMLDBException;

import java.io.IOException;

/**
 * Abstract base class for an action to be tested.
 * 
 * @author wolf
 */
public abstract class Action {
	
	protected final String collectionPath;
	protected final String resourceName;
	
	public Action(final String collectionPath, final String resourceName) {
		this.collectionPath = collectionPath;
		this.resourceName = resourceName;
	}

	/**
	 * Returns true if execution compled successfully.
	 *
	 * @return true if execution completed successfully, false otherwise.
	 */
	abstract public boolean execute() throws XMLDBException, IOException;
}