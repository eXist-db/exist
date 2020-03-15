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

/**
 * Instances of this class can be registered with the BrokerPool to get informed when the
 * database shuts down. The shutdown method is called after the database instance has
 * been shut down.
 *
 * @author wolf
 */
public interface ShutdownListener {

    /**
     * The database instance identified by dbname has been shut down.
     *
     * @param dbname             The name of the database instance.
     * @param remainingInstances Number of remaining database instances.
     */
    void shutdown(String dbname, int remainingInstances);
}
