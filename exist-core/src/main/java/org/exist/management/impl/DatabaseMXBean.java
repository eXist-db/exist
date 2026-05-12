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
package org.exist.management.impl;

import java.util.List;

/**
 * JMX MXBean interface exposing runtime information about a database instance.
 */
public interface DatabaseMXBean extends PerInstanceMBean {

    /**
     * Get the current status of the database.
     *
     * @return a string describing the database status
     */
    String getStatus();

    /**
     * Initiate a graceful shutdown of the database instance.
     */
    void shutdown();

    /**
     * Get the maximum number of brokers that may be active simultaneously.
     *
     * @return maximum broker count
     */
    int getMaxBrokers();

    /**
     * Get the number of brokers currently available (not in use).
     *
     * @return available broker count
     */
    int getAvailableBrokers();

    /**
     * Get the number of brokers currently in active use.
     *
     * @return active broker count
     */
    int getActiveBrokers();

    /**
     * Get the total number of brokers that have been created.
     *
     * @return total broker count
     */
    int getTotalBrokers();

    /**
     * Get the amount of memory (in bytes) reserved for the database.
     *
     * @return reserved memory in bytes
     */
    long getReservedMem();

    /**
     * Get the total memory (in bytes) currently used by all page caches.
     *
     * @return cache memory in bytes
     */
    long getCacheMem();

    /**
     * Get the maximum memory (in bytes) allocated to the collection cache.
     *
     * @return collection cache memory in bytes
     */
    long getCollectionCacheMem();

    /**
     * Get details of all currently active brokers.
     *
     * @return list of active broker information
     */
    List<ActiveBroker> getActiveBrokersMap();

    /**
     * Get the time (in milliseconds) that the database has been running.
     *
     * @return uptime in milliseconds
     */
    long getUptime();

    /**
     * Get the absolute path of the eXist-db home directory.
     *
     * @return the eXist home path, or {@code null} if not configured
     */
    String getExistHome();
}
