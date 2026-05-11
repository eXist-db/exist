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
package org.exist.management;

import org.exist.management.impl.PerInstanceMBean;
import org.exist.storage.BrokerPool;
import org.exist.util.DatabaseConfigurationException;

/**
 * Interface to allow the JMX classes to be plugged in on systems which support
 * it. A dummy implementation will be used if JMX is not available.
 */
public interface Agent {

    /**
     * Initialise JMX MBeans for a new database instance.
     *
     * @param instance the broker pool representing the database instance
     */
    void initDBInstance(BrokerPool instance);

    /**
     * Deregister all JMX MBeans associated with a database instance that is being closed.
     *
     * @param instance the broker pool representing the database instance
     */
    void closeDBInstance(BrokerPool instance);

    /**
     * Register an additional MBean with the MBean server.
     *
     * @param mbean the MBean to register
     * @throws DatabaseConfigurationException if the MBean cannot be registered
     */
    void addMBean(PerInstanceMBean mbean) throws DatabaseConfigurationException;

    /**
     * Notify the agent that the status of a background task has changed.
     *
     * @param instance     the broker pool representing the database instance
     * @param actualStatus the new task status
     */
    void changeStatus(BrokerPool instance, TaskStatus actualStatus);

    /**
     * Update the percentage-complete of a running background task.
     *
     * @param instance   the broker pool representing the database instance
     * @param percentage the percentage of work completed (0–100)
     */
    void updateStatus(BrokerPool instance, int percentage);
}
