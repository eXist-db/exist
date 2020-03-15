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
package org.exist.xmldb;

import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * A service to manage the database instance. The service defines
 * a single method shutdown() to shut down the database instance
 * used by the current driver.
 * 
 */
public interface DatabaseInstanceManager extends Service {
	
	/**
	 * Immediately shutdown the current database instance.
     *
     * The current user must be a member of the "dba" group
	 * or an exception will be thrown.
	 *
     * This operation is synchronous and will not return
     * until the database is shutdown
     *
	 * @throws XMLDBException if an error occurs during shutdown.
	 */
	void shutdown() throws XMLDBException;
	
	/**
	 * Shutdown the current database instance after the specified
	 * delay (in milliseconds).
	 *
	 * The current user must be a member of the "dba" group
	 * or an exception will be thrown.
     *
     * This operation is asynchronous and the delay is scheduled
     * with the database scheduler.
	 *
	 * @param delay the period in ms to wait before shutting down
	 *
	 * @throws XMLDBException if an error occurs during shutdown.
	 */
	void shutdown(long delay) throws XMLDBException;

    boolean enterServiceMode() throws XMLDBException;

    void exitServiceMode() throws XMLDBException;
    
    DatabaseStatus getStatus() throws XMLDBException;
	
	/**
	 * Returns true if the database instance is running local, i.e. in
	 * the same thread as this service.
	 *
	 * @return true if the database instance is running local
	 */
	boolean isLocalInstance();
}